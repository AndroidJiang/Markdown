package com.fluid.markdown.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息数据模型。
 * <p>
 * AI 回复的 markdown 文本中可嵌入卡片标记 {@code {{hotel:JSON}}}，
 * TypewriterController 会将其拆分为多个 Segment 依次流式渲染。
 */
public class ChatMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;

    public int type;
    public String userText;

    // AI 回复相关
    public String fullResponse;
    public List<Segment> segments;
    public int currentSegmentIndex;
    public boolean isStreaming;

    public ChatMessage(int type, String text) {
        this.type = type;
        if (type == TYPE_USER) {
            this.userText = text;
        } else {
            this.fullResponse = text;
            this.segments = parseSegments(text);
            this.currentSegmentIndex = 0;
            this.isStreaming = false;
        }
    }

    /**
     * 将原始回复文本按卡片标记拆分为多个段落。
     * 卡片标记格式：{{hotel:JSON_DATA}}
     */
    public static List<Segment> parseSegments(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        int cursor = 0;
        String marker = "{{hotel:";
        while (true) {
            int markerStart = text.indexOf(marker, cursor);
            if (markerStart < 0) {
                if (cursor < text.length()) {
                    segments.add(new Segment(Segment.TYPE_TEXT, text.substring(cursor)));
                }
                break;
            }

            if (markerStart > cursor) {
                segments.add(new Segment(Segment.TYPE_TEXT, text.substring(cursor, markerStart)));
            }

            int markerEnd = text.indexOf("}}", markerStart);
            if (markerEnd < 0) {
                segments.add(new Segment(Segment.TYPE_TEXT, text.substring(markerStart)));
                break;
            }

            String json = text.substring(markerStart + marker.length(), markerEnd);
            segments.add(new Segment(Segment.TYPE_HOTEL_CARD, json));

            cursor = markerEnd + 2;
        }

        return segments;
    }

    public static class Segment {
        public static final int TYPE_TEXT = 0;
        public static final int TYPE_HOTEL_CARD = 1;

        public int type;
        public String content;

        public Segment(int type, String content) {
            this.type = type;
            this.content = content;
        }
    }
}
