package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeChunk;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchNoticesTool implements AgentTool {

    private static final Pattern LATIN_KEYWORD =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,}");

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
                + "或需要确认某条通知是否存在时调用。结果包含原文链接，回答中必须使用"
                + "Markdown格式[通知标题](原文链接)提供可点击来源。";
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

        List<Notice> keywordHits = searchTitles(keyword);
        if (!keywordHits.isEmpty()) return noticeResults(keyword, keywordHits);

        List<NoticeChunk> chunks = SemanticSearch.topChunks(ctx, keyword, 3);
        if (chunks.isEmpty()) {
            return "未找到与「" + keyword + "」相关的资讯";
        }

        JSONArray results = new JSONArray();
        for (NoticeChunk c : chunks) {
            results.put(resultItem(c.title, c.publishDate, c.text, c.url));
        }
        return resultEnvelope(keyword, results).toString();
    }

    private List<Notice> searchTitles(String keyword) {
        Map<String, Notice> unique = new LinkedHashMap<>();
        addHits(unique, AppDatabase.get(ctx).noticeDao().search(keyword));

        Matcher matcher = LATIN_KEYWORD.matcher(keyword);
        while (matcher.find() && unique.size() < 3) {
            addHits(unique, AppDatabase.get(ctx).noticeDao().search(matcher.group()));
        }

        String concise = keyword.replaceAll(
                "关于|请问|查询|查找|一下|校园|资讯|信息|相关|通知|竞赛|比赛", "").trim();
        if (concise.length() >= 2 && unique.size() < 3) {
            addHits(unique, AppDatabase.get(ctx).noticeDao().search(concise));
        }
        return new java.util.ArrayList<>(unique.values());
    }

    private void addHits(Map<String, Notice> out, List<Notice> hits) {
        for (Notice notice : hits) {
            if (out.size() >= 3) break;
            if (notice.url != null) out.put(notice.url, notice);
        }
    }

    private String noticeResults(String keyword, List<Notice> hits) throws Exception {
        JSONArray results = new JSONArray();
        for (Notice n : hits) {
            String content = n.content == null ? "" : n.content;
            if (content.length() > 300) content = content.substring(0, 300);
            results.put(resultItem(n.title, n.publishDate, content, n.url));
        }
        return resultEnvelope(keyword, results).toString();
    }

    private JSONObject resultItem(String title, String date, String snippet, String url)
            throws Exception {
        JSONObject item = new JSONObject();
        item.put("标题", title == null ? "" : title);
        item.put("发布日期", date == null ? "" : date);
        item.put("相关原文", snippet == null ? "" : snippet);
        item.put("原文链接", url == null ? "" : url);
        return item;
    }

    private JSONObject resultEnvelope(String keyword, JSONArray results) throws Exception {
        JSONObject root = new JSONObject();
        root.put("检索词", keyword);
        root.put("结果", results);
        root.put("引用要求", "回答涉及这些通知时，在末尾使用[通知标题](原文链接)列出来源");
        return root;
    }
}
