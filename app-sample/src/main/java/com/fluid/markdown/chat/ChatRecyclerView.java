package com.fluid.markdown.chat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 自动滚动 RecyclerView（参考千问 HybridFeedRecyclerView）。
 * <p>
 * 核心机制（千问方案）：
 * 1. 流式渲染时用 scrollToPosition 直接定位（非动画），不用 smoothScrollToPosition
 *    —— smoothScroll 是动画滚动，流式高频触发会互相打断造成抖动
 * 2. 高度去抖：computeVerticalScrollRange 没变化时不滚
 * 3. post 合并防抖：短时间多次请求合并为一次
 * 4. 滚动状态机：AUTO_SCROLL / MANUAL_SCROLL / MANUAL_SCROLL_PAUSED / NO_SCROLL
 * 5. 用户手动上滑时暂停自动滚动，回到底部后恢复
 * 6. 卡片出现时暂停滚动，布局完成后恢复
 */
public class ChatRecyclerView extends RecyclerView {

    private static final String TAG = "ChatRecyclerView";

    private ChatScrollState autoScrollState = ChatScrollState.AUTO_SCROLL;
    private int lastRange = 0;
    private boolean isScrollPending = false;
    private final Handler scrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable scrollRunnable = this::doScrollToBottom;

    private final OnScrollListener autoScrollListener = new OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
            if (newState == SCROLL_STATE_DRAGGING) {
                if (autoScrollState == ChatScrollState.AUTO_SCROLL) {
                    autoScrollState = ChatScrollState.MANUAL_SCROLL;
                }
            } else if (newState == SCROLL_STATE_IDLE) {
                if (isAtBottom() && autoScrollState != ChatScrollState.NO_SCROLL) {
                    autoScrollState = ChatScrollState.AUTO_SCROLL;
                } else if (!isAtBottom() && autoScrollState == ChatScrollState.MANUAL_SCROLL) {
                    autoScrollState = ChatScrollState.MANUAL_SCROLL_PAUSED;
                }
            }
        }
    };

    public ChatRecyclerView(Context context) {
        super(context);
        init();
    }

    public ChatRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChatRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        addOnScrollListener(autoScrollListener);
    }

    public void setAutoScrollState(ChatScrollState state) {
        this.autoScrollState = state;
    }

    public ChatScrollState getAutoScrollState() {
        return autoScrollState;
    }

    /**
     * 判断当前是否滚动到了底部。
     */
    public boolean isAtBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) getLayoutManager();
        if (lm == null || getAdapter() == null || getAdapter().getItemCount() == 0) {
            return true;
        }
        int lastVisible = lm.findLastCompletelyVisibleItemPosition();
        int lastItem = getAdapter().getItemCount() - 1;
        return lastVisible >= lastItem - 1;
    }

    /**
     * 请求滚动到底部（流式渲染时调用）。
     * <p>
     * 关键：用 scrollToPosition（直接定位）而非 smoothScrollToPosition（动画）。
     * 流式渲染时高度每帧都在变，动画滚动会被反复打断导致抖动。
     * 直接定位配合高度去抖 + post 合并，效果等同千问的 scrollToPositionWithOffset。
     */
    public void requestScrollToBottom() {
        if (autoScrollState != ChatScrollState.AUTO_SCROLL) return;
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;

        // 高度去抖
        int range = computeVerticalScrollRange();
        if (range == lastRange) return;
        lastRange = range;

        // post 合并防抖
        if (isScrollPending) return;
        isScrollPending = true;
        scrollHandler.postDelayed(scrollRunnable, 16);
    }

    private void doScrollToBottom() {
        isScrollPending = false;
        if (autoScrollState != ChatScrollState.AUTO_SCROLL) return;
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;

        int lastPos = getAdapter().getItemCount() - 1;
        // scrollToPosition + stackFromEnd=true → LayoutManager 自动将
        // 最后一条底部对齐 RV 底部，无需手动算 offset
        // 关键：这是直接定位，不是动画，不会互相打断
        scrollToPosition(lastPos);
    }

    /**
     * 强制滚动到底部（发送消息时用）。
     * 重置状态为 AUTO_SCROLL，立即滚底。
     */
    public void forceScrollToBottom() {
        autoScrollState = ChatScrollState.AUTO_SCROLL;
        scrollHandler.removeCallbacks(scrollRunnable);
        isScrollPending = false;
        stopScroll();
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;
        int lastPos = getAdapter().getItemCount() - 1;
        scrollToPosition(lastPos);
    }

    /**
     * 暂停自动滚动（卡片出现时调用）。
     */
    public void pauseAutoScroll() {
        autoScrollState = ChatScrollState.NO_SCROLL;
        scrollHandler.removeCallbacks(scrollRunnable);
        isScrollPending = false;
    }

    /**
     * 恢复自动滚动，并重置高度缓存以触发一次滚动。
     */
    public void resumeAutoScroll() {
        autoScrollState = ChatScrollState.AUTO_SCROLL;
        lastRange = 0;
        requestScrollToBottom();
    }
}
