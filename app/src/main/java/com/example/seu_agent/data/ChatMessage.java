package com.example.seu_agent.data;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {
    public String content;
    public boolean isUser;
    public boolean isLoading;
    public String processStatus;
    public final List<String> processSteps;
    public long timestamp;

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.isLoading = false;
        this.processStatus = "";
        this.processSteps = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }
}
