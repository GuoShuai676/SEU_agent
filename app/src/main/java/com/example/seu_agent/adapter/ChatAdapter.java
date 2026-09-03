package com.example.seu_agent.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;
import com.example.seu_agent.data.ChatMessage;

import io.noties.markwon.Markwon;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
private static final int TYPE_SENT=1;

private static final int TYPE_RECV=2;
private List<ChatMessage> messages;
private Markwon markwon;
public ChatAdapter(List<ChatMessage>m)
{
    this.messages=m;
}
@Override
public int getItemViewType(int position)
{
    return  messages.get(position).isUser? TYPE_SENT:TYPE_RECV;
}

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) markwon = Markwon.create(parent.getContext());
        LayoutInflater inflater=LayoutInflater.from(parent.getContext());
        if(viewType==TYPE_SENT)
        {
            View view=inflater.inflate(R.layout.item_message_sent,parent,false);
            return new MessageViewHolder(view, false, markwon);
        }
        else
        {
            View view =inflater.inflate(R.layout.item_message_agent,parent,false);
            return new MessageViewHolder(view, true, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

public void addMessage(ChatMessage m)
{
    messages.add(m);
    notifyItemInserted(messages.size()-1);
}

public void updateLastMessage(String newContent)
{
    if(messages.isEmpty())return;
    ChatMessage last=messages.get(messages.size()-1);
    last.content=newContent;
    notifyItemChanged(messages.size()-1);
}

/**
 * 流式输出
 */
public void updateStreaming(RecyclerView rv, String newContent)
{
    if(messages.isEmpty())return;
    int pos=messages.size()-1;
    ChatMessage last=messages.get(pos);
    last.content=newContent;                       // 数据同步更新，滚动走远后重绑仍是完整文本
    RecyclerView.ViewHolder found=rv.findViewHolderForAdapterPosition(pos);
    if(found == null) notifyItemChanged(pos);
    else ((MessageViewHolder)found).setStreamingText(newContent);
}

static class MessageViewHolder extends RecyclerView.ViewHolder {
    final TextView tv;
    final boolean isAgent;
    final Markwon markwon;

    MessageViewHolder(View itemView, boolean isAgent, Markwon markwon) {
        super(itemView);
        this.isAgent=isAgent;
        this.markwon=markwon;
        if(isAgent) tv=itemView.findViewById(R.id.message_agent);
        else tv=itemView.findViewById(R.id.message_sent);
    }

    void bind(ChatMessage message) {
        String content=message.content == null ? "" : message.content;
        if(isAgent) markwon.setMarkdown(tv, content);
        else tv.setText(content);
    }

    void setStreamingText(String text) {
        tv.setText(text);
    }
}


}
