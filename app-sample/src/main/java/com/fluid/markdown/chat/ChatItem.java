package com.fluid.markdown.chat;

import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;

/**
 * 回答 item 数据模型（参考 egame ChatItem）。
 * 每种卡片类型对应一个子类。
 */
public abstract class ChatItem {
    public abstract String getId();
    public abstract int getViewType();

    // 文本
    public static class TextItem extends ChatItem {
        public final String id;
        public String text;
        public boolean isStreaming;
        public boolean isHistory;
        public PrinterMarkDownTextView.MarkDownPrintData printData;

        public TextItem(String id, String text, boolean isStreaming) {
            this.id = id;
            this.text = text;
            this.isStreaming = isStreaming;
            this.isHistory = false;
        }

        @Override public String getId() { return id; }
        @Override public int getViewType() { return AnswerCardAdapter.VT_TEXT; }
    }

    // 酒店卡片
    public static class HotelCardItem extends ChatItem {
        public final String id;
        public final String json;

        public HotelCardItem(String id, String json) {
            this.id = id;
            this.json = json;
        }

        @Override public String getId() { return id; }
        @Override public int getViewType() { return AnswerCardAdapter.VT_HOTEL; }
    }

    // 火车票卡片
    public static class TrainCardItem extends ChatItem {
        public final String id;
        public final String json;

        public TrainCardItem(String id, String json) {
            this.id = id;
            this.json = json;
        }

        @Override public String getId() { return id; }
        @Override public int getViewType() { return AnswerCardAdapter.VT_TRAIN; }
    }

    // 机票卡片
    public static class FlightCardItem extends ChatItem {
        public final String id;
        public final String json;

        public FlightCardItem(String id, String json) {
            this.id = id;
            this.json = json;
        }

        @Override public String getId() { return id; }
        @Override public int getViewType() { return AnswerCardAdapter.VT_FLIGHT; }
    }

    // 天气卡片
    public static class WeatherCardItem extends ChatItem {
        public final String id;
        public final String json;

        public WeatherCardItem(String id, String json) {
            this.id = id;
            this.json = json;
        }

        @Override public String getId() { return id; }
        @Override public int getViewType() { return AnswerCardAdapter.VT_WEATHER; }
    }
}
