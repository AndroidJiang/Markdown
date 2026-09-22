package com.fluid.markdown.chat;

import android.os.Handler;
import android.os.Looper;

/**
 * 流式打字机控制器（参考千问 TypewriterController）。
 * <p>
 * 核心机制：
 * 1. 逐帧推进文本进度，每帧步长 = ceil(剩余长度 / (刷新率/8))
 * 2. 通过 Handler.postDelayed 模拟帧回调（简化版，千问用 Choreographer）
 * 3. 每帧回调 Listener.onTypewriterTick(currentIndex, displayText)
 * 4. 支持暂停/恢复/停止
 * 5. 完成后回调 Listener.onTypewriterComplete()
 */
public class TypewriterController {

    private static final String TAG = "TypewriterController";
    private static final int FRAME_INTERVAL_MS = 16; // 约60fps
    private static final int BASE_STEP = 2;           // 每帧基础步长

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String fullText;
    private int currentIndex = 0;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private Listener listener;

    public interface Listener {
        /**
         * 每帧回调，传入当前截断后的文本。
         */
        void onTypewriterTick(int index, String displayText);

        /**
         * 打字机完成。
         */
        void onTypewriterComplete();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start(String text) {
        this.fullText = text;
        this.currentIndex = 0;
        this.isRunning = true;
        this.isPaused = false;
        tick();
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void pause() {
        isPaused = true;
        handler.removeCallbacksAndMessages(null);
    }

    public void resume() {
        if (!isRunning || !isPaused) return;
        isPaused = false;
        tick();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    private void tick() {
        if (!isRunning || isPaused || fullText == null) return;

        if (currentIndex >= fullText.length()) {
            // 完成
            isRunning = false;
            if (listener != null) {
                listener.onTypewriterComplete();
            }
            return;
        }

        // 计算步长：剩余长度越少步长越小，实现"减速"效果
        int remaining = fullText.length() - currentIndex;
        int step = Math.max(BASE_STEP, (int) Math.ceil(remaining / 30.0));
        // 限制最大步长，避免一次跳太多
        step = Math.min(step, 8);

        int endIndex = Math.min(currentIndex + step, fullText.length());
        currentIndex = endIndex;

        String displayText = fullText.substring(0, currentIndex);
        if (listener != null) {
            listener.onTypewriterTick(currentIndex, displayText);
        }

        if (currentIndex < fullText.length()) {
            handler.postDelayed(this::tick, FRAME_INTERVAL_MS);
        } else {
            isRunning = false;
            if (listener != null) {
                listener.onTypewriterComplete();
            }
        }
    }
}
