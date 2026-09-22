package com.fluid.markdown.chat;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;
import com.fluid.afm.styles.MarkdownStyles;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天列表 Adapter（参考千问 HybridFeedListAdapter）。
 * <p>
 * 两种 ViewHolder：
 * - UserViewHolder：用户消息（简单文本气泡）
 * - ChatViewHolder：AI 回复（流式 Markdown + 卡片）
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ElementClickEventCallback callback;

    public ChatAdapter(ElementClickEventCallback callback) {
        this.callback = callback;
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).type;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    getResourceId(parent, "item_chat_user"), parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    getResourceId(parent, "item_chat_ai"), parent, false);
            return new ChatViewHolder(view, callback);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(msg);
        } else if (holder instanceof ChatViewHolder) {
            ((ChatViewHolder) holder).bind(msg);
        }
    }

    private int getResourceId(ViewGroup parent, String name) {
        return parent.getContext().getResources().getIdentifier(
                name, "layout", parent.getContext().getPackageName());
    }

    // ==================== User ViewHolder ====================

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            int id = itemView.getContext().getResources().getIdentifier(
                    "tv_user_message", "id", itemView.getContext().getPackageName());
            textView = itemView.findViewById(id);
        }

        void bind(ChatMessage msg) {
            textView.setText(msg.userText);
        }
    }

    // ==================== AI Chat ViewHolder ====================

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout contentContainer;
        private final ElementClickEventCallback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private ChatMessage currentMessage;

        ChatViewHolder(@NonNull View itemView, ElementClickEventCallback callback) {
            super(itemView);
            this.callback = callback;
            int id = itemView.getContext().getResources().getIdentifier(
                    "content_container", "id", itemView.getContext().getPackageName());
            contentContainer = itemView.findViewById(id);
        }

        void bind(ChatMessage msg) {
            currentMessage = msg;
            contentContainer.removeAllViews();

            if (!msg.isStreaming || msg.segments == null || msg.segments.isEmpty()) {
                // 非流式或已完成：直接渲染所有段落
                for (ChatMessage.Segment seg : msg.segments) {
                    if (seg.type == ChatMessage.Segment.TYPE_TEXT) {
                        addTextView(seg.content);
                    } else {
                        addHotelCard(seg.content);
                    }
                }
                return;
            }

            // 流式渲染：从当前段落开始
            startStreaming();
        }

        private void startStreaming() {
            if (currentMessage.currentSegmentIndex >= currentMessage.segments.size()) {
                currentMessage.isStreaming = false;
                return;
            }

            ChatMessage.Segment seg = currentMessage.segments.get(currentMessage.currentSegmentIndex);

            if (seg.type == ChatMessage.Segment.TYPE_HOTEL_CARD) {
                // 卡片段落：暂停滚动 → 添加卡片 → 等布局完成 → 恢复滚动 → 继续下一段
                ChatRecyclerView rv = getChatRecyclerView();
                if (rv != null) rv.pauseAutoScroll();

                addHotelCard(seg.content);
                currentMessage.currentSegmentIndex++;

                // 等卡片布局完成后再恢复滚动并继续下一段
                itemView.post(() -> {
                    if (rv != null) rv.resumeAutoScroll();
                    startStreaming();
                });
            } else {
                // 文本段落：启动打字机
                addTextViewAndStream(seg.content);
            }
        }

        private void addTextView(String text) {
            if (text == null || text.isEmpty()) return;
            PrinterMarkDownTextView tv = createMarkdownView();
            contentContainer.addView(tv);
            tv.setMarkdownText(text);
            // PrinterMarkDownTextView.onMeasure 会 setMinHeight(newHeight) 且只增不减。
            // 复用时 setMarkdownText 第一次 onMeasure 可能高度偏大（图片/span 未就绪），
            // 后续实际高度变小但 setMinHeight 锁住不让缩 → 气泡底部出现空白。
            // post setMinHeight(0) 解除锁定，让后续 measure 能自由收缩。
            tv.post(() -> tv.setMinHeight(0));
        }

        private void addHotelCard(String json) {
            HotelCardData data = HotelCardData.fromJson(json);
            HotelCardView cardView = new HotelCardView(itemView.getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(8), 0, dp(8));
            cardView.setLayoutParams(lp);
            cardView.bind(data);
            contentContainer.addView(cardView);
        }

        private void addTextViewAndStream(String text) {
            if (text == null || text.isEmpty()) {
                currentMessage.currentSegmentIndex++;
                startStreaming();
                return;
            }

            PrinterMarkDownTextView tv = createMarkdownView();
            contentContainer.addView(tv);

            // 使用 PrinterMarkDownTextView 自带的流式打印功能
            tv.startPrinting(text);
            tv.setPrintingEventListener(new PrinterMarkDownTextView.PrintingEventListener() {
                @Override
                public void onPrintStart() {}

                @Override
                public void onPrintStop(boolean printAll) {
                    // 打印结束就推进到下一段（AntFluid 库 printAll 参数不可靠）
                    currentMessage.currentSegmentIndex++;
                    if (currentMessage.currentSegmentIndex < currentMessage.segments.size()) {
                        startStreaming();
                    } else {
                        currentMessage.isStreaming = false;
                    }
                }

                @Override
                public void onPrintPaused(int index) {}

                @Override
                public void onPrintResumed() {}
            });

            // 监听高度变化，触发自动滚动（已带防抖）
            tv.setSizeChangedListener((width, height) -> {
                ChatRecyclerView rv = getChatRecyclerView();
                if (rv != null) rv.requestScrollToBottom();
            });
        }

        private ChatRecyclerView getChatRecyclerView() {
            View parent = itemView;
            while (parent != null && !(parent.getParent() instanceof ChatRecyclerView)) {
                parent = (View) parent.getParent();
            }
            if (parent != null && parent.getParent() instanceof ChatRecyclerView) {
                return (ChatRecyclerView) parent.getParent();
            }
            return null;
        }

        private PrinterMarkDownTextView createMarkdownView() {
            PrinterMarkDownTextView tv = new PrinterMarkDownTextView(itemView.getContext());
            MarkdownStyles styles = MarkdownStyles.getDefaultStyles();
            tv.init(styles, callback);
            tv.setPrintParams(20, 2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            return tv;
        }

        private int dp(float v) {
            return (int) (v * itemView.getContext().getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
