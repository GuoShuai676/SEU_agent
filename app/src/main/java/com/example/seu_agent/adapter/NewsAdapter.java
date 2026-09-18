package com.example.seu_agent.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;

import java.util.ArrayList;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.VH> {


    public interface OnItemClickListener {
        void onItemClick(NewsItem item);
    }

    public static class NewsItem {
        public final String title;
        public final String time;
        public final String tag;
        public final String url;

        public NewsItem(String title, String time, String tag, String url) {
            this.title = title;
            this.time = time;
            this.tag = tag;
            this.url = url;
        }
    }

    private final List<NewsItem> items = new ArrayList<>();
    private OnItemClickListener listener;


    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void submit(List<NewsItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

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
        holder.ivCover.setImageResource(coverForTag(item.tag));
        holder.tvCoverLabel.setText(labelForTag(item.tag));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int coverForTag(String tag) {
        if (tag == null) return R.drawable.news_cover_general;
        if (tag.contains("教务")) return R.drawable.news_cover_academic;
        if (tag.contains("讲座")) return R.drawable.news_cover_lecture;
        if (tag.contains("实践")) return R.drawable.news_cover_practice;
        return R.drawable.news_cover_general;
    }

    private String labelForTag(String tag) {
        if (tag == null) return "校园";
        if (tag.contains("教务")) return "教务";
        if (tag.contains("讲座")) return "讲座";
        if (tag.contains("实践")) return "实践";
        return "校园";
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvTime;
        final TextView tvTag;
        final TextView tvCoverLabel;
        final ImageView ivCover;

        VH(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvTag = itemView.findViewById(R.id.tv_tag);
            tvCoverLabel = itemView.findViewById(R.id.tv_cover_label);
            ivCover = itemView.findViewById(R.id.iv_cover);
        }
    }
}
