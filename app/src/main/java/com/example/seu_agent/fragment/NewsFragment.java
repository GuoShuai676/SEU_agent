package com.example.seu_agent.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;
import com.example.seu_agent.adapter.NewsAdapter;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderEffectBlur;
import eightbitlab.com.blurview.RenderScriptBlur;


public class NewsFragment extends Fragment {

    private RecyclerView rvNews;
    private final NewsAdapter adapter = new NewsAdapter();

    // 演示数据
    private static final List<NewsAdapter.NewsItem> ALL = Arrays.asList(
            new NewsAdapter.NewsItem("关于开展 2026 年秋季学期选课工作的通知", "09-01", "教务", "https://jwc.seu.edu.cn"),
            new NewsAdapter.NewsItem("人工智能前沿讲座：大模型与 RAG 技术", "09-01", "讲座", "https://cs.seu.edu.cn"),
            new NewsAdapter.NewsItem("2026 届毕业生就业双选会即将举办", "09-02", "实践", "https://job.seu.edu.cn"),
            new NewsAdapter.NewsItem("关于图书馆中秋假期开放安排的通知", "09-01", "教务", "https://lib.seu.edu.cn"),
            new NewsAdapter.NewsItem("校级暑期社会实践评审结果公示", "08-30", "教务", "https://tw.seu.edu.cn"),
            new NewsAdapter.NewsItem("青年教师教学创新大赛报名启动", "09-03", "讲座", "https://jwc.seu.edu.cn")
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 加载布局
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

        rvNews = view.findViewById(R.id.rv_news);
        rvNews.setLayoutManager(new LinearLayoutManager(getContext()));
        rvNews.setAdapter(adapter);
        adapter.submit(filter("全部"));



        adapter.setOnItemClickListener(item -> {
            if (item.url == null || item.url.isEmpty()) {
                Toast.makeText(getContext(), "该资讯暂无链接", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            startActivity(intent);
        });

        // 2. 找到分类 Tab，点击切换时按分类筛选
        TabLayout tabCategory = view.findViewById(R.id.tab_category);
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                adapter.submit(filter(tab.getText() == null ? "" : tab.getText().toString()));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    /** 按分类过滤 **/
    private List<NewsAdapter.NewsItem> filter(String category) {
        if (category == null || category.equals("全部")) {
            return ALL;
        }
        List<NewsAdapter.NewsItem> result = new ArrayList<>();
        for (NewsAdapter.NewsItem item : ALL) {
            if (category.equals(item.tag)) result.add(item);
        }
        return result;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvNews = null;
    }
}
