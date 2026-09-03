package com.example.seu_agent.fragment;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
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

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.NativeBridge;
import com.example.seu_agent.NoticeChunk;
import com.example.seu_agent.R;
import com.example.seu_agent.adapter.ChatAdapter;
import com.example.seu_agent.data.ChatMessage;
import com.example.seu_agent.rag.SearchNoticesTool;
import com.example.seu_agent.rag.SemanticSearch;
import com.example.seu_agent.rag.ToolRegistry;
import com.example.seu_agent.tool.GetMyProfileTool;
import com.example.seu_agent.tool.GetLocationTool;
import com.example.seu_agent.tool.GetCoursesTool;
import com.example.seu_agent.tool.GettimeTool;
import com.example.seu_agent.tool.WeatherTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/** 小助手聊天页。 */
public class AgentFragment extends Fragment {

    private static final int MAX_TOOL_ROUNDS = 5;

    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rv;
    private EditText InputBox;
    private TextView sendButton;

    private final NativeBridge bridge = new NativeBridge(); // JNI 桥

    // 目前只有资讯检索，后面加工具时继续在 onViewCreated 里注册
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
    private final StringBuilder streamBuf = new StringBuilder();
    private boolean renderPending = false;
    private long lastRenderTime = 0;
    private int lastRenderedLen = 0;
    private static final long RENDER_INTERVAL_MS = 66;
    private int navBarHeight;                  //底部导航栏高度
    private ValueAnimator padAnimator;  //动画

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 给状态栏和键盘留位置
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeVisible) {
                // 键盘弹出
                v.setPadding(0, bars.top, 0, ime.bottom+dp(5));
            } else {
                v.setPadding(0, bars.top, 0, v.getPaddingBottom());
                animateBottomPadding(v, dp(90));
            }
            return insets;
        });
        measureNavBarHeight();

        rv = view.findViewById(R.id.chat_recycle);
        LinearLayoutManager llm = new LinearLayoutManager(requireContext());
        rv.setLayoutManager(llm);
        adapter = new ChatAdapter(messages);
        rv.setAdapter(adapter);

        InputBox = view.findViewById(R.id.et_chat_input);
        sendButton = view.findViewById(R.id.tvsend);
        // 发送按钮
        sendButton.setOnClickListener(v -> {
            if (waiting) {
                cancelCurrentRequest();
                return;
            }
            String text = InputBox.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                InputBox.setText("");
            }
        });

        // 右上角模型切换
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

        // 键盘上的“发送”键
        InputBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String text = InputBox.getText().toString().trim();
                if (!text.isEmpty()) {
                    sendMessage(text);
                    InputBox.setText("");
                }
                return true;
            }
            return false;
        });

        if (adapter.getItemCount() == 0) {
            adapter.addMessage(new ChatMessage(
                    "你好，我是东南大学智能小助手，可以问我关于教务、讲座、实践等校园资讯的问题～", false));
        }

        // 注册工具，更新上下文
        registry.register(new SearchNoticesTool(requireContext()));
        registry.register(new GetMyProfileTool(requireContext()));
        registry.register(new WeatherTool());
        registry.register(new GettimeTool());
        registry.register(new GetLocationTool());
        registry.register(new GetCoursesTool(requireContext()));
        final Context ctx = getContext();
        if (ctx != null) {
            new Thread(() -> SemanticSearch.warmUp(ctx)).start();
        }
    }

    private void sendMessage(String s) {
        if (waiting) {
            Toast.makeText(getContext(), "上一条还在回复中…", Toast.LENGTH_SHORT).show();
            return;
        }

        adapter.addMessage(new ChatMessage(s, true));
        bridge.addChatMessage(s, true);
        rv.smoothScrollToPosition(messages.size() - 1);


        adapter.addMessage(new ChatMessage("…", false));
        rv.smoothScrollToPosition(messages.size() - 1);


        fetchAiResponse(s, response -> {
            requireActivity().runOnUiThread(() -> {
                adapter.updateLastMessage(response);
                rv.smoothScrollToPosition(messages.size() - 1);
            });
        });
    }
    private void streamAppend(String delta) {
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
        adapter.updateStreaming(rv, text);
        rv.scrollToPosition(messages.size() - 1);
    }

    private void resetStream() {
        synchronized (streamBuf) {
            streamBuf.setLength(0);
        }
        lastRenderedLen = 0;
        renderPending = false;
        streamHandler.removeCallbacksAndMessages(null);
    }

    // 请求放在后台线程里。模型要用工具时，执行完再带着结果请求一次。
    private void fetchAiResponse(final String prompt, final ResponseCallback cb) {
        final Activity act = getActivity();
        final Context ctx = getContext();
        if (act == null || ctx == null) return;
        waiting = true;
        cancelRequested = false;
        updateSendButton(true);
        resetStream();

        activeWorker = new Thread(() -> {
            try {
                String apiKey = AppConfig.getApiKey(ctx);
                String llmUrl = AppConfig.getLlmUrl(ctx);
                String model = AppConfig.getLlmModel(ctx);
                if (apiKey.isEmpty() || llmUrl.isEmpty()) {
                    act.runOnUiThread(() -> cb.pnResult("请先在「我的 → 设置」里填写 API Key 和接口地址"));
                    return;
                }

                String toolsJson = registry.buildToolsJson();
                String context = buildLocalContext(ctx, prompt);

                String bodyJson = bridge.buildLlmRequest(prompt, context, model, toolsJson);
                String finalText = null;
                JSONArray agentTrace = new JSONArray();
                Map<String, String> observationCache = new HashMap<>();
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
                            act.runOnUiThread(() -> cb.pnResult("请求失败：" + resp.code() + "\n" + errDetail));
                            return;
                        }
                        okhttp3.ResponseBody body = resp.body();
                        if (body == null) {
                            act.runOnUiThread(() -> cb.pnResult("[空响应]"));
                            return;
                        }

                        BufferedSource source = body.source();
                        String line;
                        while ((line = source.readUtf8Line()) != null) {
                            String delta = bridge.parseLlmStreamLine(line);
                            if (delta != null && !delta.isEmpty()) {
                                full.append(delta);
                                streamAppend(delta);   // 固定帧率合并渲染
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
                                if (!a.isEmpty()) c.args.append(a);
                            }
                        }
                    }

                    if (!calls.isEmpty()) {
                        // 参数是分段返回的，到这里才是完整的一次工具调用
                        resetStream();
                        act.runOnUiThread(() -> cb.pnResult("正在调用工具…"));
                        JSONArray callsArr = new JSONArray();
                        JSONArray resultsArr = new JSONArray();
                        for (ToolCall c : calls.values()) {
                            if (cancelRequested) throw new InterruptedException("cancelled");
                            String args = c.args.toString();
                            // 参数偶尔会被流截断，至少保证续轮 JSON 合法
                            if (!isValidJsonObject(args)) args = "{}";
                            if (c.id == null || c.id.isEmpty()) {
                                c.id = "call_" + System.currentTimeMillis() + "_" + callsArr.length();
                            }
                            String fingerprint = c.name + "\n" + args;
                            String result = observationCache.get(fingerprint);
                            if (result == null) {
                                result = registry.execute(c.name, args);
                                observationCache.put(fingerprint, result);
                            }
                            android.util.Log.i("SEU_CHAT", "tool=" + c.name + " args=" + args
                                    + " resultLen=" + (result == null ? 0 : result.length()));
                            callsArr.put(buildToolCallObject(c.id, c.name, args));
                            resultsArr.put(buildToolResultObject(c.id, result));
                        }
                        String nextTools = round + 1 >= MAX_TOOL_ROUNDS ? "" : toolsJson;
                        bodyJson = bridge.buildLlmToolRequest(context, model, nextTools,
                                agentTrace.toString(), callsArr.toString(), resultsArr.toString(),
                                reasoning.toString());
                        agentTrace.put(buildTraceItem(callsArr, resultsArr, reasoning.toString()));
                        continue;
                    }
                    finalText = full.toString();
                    break;
                }

                if (finalText == null || finalText.isEmpty()) finalText = "[无回复]";
                final String done = finalText;
                act.runOnUiThread(() -> {
                    // 先取消排队中的纯文本刷新，避免它在 Markdown 渲染后又覆盖 TextView。
                    resetStream();
                    cb.pnResult(done);
                });
                bridge.addChatMessage(done, false);
            } catch (Exception e) {
                if (!cancelRequested) e.printStackTrace();
                final String err = cancelRequested ? "已取消" : "[请求出错] " + e.getMessage();
                act.runOnUiThread(() -> {
                    resetStream();
                    cb.pnResult(err);
                });
            } finally {
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
        Call call = activeCall;
        if (call != null) call.cancel();
        Thread worker = activeWorker;
        if (worker != null) worker.interrupt();
        resetStream();
        adapter.updateLastMessage("已取消");
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

    // 一个回复中可能有多个 tool call，所以按 index 分开拼参数
    private static class ToolCall {
        String id;
        String name;
        final StringBuilder args = new StringBuilder();
    }

    private void measureNavBarHeight() {
        View nav = getActivity() == null ? null : getActivity().findViewById(R.id.navigation);
        if (nav == null) return;
        final ViewGroup capsule = (ViewGroup) nav.getParent();
        capsule.post(() -> {
            if (capsule.getHeight() > 0) navBarHeight = capsule.getHeight();
        });
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
        if (padAnimator != null) {
            padAnimator.cancel();
            padAnimator = null;
        }
        sendButton = null;
    }

    // 短追问沿用聊天历史，其他问题从本地资讯里取最相关的五个片段
    private String buildLocalContext(Context ctx, String question) {
        try {

            boolean shortFollowUp = question.length() < 12 && messages.size() >= 4;
            if (shortFollowUp) return "";

            List<NoticeChunk> chunks = SemanticSearch.topChunks(ctx, question, 5);
            if (chunks.isEmpty()) return "";

            JSONArray arr = new JSONArray();
            for (NoticeChunk c : chunks) {
                JSONObject o = new JSONObject();
                o.put("title", c.title == null ? "" : c.title);
                o.put("date", c.publishDate == null ? "" : c.publishDate);
                o.put("content", c.text == null ? "" : c.text);
                arr.put(o);
            }
            return bridge.buildContext(arr.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    interface ResponseCallback {
        void pnResult(String result);
    }
}
