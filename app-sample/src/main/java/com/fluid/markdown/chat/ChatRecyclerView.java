package com.fluid.markdown.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 自动滚动 RecyclerView（参考千问 HybridFeedRecyclerView）。
 * <p>
 * 核心机制（千问方案）：
 * 1. PrinterMarkDownTextView 流式打印时高度每帧都在变，
 *    onSizeChanged 回调直接触发 requestScrollToBottom，
 *    持续不断地跟滚，视觉上就是一直在平滑滚动
 * 2. 每次高度变化用 ValueAnimator + scrollBy 做 ~200ms 减速动画，
 *    新请求取消旧动画、从当前位移继续，动画重叠 = 连续平滑
 * 3. 高度去抖：computeVerticalScrollRange 没变化时不滚
 * 4. post 合并防抖：16ms 内多次请求合并为一次
 * 5. 滚动状态机：AUTO_SCROLL / MANUAL_SCROLL / MANUAL_SCROLL_PAUSED / NO_SCROLL
 */
public class ChatRecyclerView extends RecyclerView {

    private ChatScrollState autoScrollState = ChatScrollState.AUTO_SCROLL;
    private int lastRange = 0;
    private boolean isScrollPending = false;
    private final Handler scrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable scrollRunnable = this::doScrollToBottom;
    private ValueAnimator scrollAnimator;
    private int currentScrollDelta = 0;

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
     * 每帧高度变化都会调用此方法，形成持续跟滚效果。
     */
    public void requestScrollToBottom() {
        if (autoScrollState != ChatScrollState.AUTO_SCROLL) return;
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;

        // 高度去抖
        int range = computeVerticalScrollRange();
        if (range != lastRange) {
            lastRange = range;
            if (!isScrollPending) {
                isScrollPending = true;
                scrollHandler.postDelayed(scrollRunnable, 16);
            }
            return;
        }

        // 高度未变也可能是"新 item 刚 notify 尚未布局"（notifyItemInserted 的布局在下一帧）：
        // 此刻 range 仍是旧值，直接 return 会漏掉新卡片/新段落插入后的滚动。
        // 安排一次延迟兜底，16ms 后布局完成，doScrollToBottom 会按真实距离滚动。
        if (isScrollPending) return;
        isScrollPending = true;
        scrollHandler.postDelayed(scrollRunnable, 16);
    }

    /**
     * 执行平滑滚动到底部。
     * <p>
     * 用 ValueAnimator + scrollBy 直接控制位移和时长。
     * 新请求取消旧动画，从当前位移继续滚动，动画重叠 = 连续平滑。
     */
    private void doScrollToBottom() {
        isScrollPending = false;
        if (autoScrollState != ChatScrollState.AUTO_SCROLL) return;
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;

        // 计算剩余距离
        int range = computeVerticalScrollRange();
        int extent = computeVerticalScrollExtent();
        int offset = computeVerticalScrollOffset();
        int remainingPx = Math.max(0, range - extent - offset);
        if (remainingPx <= 0) return;

        // 取消旧动画，记录已滚动位移
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        currentScrollDelta = 0;

        // 时长：~200ms 减速动画
        // 千问 SmoothScrollerWithOffset: min(250f/remainingPx, 7.5f) ms/px
        // 总时间 ≈ min(250, 7.5 * remainingPx)
        int duration = (int) Math.min(remainingPx * 7.5f, 200);
        if (duration < 50) duration = 50;

        scrollAnimator = ValueAnimator.ofInt(0, remainingPx);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new DecelerateInterpolator());

        scrollAnimator.addUpdateListener(animation -> {
            int currentDelta = (int) animation.getAnimatedValue();
            int step = currentDelta - currentScrollDelta;
            if (step > 0) {
                scrollBy(0, step);
                currentScrollDelta = currentDelta;
            }
        });
        scrollAnimator.start();
    }

    /**
     * 强制滚动到底部（发送消息时用）。
     */
    public void forceScrollToBottom() {
        autoScrollState = ChatScrollState.AUTO_SCROLL;
        scrollHandler.removeCallbacks(scrollRunnable);
        isScrollPending = false;
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            scrollAnimator.cancel();
        }
        currentScrollDelta = 0;
        stopScroll();
        if (getAdapter() == null || getAdapter().getItemCount() == 0) return;

        int range = computeVerticalScrollRange();
        int extent = computeVerticalScrollExtent();
        int offset = computeVerticalScrollOffset();
        int remainingPx = Math.max(0, range - extent - offset);
        if (remainingPx <= 0) return;

        int duration = (int) Math.min(remainingPx * 7.5f, 200);
        if (duration < 50) duration = 50;

        scrollAnimator = ValueAnimator.ofInt(0, remainingPx);
        scrollAnimator.setDuration(duration);
        scrollAnimator.setInterpolator(new DecelerateInterpolator());
        scrollAnimator.addUpdateListener(animation -> {
            int currentDelta = (int) animation.getAnimatedValue();
            int step = currentDelta - currentScrollDelta;
            if (step > 0) {
                scrollBy(0, step);
                currentScrollDelta = currentDelta;
            }
        });
        scrollAnimator.start();
    }

    public void pauseAutoScroll() {
        autoScrollState = ChatScrollState.NO_SCROLL;
        scrollHandler.removeCallbacks(scrollRunnable);
        isScrollPending = false;
    }

    public void resumeAutoScroll() {
        autoScrollState = ChatScrollState.AUTO_SCROLL;
        lastRange = 0;
        requestScrollToBottom();
    }
}
