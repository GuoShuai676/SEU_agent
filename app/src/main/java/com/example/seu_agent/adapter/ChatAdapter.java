package com.example.seu_agent.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.R;
import com.example.seu_agent.data.ChatMessage;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
private static final int TYPE_SENT=1;

private static final int TYPE_RECV=2;
private List<ChatMessage> messages;
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater=LayoutInflater.from(parent.getContext());
        if(viewType==TYPE_SENT)
        {
            View view=inflater.inflate(R.layout.item_message_sent,parent,false);
            return new SentViewHolder(view);
        }
        else
        {
            View view =inflater.inflate(R.layout.item_message_agent,parent,false);
            return new AgentViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    ChatMessage m=messages.get(position);
    if(holder instanceof SentViewHolder)
    {
        ((SentViewHolder)holder).bind(m);
    }
    else
    {
        ((AgentViewHolder)holder).bind(m);
    }
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
 * 流式输出专用更新：最后一条气泡可见时直接改 ViewHolder 文本，
 * 跳过 notifyItemChanged（避免每帧全量重绑导致卡顿）；不可见时回退 notify。
 */
public void updateStreaming(RecyclerView rv, String newContent)
{
    if(messages.isEmpty())return;
    int pos=messages.size()-1;
    ChatMessage last=messages.get(pos);
    last.content=newContent;                       // 数据同步更新，滚动走远后重绑仍是完整文本
    RecyclerView.ViewHolder h=rv.findViewHolderForAdapterPosition(pos);
    if(h instanceof SentViewHolder)
    {
        ((SentViewHolder)h).tv.setText(newContent);
    }
    else if(h instanceof AgentViewHolder)
    {
        ((AgentViewHolder)h).tv.setText(newContent);
    }
    else
    {
        notifyItemChanged(pos);
    }
}

static class SentViewHolder extends RecyclerView.ViewHolder{
    TextView tv;
    SentViewHolder(View itemView) {
        super(itemView);
        tv=itemView.findViewById(R.id.message_sent);
    }
    void bind(ChatMessage m)
    {
        tv.setText(m.content);
    }
}

    static class AgentViewHolder extends RecyclerView.ViewHolder{
        TextView tv;
        AgentViewHolder(View itemView) {
            super(itemView);
            tv=itemView.findViewById(R.id.message_agent);
        }
        void bind(ChatMessage m)
        {
            tv.setText(m.content);
        }
    }


}
