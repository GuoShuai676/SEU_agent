package com.example.seu_agent.fragment;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.NativeBridge;
import com.example.seu_agent.R;
import com.example.seu_agent.adapter.ChatAdapter;
import com.example.seu_agent.data.ChatMessage;
import com.example.seu_agent.rag.SemanticSearch;
import com.example.seu_agent.rag.ToolRegistry;
import com.example.seu_agent.rag.ConversationMemory;
import com.example.seu_agent.tool.AgentTools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import eightbitlab.com.blurview.BlurView;

import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

public class AgentFragment extends Fragment {

    private static final int MAX_TOOL_ROUNDS = 5;

    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private RecyclerView chatList;
    private EditText inputBox;
    private TextView sendButton;

    private final NativeBridge bridge = new NativeBridge();
    private final ToolRegistry registry = new ToolRegistry();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private volatile boolean waiting = false;
    private volatile boolean cancelRequested = false;
    private volatile Call activeCall;
    private volatile Thread activeWorker;


    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder streamBuf = new StringBuilder();
    private boolean renderPending = false;
    private volatile boolean answerStreaming = false;
    private volatile int statusGeneration = 0;
    private long lastRenderTime = 0;
    private int lastRenderedLen = 0;
    private static final long RENDER_INTERVAL_MS = 66;
    private ValueAnimator padAnimator;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeVisible) {
                v.setPadding(0, bars.top, 0, ime.bottom+dp(5));
            } else {
                v.setPadding(0, bars.top, 0, v.getPaddingBottom());
                animateBottomPadding(v, dp(90));
            }
            return insets;
        });
        chatList = view.findViewById(R.id.chat_recycle);
        LinearLayoutManager llm = new LinearLayoutManager(requireContext());
        chatList.setLayoutManager(llm);
        chatAdapter = new ChatAdapter(messages);
        chatList.setAdapter(chatAdapter);
        if (chatList.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) chatList.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        inputBox = view.findViewById(R.id.et_chat_input);
        sendButton = view.findViewById(R.id.tvsend);
        setupInputBlur(view);
        sendButton.setOnClickListener(v -> {
            if (waiting) {
                cancelCurrentRequest();
                return;
            }
            String text = inputBox.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                inputBox.setText("");
            }
        });

        Spinner spModel = view.findViewById(R.id.sp_model);
        List<String> modelList = AppConfig.getModels(requireContext());
        ArrayAdapter<String> spAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, modelList);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(spAdapter);
        int curIdx = modelList.indexOf(AppConfig.getLlmModel(requireContext()));
        spModel.setSelection(curIdx >= 0 ? curIdx : 0);
        spModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String picked = modelList.get(position);
                if (!picked.equals(AppConfig.getLlmModel(requireContext()))) {
                    AppConfig.setModel(requireContext(), picked);
                    Toast.makeText(getContext(), "已切换模型：" + picked, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (chatAdapter.getItemCount() == 0) {
            chatAdapter.addMessage(new ChatMessage(
                    "你好，我是东南大学智能小助手，可以问我关于教务、讲座、实践等校园资讯的问题～", false));
        }

        AgentTools.registerBuiltIns(requireContext(), registry);
        final Context ctx = getContext();
        if (ctx != null) {
            new Thread(() -> SemanticSearch.warmUp(ctx)).start();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && isAdded()) AgentTools.registerUserTools(requireContext(), registry);
    }

    private void setupInputBlur(View view) {
        ViewGroup chatArea = view.findViewById(R.id.chat_area);
        BlurView inputBlur = view.findViewById(R.id.chat_input_blur);

        inputBlur.setupWith(chatArea)
                .setBlurRadius(14f)
                .setBlurAutoUpdate(true);
        inputBlur.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        inputBlur.setClipToOutline(true);

        inputBlur.addOnLayoutChangeListener((v, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) -> {
            int paddingBottom = v.getHeight() + dp(12);
            if (chatList.getPaddingBottom() == paddingBottom) return;
            chatList.setPadding(chatList.getPaddingLeft(), chatList.getPaddingTop(),
                    chatList.getPaddingRight(), paddingBottom);
        });
    }

    private void sendMessage(String s) {
        if (waiting) {
            Toast.makeText(getContext(), "上一条还在回复中…", Toast.LENGTH_SHORT).show();
            return;
        }
        chatAdapter.addMessage(new ChatMessage(s, true));
        bridge.addChatMessage(s, true);
        chatList.smoothScrollToPosition(messages.size() - 1);

        ChatMessage pending = new ChatMessage("", false);
        pending.isLoading = true;
        chatAdapter.addMessage(pending);
        chatList.smoothScrollToPosition(messages.size() - 1);

        fetchAiResponse(s, response -> {
            requireActivity().runOnUiThread(() -> {
                chatAdapter.updateLastMessage(response);
                chatList.smoothScrollToPosition(messages.size() - 1);
            });
        });
    }

    private void streamAppend(String delta) {
        if (!answerStreaming) {
            answerStreaming = true;
            stopWorkingStatus();
            streamHandler.post(() -> {
                if (chatAdapter != null) chatAdapter.finishLastMessageProcess();
            });
        }
        synchronized (streamBuf) {
            streamBuf.append(delta);
        }
        if (!renderPending) {
            renderPending = true;
            long since = System.currentTimeMillis() - lastRenderTime;
            long delay = since >= RENDER_INTERVAL_MS ? 0 : RENDER_INTERVAL_MS - since;
            streamHandler.postDelayed(this::renderStream, delay);
        }
    }

    private void renderStream() {
        renderPending = false;
        lastRenderTime = System.currentTimeMillis();
        String text;
        synchronized (streamBuf) {
            text = streamBuf.toString();
        }
        if (text.length() <= lastRenderedLen) return;
        lastRenderedLen = text.length();
        chatAdapter.updateStreaming(chatList, text);
        boolean atBottom = !chatList.canScrollVertically(1);
        if(atBottom)
        chatList.scrollToPosition(messages.size() - 1);
    }

    private void resetStream() {
        synchronized (streamBuf) {
            streamBuf.setLength(0);
        }
        lastRenderedLen = 0;
        renderPending = false;
        answerStreaming = false;
        streamHandler.removeCallbacksAndMessages(null);
    }

    private void showWorkingStatus(String label) {
        final int generation = ++statusGeneration;
        final long startedAt = System.currentTimeMillis();
        statusHandler.removeCallbacksAndMessages(null);
        statusHandler.post(() -> {
            if (generation == statusGeneration && waiting && chatAdapter != null) {
                chatAdapter.startLastMessageProcess(label);
            }
        });
        statusHandler.post(new Runnable() {
            @Override
            public void run() {
                if (generation != statusGeneration || !waiting || chatAdapter == null) return;
                StringBuilder text = new StringBuilder(label);
                long seconds = (System.currentTimeMillis() - startedAt) / 1000;
                if (seconds >= 5) {
                    text.append("\n已等待 ").append(seconds)
                            .append(" 秒，仍在处理中，可点击 ■ 取消");
                }
                if (seconds >= 5) chatAdapter.updateLastMessageStatus(text.toString());
                statusHandler.postDelayed(this, 1000);
            }
        });
    }

    private void stopWorkingStatus() {
        statusGeneration++;
        statusHandler.removeCallbacksAndMessages(null);
    }

    private String toolProgressText(String name, String args) {
        if ("search_notices".equals(name)) {
            String keyword = "";
            try {
                keyword = new JSONObject(args).optString("keyword", "").trim();
            } catch (Exception ignored) {
            }
            if (keyword.length() > 24) keyword = keyword.substring(0, 24) + "…";
            return keyword.isEmpty() ? "正在检索校园资讯" : "正在检索校园资讯 · " + keyword;
        }
        if ("get_weather".equals(name)) return "正在查询天气";
        if ("add_http_tool".equals(name)) return "正在创建自定义工具";
        if ("get_current_time".equals(name)) return "正在确认日期和时间";
        if ("get_current_location".equals(name)) return "正在获取位置信息";
        if ("get_my_courses".equals(name)) return "正在查询你的课表";
        if ("GetMyProfile".equals(name)) return "正在读取个人资料";
        if ("search_web".equals(name)) return "正在搜索网络资料";
        if ("add_campus_task".equals(name)) return "正在添加待办日程";
        return "正在调用工具获取资料";
    }

    private void fetchAiResponse(final String prompt, final ResponseCallback cb) {
        final Activity act = getActivity();
        final Context ctx = getContext();
        if (act == null || ctx == null) return;
        waiting = true;
        cancelRequested = false;
        updateSendButton(true);
        resetStream();
        showWorkingStatus("正在分析问题");
        final int recentTurns = Math.min(6, Math.max(0, (messages.size() - 2) / 2));

        activeWorker = new Thread(() -> {
            try {
                String apiKey = AppConfig.getApiKey(ctx);
                String llmUrl = AppConfig.getLlmUrl(ctx);
                String model = AppConfig.getLlmModel(ctx);
                if (apiKey.isEmpty() || llmUrl.isEmpty()) {
                    act.runOnUiThread(() -> cb.deal("请先在「我的 → 设置」里填写 API Key 和接口地址"));
                    return;
                }

                String toolsJson = registry.buildToolsJson();
                String context = ConversationMemory.findRelevant(ctx, prompt, 3, recentTurns);

                String systemPrompt = AppConfig.getSystemPrompt(ctx);
                String bodyJson = bridge.buildLlmRequest(prompt, context, model, toolsJson, systemPrompt);
                String finalText = null;
                JSONArray agentTrace = new JSONArray();
                Map<String, String> observationCache = new HashMap<>();
                Map<String, String> campusSourceLinks = new LinkedHashMap<>();
                for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                    if (cancelRequested) throw new InterruptedException("cancelled");
                    StringBuilder full = new StringBuilder();
                    Map<Integer, ToolCall> calls = new TreeMap<>();
                    StringBuilder reasoning = new StringBuilder();

                    Request req = new Request.Builder()
                            .url(llmUrl)
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .addHeader("Accept", "text/event-stream")
                            .post(RequestBody.create(bodyJson, MediaType.parse("application/json; charset=utf-8")))
                            .build();

                    Call call = httpClient.newCall(req);
                    activeCall = call;
                    try (Response resp = call.execute()) {
                        if (!resp.isSuccessful()) {
                            String detail = "";
                            try {
                                detail = resp.body() != null ? resp.body().string() : "";
                            } catch (Exception ignored) {
                            }
                            if (detail.length() > 300) detail = detail.substring(0, 300);
                            final String errDetail = detail;
                            android.util.Log.e("SEU_CHAT", "HTTP " + resp.code() + " body=" + errDetail
                                    + "\nreq=" + bodyJson);
                            act.runOnUiThread(() -> cb.deal("请求失败：" + resp.code() + "\n" + errDetail));
                            return;
                        }
                        okhttp3.ResponseBody body = resp.body();
                        if (body == null) {
                            act.runOnUiThread(() -> cb.deal("[空响应]"));
                            return;
                        }

                        BufferedSource source = body.source();
                        String line;
                        while ((line = source.readUtf8Line()) != null) {
                            String delta = bridge.parseLlmStreamLine(line);
                            if (delta != null && !delta.isEmpty()) {
                                full.append(delta);
                                streamAppend(delta);
                            }
                            String rc = bridge.parseLlmStreamReasoning(line);
                            if (rc != null && !rc.isEmpty()) reasoning.append(rc);
                            String tc = bridge.parseLlmStreamToolCall(line);
                            if (tc != null && !tc.isEmpty()) {
                                JSONObject o = new JSONObject(tc);
                                int idx = o.optInt("index", 0);
                                ToolCall c = calls.get(idx);
                                if (c == null) {
                                    c = new ToolCall();
                                    calls.put(idx, c);
                                }
                                if (o.has("id") && !o.isNull("id")) c.id = o.optString("id");
                                if (o.has("name") && !o.isNull("name")) c.name = o.optString("name");
                                String a = o.optString("arguments", "");
                                    c.args.append(a);
                            }
                        }
                    }

                    if (!calls.isEmpty()) {
                        resetStream();
                        JSONArray callsArr = new JSONArray();
                        JSONArray resultsArr = new JSONArray();
                        for (ToolCall c : calls.values()) {
                            if (cancelRequested) throw new InterruptedException("cancelled");
                            String args = c.args.toString();

                            if (!isValidJsonObject(args)) args = "{}";
                            if (c.id == null || c.id.isEmpty()) {
                                c.id = "call_" + System.currentTimeMillis() + "_" + callsArr.length();
                            }
                            String fingerprint = c.name + "\n" + args;
                            String result = observationCache.get(fingerprint);
                            if (result == null) {
                                showWorkingStatus(toolProgressText(c.name, args));
                                result = registry.execute(c.name, args);
                                observationCache.put(fingerprint, result);
                            } else {
                                showWorkingStatus("正在复用已获取的资料");
                            }
                            collectCampusSourceLinks(c.name, result, campusSourceLinks);
                            android.util.Log.i("SEU_CHAT", "tool=" + c.name + " args=" + args
                                    + " resultLen=" + (result == null ? 0 : result.length()));
                            callsArr.put(buildToolCallObject(c.id, c.name, args));
                            resultsArr.put(buildToolResultObject(c.id, result));
                        }
                        showWorkingStatus("资料已获取，正在整理回答");
                        String nextTools = round + 1 >= MAX_TOOL_ROUNDS
                                ? "" : registry.buildToolsJson();
                        bodyJson = bridge.buildLlmToolRequest(context, model, nextTools,
                                agentTrace.toString(), callsArr.toString(), resultsArr.toString(),
                                reasoning.toString(), systemPrompt);
                        agentTrace.put(buildTraceItem(callsArr, resultsArr, reasoning.toString()));
                        continue;
                    }
                    finalText = full.toString();
                    break;
                }

                if (finalText == null || finalText.isEmpty()) finalText = "[无回复]";
                finalText = appendCampusSourceLinks(finalText, campusSourceLinks);
                final String done = finalText;
                act.runOnUiThread(() -> {
                    resetStream();
                    cb.deal(done);
                });
                bridge.addChatMessage(done, false);
                if (!done.startsWith("[")) {
                    new Thread(() -> ConversationMemory.save(ctx, prompt, done)).start();
                }
            } catch (Exception e) {
                if (!cancelRequested) e.printStackTrace();
                final String err = cancelRequested ? "已取消" : "[请求出错] " + e.getMessage();
                act.runOnUiThread(() -> {
                    resetStream();
                    cb.deal(err);
                });
            } finally {
                stopWorkingStatus();
                waiting = false;
                activeCall = null;
                activeWorker = null;
                act.runOnUiThread(() -> updateSendButton(false));
            }
        });
        activeWorker.start();
    }

    private void cancelCurrentRequest() {
        cancelRequested = true;
        stopWorkingStatus();
        Call call = activeCall;
        if (call != null) call.cancel();
        Thread worker = activeWorker;
        if (worker != null) worker.interrupt();
        resetStream();
        chatAdapter.updateLastMessage("已取消");
        updateSendButton(false);
    }

    private void updateSendButton(boolean running) {
        if (sendButton == null) return;
        sendButton.setText(running ? "■" : "↑");
        sendButton.setTextSize(running ? 15 : 22);
        sendButton.setContentDescription(running ? "取消提问" : "发送");
    }

    private JSONObject buildToolCallObject(String id, String name, String args) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", (id == null || id.isEmpty()) ? "call_" + System.currentTimeMillis() : id);
            o.put("name", name == null ? "" : name);
            o.put("arguments", (args == null || args.isEmpty()) ? "{}" : args);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void collectCampusSourceLinks(String toolName, String result,
                                          Map<String, String> links) {
        if (!"search_notices".equals(toolName) || result == null || result.isEmpty()) return;
        try {
            JSONArray rows = new JSONObject(result).optJSONArray("结果");
            if (rows == null) return;
            for (int i = 0; i < rows.length() && links.size() < 3; i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String url = row.optString("原文链接", "").trim();
                String title = row.optString("标题", "查看原通知").trim();
                if (isHttpUrl(url) && !links.containsKey(url)) {
                    links.put(url, title.isEmpty() ? "查看原通知" : title);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String appendCampusSourceLinks(String answer, Map<String, String> links) {
        if (links.isEmpty()) return answer;
        StringBuilder sources = new StringBuilder();
        for (Map.Entry<String, String> entry : links.entrySet()) {
            if (answer.contains(entry.getKey())) continue;
            if (sources.length() == 0) sources.append("\n\n**原文链接**\n");
            sources.append("- [")
                    .append(escapeMarkdownLabel(entry.getValue()))
                    .append("](").append(entry.getKey()).append(")\n");
        }
        return answer + sources;
    }

    private boolean isHttpUrl(String url) {
        return url.startsWith("https://") || url.startsWith("http://");
    }

    private String escapeMarkdownLabel(String value) {
        return value.replace("[", "\\[").replace("]", "\\]");
    }

    private JSONObject buildToolResultObject(String id, String content) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id == null ? "" : id);
            o.put("content", content == null ? "" : content);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private JSONObject buildTraceItem(JSONArray calls, JSONArray results, String reasoning) {
        try {
            JSONObject item = new JSONObject();
            item.put("calls", calls);
            item.put("results", results);
            item.put("reasoning", reasoning == null ? "" : reasoning);
            return item;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private boolean isValidJsonObject(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            return new JSONObject(s).length() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static class ToolCall {
        String id;
        String name;
        final StringBuilder args = new StringBuilder();
    }

    private void animateBottomPadding(View v, int target) {
        if (padAnimator != null) padAnimator.cancel();
        padAnimator = ValueAnimator.ofInt(v.getPaddingBottom(), target);
        padAnimator.addUpdateListener(a -> v.setPadding(
                v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                (int) a.getAnimatedValue()));
        padAnimator.setDuration(220);
        padAnimator.setInterpolator(new DecelerateInterpolator());
        padAnimator.start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        if (waiting) cancelCurrentRequest();
        super.onDestroyView();
        streamHandler.removeCallbacksAndMessages(null);
        statusHandler.removeCallbacksAndMessages(null);
        if (padAnimator != null) {
            padAnimator.cancel();
            padAnimator = null;
        }
        sendButton = null;
    }

    interface ResponseCallback {
        void deal(String result);
    }
}
