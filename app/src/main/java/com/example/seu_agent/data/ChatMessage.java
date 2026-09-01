package com.example.seu_agent.data;

public class ChatMessage {
    public String content;
    public boolean isUser;
    public long timestamp;

    public ChatMessage(String s,boolean isuser)
    {
        this.content=s;
        this.isUser=isuser;
        this.timestamp=System.currentTimeMillis();
    }
}
