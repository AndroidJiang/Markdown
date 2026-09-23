package com.fluid.markdown.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;
import com.fluid.afm.styles.MarkdownStyles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 内层回答 Adapter（参考 egame UnifiedAnswerCardAdapter）。
 * <p>
 * 继承 ListAdapter，内部管理多种 ChatItem。
 * 外层 ChatAdapter 订阅它的 item 列表变化，扁平化展开到外层 RV。
 * <p>
 * 数据驱动（参考 egame）：数据源一次性注入全部 item，各文本段独立打字机渲染，互不等待。
 */
public class AnswerCardAdapter extends ListAdapter<ChatItem, RecyclerView.ViewHolder> {

    public static final int VT_TEXT      = 0;
    public static final int VT_HOTEL     = 1;
    public static final int VT_TRAIN     = 2;
    public static final int VT_FLIGHT    = 3;
    public static final int VT_WEATHER   = 4;
    public static final int VT_ITINERARY = 5;

    private final ElementClickEventCallback callback;
    private final List<Function<List<ChatItem>, Void>> itemsChangedObservers = new ArrayList<>();
    private OnShowNextListener showNextListener;
    private OnHeightChangedListener heightListener;

    public interface OnHeightChangedListener {
        void onHeightChanged();
    }

    /** 当前文本段打字机自然打完，可放行显示下一个 item。 */
    public interface OnShowNextListener {
        void onShowNext();
    }

    public AnswerCardAdapter(ElementClickEventCallback callback) {
        super(new DiffUtil.ItemCallback<ChatItem>() {
            @Override
            public boolean areItemsTheSame(ChatItem oldItem, ChatItem newItem) {
                return oldItem.getClass() == newItem.getClass() && oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(ChatItem oldItem, ChatItem newItem) {
                if (oldItem instanceof ChatItem.TextItem && newItem instanceof ChatItem.TextItem) {
                    ChatItem.TextItem o = (ChatItem.TextItem) oldItem;
                    ChatItem.TextItem n = (ChatItem.TextItem) newItem;
                    return o.text.equals(n.text) && o.isStreaming == n.isStreaming;
                }
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public Object getChangePayload(ChatItem oldItem, ChatItem newItem) {
                if (oldItem instanceof ChatItem.TextItem && newItem instanceof ChatItem.TextItem) {
                    return "PAYLOAD_TEXT_UPDATE";
                }
                return null;
            }
        });
        this.callback = callback;
    }

    /**
     * 当前文本段显示完成（由外层 ChatAdapter.AnswerVH 在打字机自然打完后调用），
     * 放行显示下一个 item。
     */
    public void notifyShowNext() {
        if (showNextListener != null) {
            showNextListener.onShowNext();
        }
    }

    public void setOnShowNextListener(OnShowNextListener listener) {
        this.showNextListener = listener;
    }

    public void setOnHeightChangedListener(OnHeightChangedListener listener) {
        this.heightListener = listener;
    }

    public void addItemsChangedObserver(Function<List<ChatItem>, Void> observer) {
        itemsChangedObservers.add(observer);
    }

    public void removeItemsChangedObserver(Function<List<ChatItem>, Void> observer) {
        itemsChangedObservers.remove(observer);
    }

    public List<ChatItem> getItems() {
        return getCurrentList();
    }

    /**
     * 提交新列表并通知观察者。
     */
    public void submitItems(List<ChatItem> newItems) {
        submitList(new ArrayList<>(newItems), () -> {
            for (Function<List<ChatItem>, Void> observer : itemsChangedObservers) {
                observer.apply(getCurrentList());
            }
        });
    }

    /**
     * 通知高度变化（由 TextVH 调用）。
     */
    public void notifyTextContentHeightChanged() {
        if (heightListener != null) heightListener.onHeightChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VT_TEXT) {
            PrinterMarkDownTextView tv = new PrinterMarkDownTextView(parent.getContext());
            MarkdownStyles styles = MarkdownStyles.getDefaultStyles();
            tv.init(styles, callback);
            tv.setPrintParams(25, 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            return new TextVH(tv);
        }

        LinearLayout container = new LinearLayout(parent.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new CardVH(container);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = getItem(position);
        if (holder instanceof TextVH) {
            ((TextVH) holder).bind((ChatItem.TextItem) item, this);
        } else if (holder instanceof CardVH) {
            ((CardVH) holder).bind(item);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        ChatItem item = getItem(position);
        if (holder instanceof TextVH) {
            ((TextVH) holder).bindIncremental((ChatItem.TextItem) item, this);
        }
    }

    // ==================== Text ViewHolder ====================

    static class TextVH extends RecyclerView.ViewHolder {
        final PrinterMarkDownTextView textView;
        private int lastNotifiedHeight = 0;

        TextVH(PrinterMarkDownTextView tv) {
            super(tv);
            this.textView = tv;
        }

        void bind(ChatItem.TextItem item, AnswerCardAdapter adapter) {
            if (item.printData != null && !item.isStreaming) {
                textView.setPrintData(item.printData);
                textView.restore(item.printData);
                return;
            }

            if (!item.isStreaming) {
                textView.setMarkdownText(item.text);
                textView.post(() -> textView.setMinHeight(0));
                return;
            }

            // 流式打印
            if (item.printData == null) {
                item.printData = new PrinterMarkDownTextView.MarkDownPrintData();
            }
            textView.setPrintData(item.printData);
            textView.startPrinting(item.text);
            textView.setPrintingEventListener(new PrinterMarkDownTextView.PrintingEventListener() {
                @Override public void onPrintStart() {}
                @Override public void onPrintStop(boolean printAll) {
                    item.isStreaming = false;
                    if (adapter.heightListener != null) adapter.heightListener.onHeightChanged();
                }
                @Override public void onPrintPaused(int index) {}
                @Override public void onPrintResumed() {}
            });

            lastNotifiedHeight = 0;
            textView.setSizeChangedListener((width, height) -> {
                if (height <= lastNotifiedHeight) return;
                lastNotifiedHeight = height;
                adapter.notifyTextContentHeightChanged();
            });
        }

        void bindIncremental(ChatItem.TextItem item, AnswerCardAdapter adapter) {
            if (item.isStreaming && textView.isStarted()) {
                textView.appendPrinting(item.text, false);
            } else {
                bind(item, adapter);
            }
        }
    }

    // ==================== Card ViewHolder ====================

    static class CardVH extends RecyclerView.ViewHolder {
        final LinearLayout container;

        CardVH(LinearLayout container) {
            super(container);
            this.container = container;
        }

        void bind(ChatItem item) {
            container.removeAllViews();
            if (item instanceof ChatItem.HotelCardItem) {
                HotelCardView v = new HotelCardView(container.getContext());
                v.bind(HotelCardData.fromJson(((ChatItem.HotelCardItem) item).json));
                container.addView(v);
            } else if (item instanceof ChatItem.TrainCardItem) {
                TrainCardView v = new TrainCardView(container.getContext());
                v.bind(TrainCardData.fromJson(((ChatItem.TrainCardItem) item).json));
                container.addView(v);
            } else if (item instanceof ChatItem.FlightCardItem) {
                FlightCardView v = new FlightCardView(container.getContext());
                v.bind(FlightCardData.fromJson(((ChatItem.FlightCardItem) item).json));
                container.addView(v);
            } else if (item instanceof ChatItem.WeatherCardItem) {
                WeatherCardView v = new WeatherCardView(container.getContext());
                v.bind(WeatherCardData.fromJson(((ChatItem.WeatherCardItem) item).json));
                container.addView(v);
            }
        }
    }
}
