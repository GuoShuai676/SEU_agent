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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * 小助手页：手机直连 DeepSeek（API Key 只存在本机 SharedPreferences，最安全）。
 * 流程：输入 → 本地资讯做上下文(C++ buildContext) → 组装请求(C++ buildLlmRequest)
 *       → OkHttp 流式请求 → SSE 逐行解析(C++ parseLlmStreamLine) → 回调逐段刷新 UI
 */
public class AgentFragment extends Fragment {

    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rv;
    private EditText InputBox;

    private final NativeBridge bridge = new NativeBridge(); // JNI 桥

    // 工具注册表：模型可自主调用的能力（扩展工具 = 实现 AgentTool + register 一行）
    private final ToolRegistry registry = new ToolRegistry();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // 流式回复较长，放宽读超时
            .build();

    private volatile boolean waiting = false;    // 防止连点发送

    // 流式渲染（固定帧率合并）：后台线程只写缓冲，UI 线程定时取"最新值"渲染。
    // 避免每次增量都 post 导致刷新队列堆积、以及大文本反复 setText 卡顿。
    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder streamBuf = new StringBuilder();
    private boolean renderPending = false;
    private long lastRenderTime = 0;
    private int lastRenderedLen = 0;
    private static final long RENDER_INTERVAL_MS = 66;   // ~15fps，肉眼流畅且不重

    // 底部留白：平时让出悬浮导航栏高度，键盘弹出时贴键盘上沿（跟随 IME 动画）
    private int navBarHeight;                     // 悬浮导航栏高度缓存
    private ValueAnimator padAnimator;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agent, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 顶部留状态栏高度；底部跟随键盘/悬浮导航栏（带动画平滑过渡）
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (imeVisible) {
                // 键盘弹出：直接跟 IME 高度走（系统键盘本身就在做动画）
                v.setPadding(0, bars.top, 0, ime.bottom);
            } else {
                // 键盘收起：平滑落回「悬浮导航栏高度 + 16dp」的常态位置
                v.setPadding(0, bars.top, 0, v.getPaddingBottom());
                int target = navBarHeight > 0 ? navBarHeight + dp(16) : dp(90);
                animateBottomPadding(v, target);
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
        TextView tx = view.findViewById(R.id.tvsend);
        tx.setOnClickListener(v -> {
            String text = InputBox.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                InputBox.setText("");
            }
        });

        // 右上角模型切换：列表来自设置里的模型管理，切换立即生效
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

        // 开场白
        if (adapter.getItemCount() == 0) {
            adapter.addMessage(new ChatMessage(
                    "你好，我是东南大学智能小助手，可以问我关于教务、讲座、实践等校园资讯的问题～", false));
        }

        // 注册工具：模型自主决定何时调用（扩展工具就在这加一行）
        registry.register(new SearchNoticesTool(requireContext()));

        // 预热语义检索：后台加载模型 + 补齐资讯向量，让首次提问不卡顿
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
        // 用户消息上屏 + 记入 C++ 聊天历史
        adapter.addMessage(new ChatMessage(s, true));
        bridge.addChatMessage(s, true);
        rv.smoothScrollToPosition(messages.size() - 1);

        // 助手占位气泡（流式输出逐段回填到这个气泡）
        adapter.addMessage(new ChatMessage("…", false));
        rv.smoothScrollToPosition(messages.size() - 1);

