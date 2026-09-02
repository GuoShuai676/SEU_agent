package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeChunk;

import org.json.JSONObject;

import java.util.List;

/**
 * 资讯检索工具（第一个内置工具）：模型判断需要查校园通知时自主调用。
 * 检索策略：按句语义检索（SemanticSearch.topChunks）→ 没命中再按标题关键词（Room LIKE）兜底。
 */
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

        // ① 按句语义检索（带相关性门控）
        List<NoticeChunk> chunks = SemanticSearch.topChunks(ctx, keyword, 3);
        if (chunks.isEmpty()) {
            // ② 语义没命中 → 标题关键词兜底
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

        // 句子结果：每条带父资讯标题/日期
        StringBuilder sb = new StringBuilder();
        for (NoticeChunk c : chunks) {
            sb.append("【").append(c.publishDate == null ? "" : c.publishDate).append("】")
                    .append(c.title == null ? "" : c.title).append("\n")
                    .append(c.text == null ? "" : c.text).append("\n\n");
        }
        return sb.toString().trim();
    }
}
