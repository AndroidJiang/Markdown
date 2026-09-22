package com.fluid.markdown.chat;

/**
 * 聊天列表自动滚动状态机（参考千问 HybridFeedScrollState）。
 * <p>
 * 四个状态：
 * - AUTO_SCROLL：流式输出中自动跟滚
 * - MANUAL_SCROLL：用户手动上滑，暂停自动滚动
 * - MANUAL_SCROLL_PAUSED：手动滚动后暂停，等待用户回到底部
 * - NO_SCROLL：不滚动（如卡片展开时）
 */
public enum ChatScrollState {
    AUTO_SCROLL,
    MANUAL_SCROLL,
    MANUAL_SCROLL_PAUSED,
    NO_SCROLL
}
