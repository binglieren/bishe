package com.example.kaoyan.dto;

/**
 * 流式聊天事件 — 区分 reasoning_content 和 content
 */
public class StreamChatEvent {
    private String type; // "reasoning" | "content"
    private String text;

    public StreamChatEvent() {}

    public StreamChatEvent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public static StreamChatEvent reasoning(String text) { return new StreamChatEvent("reasoning", text); }
    public static StreamChatEvent content(String text) { return new StreamChatEvent("content", text); }
}
