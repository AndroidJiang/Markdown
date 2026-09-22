package com.fluid.markdown.chat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;
import com.fluid.afm.styles.MarkdownStyles;

import java.util.ArrayList;
import java.util.List;

/**
 * 内层回答卡片 Adapter（参考 egame_cloud_phone UnifiedAnswerCardAdapter）。
 * <p>
 * 每条 AI 回复对应一个 AnswerCardAdapter，内部管理多种 ViewType：
 * - VT_TEXT：Markdown 文本（流式打印）
 * - VT_HOTEL：酒店卡片
 * - VT_TRAIN：火车票卡片
 * - VT_FLIGHT：机票卡片
 * - VT_WEATHER：天气卡片
 * <p>
 * 流式串联：文本 item 打印完成后 onPrintStop → onStreamComplete 回调通知外层推进下一段。
 */
public class AnswerCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VT_TEXT    = 0;
    public static final int VT_HOTEL   = 1;
    public static final int VT_TRAIN   = 2;
    public static final int VT_FLIGHT  = 3;
    public static final int VT_WEATHER = 4;

    private static class AnswerItem {
        int viewType;
        String content;
        boolean isStreaming;
        boolean streamDone;
        PrinterMarkDownTextView.MarkDownPrintData printData;
    }

    private final List<AnswerItem> items = new ArrayList<>();
    private final ElementClickEventCallback callback;
    private OnHeightChangedListener heightListener;
    private OnStreamCompleteListener streamCompleteListener;

    public interface OnHeightChangedListener {
        void onHeightChanged();
    }

    public interface OnStreamCompleteListener {
        /**
         * 当前文本段流式打印完成。
         */
        void onStreamComplete();
    }

    public AnswerCardAdapter(ElementClickEventCallback callback) {
        this.callback = callback;
    }

    public void setOnHeightChangedListener(OnHeightChangedListener listener) {
        this.heightListener = listener;
    }

    public void setOnStreamCompleteListener(OnStreamCompleteListener listener) {
        this.streamCompleteListener = listener;
    }

    /**
     * 添加一个文本段（流式）。
     */
    public void addTextItem(String text) {
        AnswerItem item = new AnswerItem();
        item.viewType = VT_TEXT;
        item.content = text;
        item.isStreaming = true;
        item.streamDone = false;
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    /**
     * 添加一个卡片段（直接显示）。
     */
    public void addCardItem(int cardType, String json) {
        AnswerItem item = new AnswerItem();
        item.viewType = cardType;
        item.content = json;
        item.isStreaming = false;
        item.streamDone = true;
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VT_TEXT) {
            PrinterMarkDownTextView tv = new PrinterMarkDownTextView(parent.getContext());
            MarkdownStyles styles = MarkdownStyles.getDefaultStyles();
            tv.init(styles, callback);
            tv.setPrintParams(20, 2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            return new TextVH(tv);
        }

        LinearLayout container = new LinearLayout(parent.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        container.setLayoutParams(lp);
        return new CardVH(container);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AnswerItem item = items.get(position);

        if (holder instanceof TextVH) {
            ((TextVH) holder).bind(item, position, this);
        } else if (holder instanceof CardVH) {
            ((CardVH) holder).bind(item.viewType, item.content);
            if (heightListener != null) {
                holder.itemView.post(() -> {
                    if (heightListener != null) heightListener.onHeightChanged();
                });
            }
        }
    }

    // ==================== Text ViewHolder ====================

    static class TextVH extends RecyclerView.ViewHolder {
        final PrinterMarkDownTextView textView;

        TextVH(PrinterMarkDownTextView tv) {
            super(tv);
            this.textView = tv;
        }

        void bind(AnswerItem item, int position, AnswerCardAdapter adapter) {
            if (!item.isStreaming || item.streamDone) {
                // 复用或已完成：直接渲染
                if (item.printData != null) {
                    textView.setPrintData(item.printData);
                    textView.restore(item.printData);
                } else {
                    textView.setMarkdownText(item.content);
                    textView.post(() -> textView.setMinHeight(0));
                }
                return;
            }

            // 流式打印
            if (item.printData == null) {
                item.printData = new PrinterMarkDownTextView.MarkDownPrintData();
                textView.setPrintData(item.printData);
            } else {
                textView.setPrintData(item.printData);
            }

            textView.startPrinting(item.content);
            textView.setPrintingEventListener(new PrinterMarkDownTextView.PrintingEventListener() {
                @Override public void onPrintStart() {}
                @Override public void onPrintStop(boolean printAll) {
                    item.streamDone = true;
                    // 通知外层 adapter 推进下一段
                    if (adapter.streamCompleteListener != null) {
                        adapter.streamCompleteListener.onStreamComplete();
                    }
                    if (adapter.heightListener != null) {
                        adapter.heightListener.onHeightChanged();
                    }
                }
                @Override public void onPrintPaused(int index) {}
                @Override public void onPrintResumed() {}
            });

            textView.setSizeChangedListener((width, height) -> {
                if (adapter.heightListener != null) adapter.heightListener.onHeightChanged();
            });
        }
    }

    // ==================== Card ViewHolder ====================

    static class CardVH extends RecyclerView.ViewHolder {
        final LinearLayout container;

        CardVH(LinearLayout container) {
            super(container);
            this.container = container;
        }

        void bind(int viewType, String json) {
            container.removeAllViews();
            switch (viewType) {
                case VT_HOTEL: {
                    HotelCardView v = new HotelCardView(container.getContext());
                    v.bind(HotelCardData.fromJson(json));
                    container.addView(v);
                    break;
                }
                case VT_TRAIN: {
                    TrainCardView v = new TrainCardView(container.getContext());
                    v.bind(TrainCardData.fromJson(json));
                    container.addView(v);
                    break;
                }
                case VT_FLIGHT: {
                    FlightCardView v = new FlightCardView(container.getContext());
                    v.bind(FlightCardData.fromJson(json));
                    container.addView(v);
                    break;
                }
                case VT_WEATHER: {
                    WeatherCardView v = new WeatherCardView(container.getContext());
                    v.bind(WeatherCardData.fromJson(json));
                    container.addView(v);
                    break;
                }
            }
        }
    }
}
