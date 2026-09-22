package com.fluid.markdown.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息数据模型。
 * <p>
 * AI 回复会被拆分为多个 Segment，每个 Segment 对应 Adapter 中的一个独立 item。
 * 卡片标记格式：
 * - {@code {{hotel:JSON}}}  → 酒店 item
 * - {@code {{train:JSON}}}  → 火车票 item
 * - {@code {{flight:JSON}}} → 机票 item
 * - {@code {{weather:JSON}}}→ 天气 item
 * 其余为文本 item（纯 Markdown，流式打印）
 */
public class ChatMessage {

    public static final int TYPE_USER = 0;

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
     */
    public static List<Segment> parseSegments(String text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        int cursor = 0;
        while (cursor < text.length()) {
            // 查找最近的卡片标记
            int markerStart = -1;
            String markerType = null;

            String[] markers = {"{{hotel:", "{{train:", "{{flight:", "{{weather:"};
            for (String m : markers) {
                int idx = text.indexOf(m, cursor);
                if (idx >= 0 && (markerStart < 0 || idx < markerStart)) {
                    markerStart = idx;
                    markerType = m;
                }
            }

            if (markerStart < 0) {
                // 没有更多卡片，剩余全部是文本
                if (cursor < text.length()) {
                    segments.add(new Segment(Segment.TYPE_TEXT, text.substring(cursor)));
                }
                break;
            }

            // 标记前的文本段
            if (markerStart > cursor) {
                segments.add(new Segment(Segment.TYPE_TEXT, text.substring(cursor, markerStart)));
            }

            // 找到标记结束位置 }}
            int markerEnd = text.indexOf("}}", markerStart);
            if (markerEnd < 0) {
                segments.add(new Segment(Segment.TYPE_TEXT, text.substring(markerStart)));
                break;
            }

            String json = text.substring(markerStart + markerType.length(), markerEnd);
            int cardType;
            switch (markerType) {
                case "{{hotel:": cardType = Segment.TYPE_HOTEL_CARD; break;
                case "{{train:": cardType = Segment.TYPE_TRAIN_CARD; break;
                case "{{flight:": cardType = Segment.TYPE_FLIGHT_CARD; break;
                case "{{weather:": cardType = Segment.TYPE_WEATHER_CARD; break;
                default: cardType = Segment.TYPE_TEXT; break;
            }
            segments.add(new Segment(cardType, json));

            cursor = markerEnd + 2;
        }

        return segments;
    }

    public static class Segment {
        public static final int TYPE_TEXT          = 0;
        public static final int TYPE_HOTEL_CARD    = 1;
        public static final int TYPE_TRAIN_CARD    = 2;
        public static final int TYPE_FLIGHT_CARD   = 3;
        public static final int TYPE_WEATHER_CARD  = 4;

        public int type;
        public String content;

        public Segment(int type, String content) {
            this.type = type;
            this.content = content;
        }
    }
}
