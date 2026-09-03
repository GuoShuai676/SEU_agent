package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeChunk;

import org.json.JSONObject;

import java.util.List;

/** 给模型用的校园资讯检索。 */
public class SearchNoticesTool implements AgentTool {

    private final Context ctx;

    public SearchNoticesTool(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public String getName() {
        return "search_notices";
    }

    @Override
    public String getDescription() {
        return "检索本地校园资讯库（东南大学教务处：教务通知、讲座、实践、竞赛等）。"
                + "当用户询问具体通知内容、时间、地点、对象、报名方式、截止日期，"
                + "或需要确认某条通知是否存在时调用。";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"keyword\":{\"type\":\"string\",\"description\":\"检索关键词，如：选课、讲座、竞赛、助管\"}"
                + "},\"required\":[\"keyword\"]}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        String keyword = new JSONObject(argumentsJson).optString("keyword", "").trim();
        if (keyword.isEmpty()) return "请提供检索关键词";
        List<NoticeChunk> chunks = SemanticSearch.topChunks(ctx, keyword, 3);
        if (chunks.isEmpty()) {
            // 向量没命中时再试标题关键字
            List<Notice> hits = AppDatabase.get(ctx).noticeDao().search(keyword);
            if (hits.size() > 3) hits = hits.subList(0, 3);
            if (hits.isEmpty()) return "未找到与「" + keyword + "」相关的资讯";
            StringBuilder sb = new StringBuilder();
            for (Notice n : hits) {
                sb.append("【").append(n.publishDate == null ? "" : n.publishDate).append("】")
                        .append(n.title == null ? "" : n.title).append("\n");
                String content = n.content == null ? "" : n.content;
                if (content.length() > 300) content = content.substring(0, 300);
                sb.append(content).append("\n\n");
            }
            return sb.toString().trim();
        }

        StringBuilder sb = new StringBuilder();
        for (NoticeChunk c : chunks) {
            sb.append("【").append(c.publishDate == null ? "" : c.publishDate).append("】")
                    .append(c.title == null ? "" : c.title).append("\n")
                    .append(c.text == null ? "" : c.text).append("\n\n");
        }
        return sb.toString().trim();
    }
}
