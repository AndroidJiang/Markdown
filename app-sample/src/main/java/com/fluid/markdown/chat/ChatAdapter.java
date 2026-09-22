package com.fluid.markdown.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.markdown.ElementClickEventCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 外层聊天列表 Adapter（参考 egame_cloud_phone ChatAdapter）。
 * <p>
 * 只有两种行：
 * - VT_USER：用户消息气泡
 * - VT_AI：AI 回答行，内部包含一个 RecyclerView + AnswerCardAdapter
 *   答卡内部按文本/酒店/火车/机票/天气多类型渲染，流式串联。
 * <p>
 * 流式串联核心：ChatAdapter 逐段向内层 adapter 添加内容，
 * 文本段由 AnswerCardAdapter 在 onPrintStop 回调中通知 ChatAdapter 推进下一段，
 * 卡片段添加后立即推进下一段。不使用任何轮询或延迟猜测。
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VT_USER = 0;
    public static final int VT_AI   = 1;

    private final List<ChatEntry> entries = new ArrayList<>();
    private final ElementClickEventCallback callback;
    private OnAIHeightChangedListener heightListener;

    public interface OnAIHeightChangedListener {
        void onAIHeightChanged();
    }

    public ChatAdapter(ElementClickEventCallback callback) {
        this.callback = callback;
    }

    public void setOnAIHeightChangedListener(OnAIHeightChangedListener listener) {
        this.heightListener = listener;
    }

    /**
     * 外层 item 数据
     */
    private static class ChatEntry {
        final int viewType;
        String userText;
        // AI 回复
        String fullResponse;
        List<ChatMessage.Segment> segments;
        int currentSegmentIndex;
        AnswerCardAdapter innerAdapter;

        ChatEntry(int viewType) {
            this.viewType = viewType;
        }
    }

    /**
     * 添加用户消息。
     */
    public void addUserMessage(String text) {
        ChatEntry entry = new ChatEntry(VT_USER);
        entry.userText = text;
        entries.add(entry);
        notifyItemInserted(entries.size() - 1);
    }

    /**
     * 添加 AI 回复（自动拆分为 segments，逐段流式渲染）。
     */
    public void addAIResponse(String fullResponse) {
        ChatEntry entry = new ChatEntry(VT_AI);
        entry.fullResponse = fullResponse;
        entry.segments = ChatMessage.parseSegments(fullResponse);
        entry.currentSegmentIndex = 0;

        // 创建内层 adapter
        entry.innerAdapter = new AnswerCardAdapter(callback);
        entry.innerAdapter.setOnHeightChangedListener(() -> {
            if (heightListener != null) heightListener.onAIHeightChanged();
        });

        // 设置内层 adapter 的流式完成回调：当一段文本打印完成时，推进下一段
        final int entryIndex = entries.size();
        entry.innerAdapter.setOnStreamCompleteListener(() -> streamNextSegment(entryIndex));

        entries.add(entry);
        notifyItemInserted(entries.size() - 1);

        // 开始第一段
        streamNextSegment(entries.size() - 1);
    }

    /**
     * 逐段向内层 adapter 添加内容。
     * - 文本段：addTextItem 后等待 onPrintStop 回调再推进下一段
     * - 卡片段：addCardItem 后立即推进下一段
     */
    private void streamNextSegment(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;
        ChatEntry entry = entries.get(entryIndex);
        if (entry.currentSegmentIndex >= entry.segments.size()) return;

        ChatMessage.Segment seg = entry.segments.get(entry.currentSegmentIndex);

        if (seg.type == ChatMessage.Segment.TYPE_TEXT) {
            // 文本段：添加到内层 adapter，流式打印完成后由 onStreamComplete 回调推进
            if (seg.content == null || seg.content.isEmpty()) {
                // 空文本段直接跳过
                entry.currentSegmentIndex++;
                streamNextSegment(entryIndex);
                return;
            }
            entry.innerAdapter.addTextItem(seg.content);
            entry.currentSegmentIndex++;
            // 不在这里推进——等 AnswerCardAdapter.onPrintStop → onStreamComplete 回调
        } else {
            // 卡片段：直接添加到内层 adapter，立即推进下一段
            int cardType;
            switch (seg.type) {
                case ChatMessage.Segment.TYPE_HOTEL_CARD:   cardType = AnswerCardAdapter.VT_HOTEL; break;
                case ChatMessage.Segment.TYPE_TRAIN_CARD:   cardType = AnswerCardAdapter.VT_TRAIN; break;
                case ChatMessage.Segment.TYPE_FLIGHT_CARD:  cardType = AnswerCardAdapter.VT_FLIGHT; break;
                case ChatMessage.Segment.TYPE_WEATHER_CARD:  cardType = AnswerCardAdapter.VT_WEATHER; break;
                default: cardType = AnswerCardAdapter.VT_TEXT; break;
            }
            entry.innerAdapter.addCardItem(cardType, seg.content);
            entry.currentSegmentIndex++;
            // 卡片不需要流式，等布局完成后立即推进下一段
            entry.innerAdapter.notifyItemChanged(entry.innerAdapter.getItemCount() - 1);
            // 用 post 等卡片布局完成再继续
            final int ei = entryIndex;
            // 通过 ViewTreeObserver 等待布局完成
            if (heightListener != null) heightListener.onAIHeightChanged();
            // 直接推进（卡片是同步添加的，不需要等打印）
            // 但要等 RecyclerView 布局完成，用 post
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> streamNextSegment(ei));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return entries.get(position).viewType;
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        String pkg = parent.getContext().getPackageName();

        if (viewType == VT_USER) {
            View view = inflater.inflate(
                    parent.getContext().getResources().getIdentifier("item_chat_user", "layout", pkg),
                    parent, false);
            return new UserVH(view);
        } else {
            View view = inflater.inflate(
                    parent.getContext().getResources().getIdentifier("item_chat_ai", "layout", pkg),
                    parent, false);
            return new AIVH(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatEntry entry = entries.get(position);
        if (holder instanceof UserVH) {
            ((UserVH) holder).bind(entry.userText);
        } else if (holder instanceof AIVH) {
            ((AIVH) holder).bind(entry);
        }
    }

    // ==================== User ViewHolder ====================

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView textView;

        UserVH(@NonNull View itemView) {
            super(itemView);
            int id = itemView.getContext().getResources().getIdentifier(
                    "tv_user_message", "id", itemView.getContext().getPackageName());
            textView = itemView.findViewById(id);
        }

        void bind(String text) {
            textView.setText(text);
        }
    }

    // ==================== AI ViewHolder（含内层 RecyclerView） ====================

    static class AIVH extends RecyclerView.ViewHolder {
        final RecyclerView innerRV;

        AIVH(@NonNull View itemView) {
            super(itemView);
            int id = itemView.getContext().getResources().getIdentifier(
                    "inner_rv", "id", itemView.getContext().getPackageName());
            innerRV = itemView.findViewById(id);
        }

        void bind(ChatEntry entry) {
            if (innerRV.getAdapter() != entry.innerAdapter) {
                innerRV.setLayoutManager(new LinearLayoutManager(itemView.getContext(),
                        LinearLayoutManager.VERTICAL, false));
                innerRV.setNestedScrollingEnabled(false);
                innerRV.setAdapter(entry.innerAdapter);
            }
        }
    }
}
