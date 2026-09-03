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

/** 校园资讯页，本地先显示，云端数据回来后再刷新。 */
public class NewsFragment extends Fragment {

    private RecyclerView rvNews;
    private final NewsAdapter adapter = new NewsAdapter();
    private final OkHttpClient httpClient = new OkHttpClient();

    EditText SearchBox;
    private String currentTag = "";
    private int queryVersion = 0;

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

        applyNavBarBottomMargin();

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

        // 输入时直接查本地库
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

    private void loadByTag(String tag) {
        currentTag = tag;
        searchNotices(SearchBox.getText().toString());
    }

    private void searchNotices(String kw) {
        final int version = ++queryVersion;
        final String key = kw == null ? "" : kw.trim();
        final Activity act = getActivity();
        if (act == null) return;
        new Thread(() -> {
            List<Notice> rows;
            NoticeDao dao = AppDatabase.get(act).noticeDao();
            boolean all = currentTag.isEmpty() || currentTag.equals("全部");
            if (key.isEmpty()) {
                rows = all ? dao.getAll() : dao.getByTag(currentTag);
            } else {
                rows = all ? dao.search(key) : dao.searchByTag(currentTag, key);
            }
            if (version != queryVersion) return;
            List<NewsAdapter.NewsItem> items = toItems(rows);
            act.runOnUiThread(() -> {
                if (version == queryVersion) adapter.submit(items);
            });
        }).start();
    }

    private void syncNoticesFromCloud() {
        final Context ctx = getContext();
        final Activity act = getActivity();
        if (ctx == null || act == null) return;
        new Thread(() -> {
            try {
                String url = AppConfig.getServerUrl(ctx) + "/api/notices?limit=50";
                Request req = new Request.Builder().url(url).build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";

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

                    AppDatabase.get(ctx).noticeDao().insertAll(list);
                    act.runOnUiThread(() -> searchNotices(SearchBox.getText().toString()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                act.runOnUiThread(() -> Toast.makeText(ctx,
                        "云端同步失败，正在显示本地缓存", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 列表最后一项不能被底部导航挡住
    private void applyNavBarBottomMargin() {
        View nav = requireActivity().findViewById(R.id.navigation);
        if (nav == null) return;
        final ViewGroup capsule = (ViewGroup) nav.getParent();
        final int gap = dp(16);

        capsule.post(() -> {
            if (rvNews == null) return;
            int h = capsule.getHeight();
            if (h > 0) animateBottomPadding(rvNews, h + gap);
        });

        navLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (rvNews == null) return;
            int h = b - t;
            if (h > 0) animateBottomPadding(rvNews, h + gap);
        };
        capsule.addOnLayoutChangeListener(navLayoutListener);
    }

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