        // 请求 DeepSeek，回调里逐段更新
        fechAiResponse(s, response -> {
            requireActivity().runOnUiThread(() -> {
                adapter.updateLastMessage(response);
                rv.smoothScrollToPosition(messages.size() - 1);
            });
        });
    }

    /**
     * 后台线程追加流式增量：只写缓冲，并安排一次合并的 UI 渲染。
     * 渲染排队中时不再重复 post（最新值覆盖旧值，旧帧自动丢弃）。
     */
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

    /** UI 线程渲染：取缓冲最新文本直接改气泡，瞬时滚动到末尾 */
    private void renderStream() {
        renderPending = false;
        lastRenderTime = System.currentTimeMillis();
        String text;
        synchronized (streamBuf) {
            text = streamBuf.toString();
        }
        if (text.length() <= lastRenderedLen) return;   // 无新增，跳过
        lastRenderedLen = text.length();
        adapter.updateStreaming(rv, text);
        rv.scrollToPosition(messages.size() - 1);
    }

    /** 新一轮对话前重置流式缓冲与渲染状态 */
    private void resetStream() {
        synchronized (streamBuf) {
            streamBuf.setLength(0);
        }
        lastRenderedLen = 0;
        renderPending = false;
        streamHandler.removeCallbacksAndMessages(null);
    }

    /** 后台线程：OkHttp 直连 DeepSeek，SSE 流式读回复。
     * 工具调用流程：首轮请求带 tools → 模型返回 tool_calls → 本地执行工具
     *              → 结果回填续轮 → 模型生成最终回答（最多 3 轮，防止死循环）。
     */
    private void fechAiResponse(final String prompt, final ResponseCallback cb) {
        final Activity act = getActivity();   // 提前捕获，线程里不碰 Fragment
        final Context ctx = getContext();
        if (act == null || ctx == null) return;
        waiting = true;
        resetStream();   // 新一轮：清空流式缓冲

        new Thread(() -> {
            try {
                String apiKey = AppConfig.getApiKey(ctx);
                String llmUrl = AppConfig.getLlmUrl(ctx);
                String model = AppConfig.getLlmModel(ctx);
                if (apiKey.isEmpty() || llmUrl.isEmpty()) {
                    act.runOnUiThread(() -> cb.pnResult("请先在「我的 → 设置」里填写 API Key 和接口地址"));
                    return;
                }

                String toolsJson = registry.buildToolsJson();   // 工具声明（空 = 不启用工具）
                String context = buildLocalContext(ctx, prompt); // 被动检索上下文（门控）

                // 最多 3 轮：模型可能连续调用多个工具
                String bodyJson = bridge.buildLlmRequest(prompt, context, model, toolsJson);
                String finalText = null;
                for (int round = 0; round < 3; round++) {
                    StringBuilder full = new StringBuilder();
                    Map<Integer, ToolCall> calls = new TreeMap<>(); // 并行工具调用按 index 分组
                    StringBuilder reasoning = new StringBuilder();   // thinking 模型思考增量

                    Request req = new Request.Builder()
                            .url(llmUrl)
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .addHeader("Accept", "text/event-stream")
                            .post(RequestBody.create(bodyJson,
                                    MediaType.parse("application/json; charset=utf-8")))
                            .build();

                    try (Response resp = httpClient.newCall(req).execute()) {
                        if (!resp.isSuccessful()) {
                            // 把响应体带出来：DeepSeek 的 400 会写明具体原因
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

                        // 逐行读 SSE：回答增量 + 工具调用增量
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
                                int idx = o.optInt("index", 0);   // 并行多次调用用 index 区分
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
                        // 模型要调用工具：先提示用户，逐个执行，全部结果回填后续轮
                        act.runOnUiThread(() -> cb.pnResult("正在检索本地资讯…"));
                        JSONArray callsArr = new JSONArray();
                        JSONArray resultsArr = new JSONArray();
                        for (ToolCall c : calls.values()) {
                            String args = c.args.toString();
                            // 保险：流式拼出的参数不完整/非法时，用空参数重来，避免续轮 400
                            if (!isValidJsonObject(args)) args = "{}";
                            String result = registry.execute(c.name, args);
                            android.util.Log.i("SEU_CHAT", "tool=" + c.name + " args=" + args
                                    + " resultLen=" + (result == null ? 0 : result.length()));
                            callsArr.put(buildToolCallObject(c.id, c.name, args));
                            resultsArr.put(buildToolResultObject(c.id, result));
                        }
                        bodyJson = bridge.buildLlmToolRequest(context, model, toolsJson,
                                callsArr.toString(), resultsArr.toString(), reasoning.toString());
                        continue;
                    }
                    finalText = full.toString();
                    break;
                }

                // 收尾：完整回复上屏 + 记入历史
                if (finalText == null || finalText.isEmpty()) finalText = "[无回复]";
                final String done = finalText;
                act.runOnUiThread(() -> cb.pnResult(done));
                bridge.addChatMessage(done, false);
            } catch (Exception e) {
                e.printStackTrace();
                final String err = "[请求出错] " + e.getMessage();
                act.runOnUiThread(() -> cb.pnResult(err));
            } finally {
                waiting = false;
            }
        }).start();
    }

    /** 组装单个工具调用对象（续轮请求 calls 数组元素） */
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

    /** 组装单个工具结果对象（续轮请求 results 数组元素） */
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

    /** 判断字符串是否为合法的 JSON 对象 */
    private boolean isValidJsonObject(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            return new JSONObject(s).length() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 一次工具调用的流式积累（按 index 分组，arguments 分片拼接） */
    private static class ToolCall {
        String id;
        String name;
        final StringBuilder args = new StringBuilder();
    }

    /** 缓存悬浮导航栏高度（键盘收起时输入栏要停在它上方） */
    private void measureNavBarHeight() {
        View nav = getActivity() == null ? null : getActivity().findViewById(R.id.navigation);
        if (nav == null) return;
        final ViewGroup capsule = (ViewGroup) nav.getParent();
        capsule.post(() -> {
            if (capsule.getHeight() > 0) navBarHeight = capsule.getHeight();
        });
    }

    /** 平滑动画调整 View 底部内边距 */
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
        super.onDestroyView();
        // 取消排队中的流式渲染回调，避免销毁后回调到已回收的 View
        streamHandler.removeCallbacksAndMessages(null);
        if (padAnimator != null) {
            padAnimator.cancel();
            padAnimator = null;
        }
    }

    /**
     * 本地检索 → 上下文：
     * ① 检索分数低于阈值的提问（闲聊/无关）→ 不注入任何资讯；
     * ② 短追问（已有对话历史）→ 不重新检索，靠对话历史回答，避免上下文每轮漂移；
     * ③ 命中 → 按句检索 top-5，每条句子作为一段上下文（带父资讯标题/日期）。
     */
    private String buildLocalContext(Context ctx, String question) {
        try {
            // ② 短追问不注入：避免"那截止时间呢"这种追问重新检索出别的资讯带偏话题
            boolean shortFollowUp = question.length() < 12 && messages.size() >= 4;
            if (shortFollowUp) return "";

            List<NoticeChunk> chunks = SemanticSearch.topChunks(ctx, question, 5);
            if (chunks.isEmpty()) return ""; // ① 无关提问不注入

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
