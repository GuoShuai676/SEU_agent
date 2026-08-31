package com.example.seu_agent.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 资讯列表适配器（经典写法：LayoutInflater + findViewById）。
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.VH> {

    /** 点击回调  */
    public interface OnItemClickListener {
        void onItemClick(NewsItem item);
    }

    /** 单条资讯数据 */
    public static class NewsItem {
        public final String title;   // 标题
        public final String time;    // 发布时间
        public final String tag;     // 分类：教务/讲座/实践
        public final String url;     // 详情链接

        public NewsItem(String title, String time, String tag, String url) {
            this.title = title;
            this.time = time;
            this.tag = tag;
            this.url = url;
        }
    }

    private final List<NewsItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    /** 设置点击监听 */
    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    /** 刷新整个列表 */
    public void submit(List<NewsItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载 item_news.xml 布局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NewsItem item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvTime.setText(item.time);
        holder.tvTag.setText(item.tag);

        // 整张卡片可点击，回调给外面处理
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** ViewHolder */
    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvTime;
        final TextView tvTag;

        VH(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvTag = itemView.findViewById(R.id.tv_tag);
        }
    }
}
