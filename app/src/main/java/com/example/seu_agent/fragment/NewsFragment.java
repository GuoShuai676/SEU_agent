package com.example.seu_agent.fragment;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeDao;
import com.example.seu_agent.R;
import com.example.seu_agent.adapter.NewsAdapter;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 资讯页：先显示本地 Room 缓存（秒开），后台从云端 /api/notices 同步。
 * 分类 Tab 从本地 Room 按 tag 查询。
 */
public class NewsFragment extends Fragment {

    private RecyclerView rvNews;
    private final NewsAdapter adapter = new NewsAdapter();
    private final OkHttpClient httpClient = new OkHttpClient();

    EditText SearchBox;

    // 当前选中的栏目（"全部" 表示不限栏目）
    private String currentTag = "";
    // 查询代号：输入很快时可能同时有好几个查询在跑，只认最后发起的那个结果
    private int queryVersion = 0;

    // 底部留白动画：避开悬浮导航栏
    private ValueAnimator padAnimator;
    private View.OnLayoutChangeListener navLayoutListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_news, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });
        SearchBox=view.findViewById(R.id.et_search);

        rvNews = view.findViewById(R.id.rv_news);
        rvNews.setLayoutManager(new LinearLayoutManager(getContext()));
        rvNews.setAdapter(adapter);

        // 列表底部留出悬浮导航栏的高度（带动画平滑过渡）
        applyNavBarBottomMargin();

        // 先读本地 Room 缓存（秒开），再后台同步云端刷新
        searchNotices("");
        syncNoticesFromCloud();

        // 点击打开链接
        adapter.setOnItemClickListener(item -> {
            if (item.url == null || item.url.isEmpty()) {
                Toast.makeText(getContext(), "该资讯暂无链接", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            startActivity(intent);
        });

        // 分类 Tab：记录当前栏目并刷新列表（保留搜索词）
        TabLayout tabCategory = view.findViewById(R.id.tab_category);
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String tag = tab.getText() == null ? "" : tab.getText().toString();
                loadByTag(tag);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // 搜索框：每输入一个字符，就按「当前栏目 + 关键词」实时过滤列表
        SearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                searchNotices(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }

    /** 切换栏目：记下当前栏目，再按现有搜索词刷新列表 */
    private void loadByTag(String tag) {
        currentTag = tag;
        searchNotices(SearchBox.getText().toString());
    }

    /** 动态搜索：后台按「当前栏目 + 关键词」查 Room，结果回主线程刷新列表 */
    private void searchNotices(String kw) {
        final int version = ++queryVersion;              // 本次查询的代号
        final String key = kw == null ? "" : kw.trim();  // 去掉首尾空格
        final Activity act = getActivity();              // 在 UI 线程捕获，线程里不碰 Fragment
        if (act == null) return;
        new Thread(() -> {
            List<Notice> rows;
            NoticeDao dao = AppDatabase.get(act).noticeDao();
            boolean all = currentTag.isEmpty() || currentTag.equals("全部");
            if (key.isEmpty()) {
                // 没输入关键词：显示当前栏目的完整列表
                rows = all ? dao.getAll() : dao.getByTag(currentTag);
            } else {
                // 有关键词：在当前栏目范围内按标题包含关键词搜索
                rows = all ? dao.search(key) : dao.searchByTag(currentTag, key);
            }
            // 只采纳最新一次查询的结果（旧查询直接丢弃，避免列表闪回）
            if (version != queryVersion) return;
            List<NewsAdapter.NewsItem> items = toItems(rows);
            act.runOnUiThread(() -> {
                if (version == queryVersion) adapter.submit(items);
            });
        }).start();
    }

    /** 后台线程：拉云端资讯 → 覆盖写进 Room → 按当前搜索条件重新刷新列表 */
    private void syncNoticesFromCloud() {
        final Context ctx = getContext();   // 提前捕获，后台线程里安全使用
        final Activity act = getActivity();
        if (ctx == null || act == null) return;
        new Thread(() -> {
            try {
                String url = AppConfig.getServerUrl(ctx) + "/api/notices?limit=50";
                Request req = new Request.Builder().url(url).build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";

                    // 解析 {code:0, data:{items:[{tag,title,content,publish_date,url}]}}
                    JSONObject json = new JSONObject(body);
                    JSONArray items = json.getJSONObject("data").getJSONArray("items");
                    List<Notice> list = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        Notice n = new Notice();
                        n.tag = it.optString("tag");
                        n.title = it.optString("title");
                        n.content = it.optString("content");
                        n.publishDate = it.optString("publish_date");
                        n.url = it.optString("url");
                        list.add(n);
                    }

                    // 覆盖写进 Room（url 重复自动覆盖）
                    AppDatabase.get(ctx).noticeDao().insertAll(list);

                    // 回主线程按当前搜索条件重新查询刷新（复用统一查询路径）
                    act.runOnUiThread(() -> searchNotices(SearchBox.getText().toString()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 网络失败时提示用户：本地缓存仍然可用
                act.runOnUiThread(() -> Toast.makeText(ctx,
                        "云端同步失败，正在显示本地缓存", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * 悬浮导航栏（底部胶囊）会盖住列表底部：
     * 测量它的实际高度，给 RecyclerView 底部加上等高的内边距余量，并带动画平滑过渡。
     */
    private void applyNavBarBottomMargin() {
        View nav = requireActivity().findViewById(R.id.navigation);
        if (nav == null) return;
        final ViewGroup capsule = (ViewGroup) nav.getParent(); // 悬浮栏外层容器
        final int gap = dp(16); // 与列表自身 16dp 内边距一致，留出舒适间距

        // 等悬浮栏完成布局后再取高度（onViewCreated 时可能还没测量）
        capsule.post(() -> {
            if (rvNews == null) return; // 页面已销毁
            int h = capsule.getHeight();
            if (h > 0) animateBottomPadding(rvNews, h + gap);
        });

        // 悬浮栏高度变化（旋转屏幕 / 系统字体缩放）时重新动画
        navLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (rvNews == null) return;
            int h = b - t;
            if (h > 0) animateBottomPadding(rvNews, h + gap);
        };
        capsule.addOnLayoutChangeListener(navLayoutListener);
    }

    /** 平滑动画调整 View 底部内边距，避免跳变 */
    private void animateBottomPadding(View v, int target) {
        if (padAnimator != null) padAnimator.cancel();
        padAnimator = ValueAnimator.ofInt(v.getPaddingBottom(), target);
        padAnimator.addUpdateListener(a -> v.setPadding(
                v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                (int) a.getAnimatedValue()));
        padAnimator.setDuration(250);
        padAnimator.setInterpolator(new DecelerateInterpolator());
        padAnimator.start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Notice → NewsItem */
    private List<NewsAdapter.NewsItem> toItems(List<Notice> rows) {
        List<NewsAdapter.NewsItem> items = new ArrayList<>();
        for (Notice n : rows) {
            items.add(new NewsAdapter.NewsItem(n.title, n.publishDate, n.tag, n.url));
        }
        return items;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (padAnimator != null) {
            padAnimator.cancel();
            padAnimator = null;
        }
        // 移除悬浮栏高度监听，避免页面销毁后仍回调到已回收的 View
        if (navLayoutListener != null && getActivity() != null) {
            View nav = getActivity().findViewById(R.id.navigation);
            if (nav != null && nav.getParent() instanceof ViewGroup) {
                ((ViewGroup) nav.getParent()).removeOnLayoutChangeListener(navLayoutListener);
            }
            navLayoutListener = null;
        }
        rvNews = null;
    }
}
