package com.example.seu_agent.adapter;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;
import com.example.seu_agent.data.ChatMessage;

import java.util.List;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final String PAYLOAD_PROCESS = "process";

    private final List<ChatMessage> messages;
    private Markwon markwon;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) markwon = Markwon.create(parent.getContext());
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            View view = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new MessageViewHolder(view, false, markwon);
        }
        View view = inflater.inflate(R.layout.item_message_agent, parent, false);
        return new MessageViewHolder(view, true, markwon);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_PROCESS)) {
            holder.bindProcess(messages.get(position));
            return;
        }
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(lastPosition());
    }

    public void updateLastMessage(String content) {
        ChatMessage last = lastMessage();
        if (last == null) return;
        completeCurrentProcess(last);
        last.content = content;
        last.isLoading = false;
        last.processStatus = "";
        notifyItemChanged(lastPosition());
    }

    public void updateLastMessageStatus(String status) {
        ChatMessage last = lastMessage();
        if (last == null) return;
        last.processStatus = status;
        last.isLoading = true;
        notifyProcessChanged();
    }

    public void startLastMessageProcess(String label) {
        ChatMessage last = lastMessage();
        if (last == null) return;
        String previous = baseStatus(last.processStatus);
        if (!previous.isEmpty() && !previous.equals(label)) completeCurrentProcess(last);
        last.processStatus = label;
        last.isLoading = true;
        notifyProcessChanged();
    }

    public void finishLastMessageProcess() {
        ChatMessage last = lastMessage();
        if (last == null) return;
        completeCurrentProcess(last);
        last.processStatus = "";
        last.isLoading = false;
        notifyProcessChanged();
    }

    public void updateStreaming(RecyclerView chatList, String content) {
        ChatMessage last = lastMessage();
        if (last == null) return;
        int position = lastPosition();
        last.content = content;
        completeCurrentProcess(last);
        last.processStatus = "";
        last.isLoading = false;

        RecyclerView.ViewHolder visible =
                chatList.findViewHolderForAdapterPosition(position);
        if (!(visible instanceof MessageViewHolder)) {
            notifyItemChanged(position);
            return;
        }
        MessageViewHolder holder = (MessageViewHolder) visible;
        holder.bindProcess(last);
        holder.setStreamingText(content);
    }

    private ChatMessage lastMessage() {
        return messages.isEmpty() ? null : messages.get(lastPosition());
    }

    private int lastPosition() {
        return messages.size() - 1;
    }

    private void notifyProcessChanged() {
        notifyItemChanged(lastPosition(), PAYLOAD_PROCESS);
    }

    private static void completeCurrentProcess(ChatMessage message) {
        String base = baseStatus(message.processStatus);
        if (base.isEmpty()) return;
        String completed = completedLabel(base);
        int size = message.processSteps.size();
        if (size == 0 || !message.processSteps.get(size - 1).equals(completed)) {
            message.processSteps.add(completed);
        }
    }

    private static String baseStatus(String status) {
        if (status == null) return "";
        int newline = status.indexOf('\n');
        return (newline >= 0 ? status.substring(0, newline) : status).trim();
    }

    private static String completedLabel(String label) {
        if (label.equals("正在分析问题") || label.equals("正在思考")) return "已分析问题";
        if (label.startsWith("资料已获取")) return "已获取并整理资料";
        if (label.startsWith("正在")) return "已" + label.substring(2);
        return label;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final boolean isAgent;
        final Markwon markwon;
        final ProgressBar loading;
        final View processRow;
        final TextView processStatus;
        final TextView processSteps;

        MessageViewHolder(View itemView, boolean isAgent, Markwon markwon) {
            super(itemView);
            this.isAgent = isAgent;
            this.markwon = markwon;
            if (isAgent) {
                messageText = itemView.findViewById(R.id.message_agent);
                loading = itemView.findViewById(R.id.message_loading);
                processRow = itemView.findViewById(R.id.message_process_row);
                processStatus = itemView.findViewById(R.id.message_process_status);
                processSteps = itemView.findViewById(R.id.message_process_steps);
                messageText.setLinksClickable(true);
                messageText.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                messageText = itemView.findViewById(R.id.message_sent);
                loading = null;
                processRow = null;
                processStatus = null;
                processSteps = null;
            }
        }

        void bind(ChatMessage message) {
            String content = message.content == null ? "" : message.content;
            if (!isAgent) {
                messageText.setText(content);
                return;
            }
            bindProcess(message);
            messageText.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);
            if (!content.isEmpty()) markwon.setMarkdown(messageText, content);
        }

        void bindProcess(ChatMessage message) {
            if (!isAgent) return;
            boolean running = message.isLoading
                    && message.processStatus != null
                    && !message.processStatus.isEmpty();
            processRow.setVisibility(running ? View.VISIBLE : View.GONE);
            loading.setVisibility(running ? View.VISIBLE : View.GONE);
            if (running) processStatus.setText(message.processStatus);

            if (message.processSteps.isEmpty()) {
                processSteps.setText("");
                processSteps.setVisibility(View.GONE);
                return;
            }
            StringBuilder text = new StringBuilder();
            for (String step : message.processSteps) {
                if (text.length() > 0) text.append('\n');
                text.append("✓ ").append(step);
            }
            processSteps.setText(text);
            processSteps.setVisibility(View.VISIBLE);
        }

        void setStreamingText(String text) {
            if (loading != null) loading.setVisibility(View.GONE);
            messageText.setVisibility(View.VISIBLE);
            messageText.setText(text);
        }
    }
}
