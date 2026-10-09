package com.fluid.afm.markdown.widget;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.IMarkdownLayer;
import com.fluid.afm.icon.LoadIconUtil;
import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.MarkdownParser;
import com.fluid.afm.markdown.MarkdownParserFactory;
import com.fluid.afm.markdown.model.EventModel;
import com.fluid.afm.markdown.span.OpacitySpan;
import com.fluid.afm.span.IClickableSpan;
import com.fluid.afm.styles.MarkdownStyles;
import com.fluid.afm.utils.MDLogger;
import com.fluid.afm.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.utils.SpanUtils;

public class PrinterMarkDownTextView extends AppCompatTextView implements IMarkdownLayer, DefaultLifecycleObserver, TextWatcher {
    private static final String TAG = "PrinterMarkDownTextView";
    public static final String END_MESSAGE = "(stopped）";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final int GRADIANT_COUNT = 10;
    protected MarkdownParser mMarkdownParser;
    private MarkdownStyles mMarkdownStyles;
    private ElementClickEventCallback mElementClickEventCallback;
    protected String mOriginText;
    private int mExposureNodeCount;
    private boolean initialedScrollerWatcher = false;
    private IClickableSpan[] mClickableSpans;
    private List<EventModel> mEventModels = new ArrayList<>();

    private int mInterval = 25;
    private int mChunkSize = 1;
    private SizeChangedListener mSizeChangedListener;
    private PrintTickListener mPrintTickListener;
    private PrintingEventListener mPrintingEventListener;
    private boolean isPrinting;
    private boolean isStopByUser;
    private int mCurrentPrintIndex;
    private SpannableStringBuilder mParsedContentText;
    private final Runnable mPrintTask = () -> printing(mCurrentPrintIndex, mChunkSize);
    private String mEndMessage = END_MESSAGE;
    private boolean isDestroyed;
    private OpacitySpan[] mGradiantSpans;
    private int mHeight;
    private int maxWidth = 0;
    private boolean isStarted;
    private MarkDownPrintData mPrintData;

    // ===== 表格横向滚动 =====
    private static final String TABLE_SCROLL_TAG = "TABLE_SCROLL_DBG";
    /** 千问同款手势仲裁比例：|dy|×2 > |dx| 时视为纵向（列表优先），否则横向可接管表格滚动 */
    private static final float VERTICAL_SCALE_RATIO = 2.0F;
    /** tableIndex -> 横向滚动偏移（像素），随 TextView 生命周期持有 */
    private final SparseArray<Integer> mTableScrollXs = new SparseArray<>();
    private float mTableDownX;
    private float mTableDownY;
    private float mDownRawX;
    private float mDownRawY;
    private boolean mDownOnScrollableTable = false;
    private boolean mTableHorizontalScrolling = false;
    private TableRowSpan mActiveTableSpan;
    private int mTableScrollStartX;
    private int mTouchSlop;

    // ===== 表格横向惯性（fling，效果接近横向滚动列表） =====
    private VelocityTracker mFlingVelocityTracker;
    private OverScroller mTableFlingScroller;
    private boolean mTableFlinging = false;
    private int mFlingTableIndex = -1;
    private int mFlingRange = 0;

    public PrinterMarkDownTextView(Context context) {
        this(context, null);
    }

    public PrinterMarkDownTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrinterMarkDownTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false);
        }
        if (context instanceof LifecycleOwner) {
            ((LifecycleOwner) context).getLifecycle().addObserver(this);
        } else if (context instanceof ContextWrapper) {
            Context realContext = ((ContextWrapper) context).getBaseContext();
            if (realContext instanceof LifecycleOwner) {
                ((LifecycleOwner) realContext).getLifecycle().addObserver(this);
            }
        }

        setHighlightColor(Color.TRANSPARENT);
        addTextChangedListener(this);
        setOnLongClickListener(v -> true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTableFlingScroller = new OverScroller(context, new DecelerateInterpolator());
    }

    public void init(@NonNull MarkdownStyles styles, ElementClickEventCallback callback) {
        mMarkdownStyles = styles;
        mElementClickEventCallback = callback;
        mMarkdownParser = MarkdownParserFactory.create(getContext(), this, styles, callback);
    }

    public void setPrintParams(int interval, int chunkSize) {
        if (mInterval > 0) {
            mInterval = interval;
        }
        if(chunkSize > 0) {
            mChunkSize = chunkSize;
        }
    }

    public void handleExposureSpm() {
        handleSpm();
    }

    public void setMarkdownText(@NonNull String markdown) {
        if (mMarkdownParser == null) {
            MDLogger.e(TAG, "PrinterMarkDownTextView is not initialized. Please call init() first.");
            return;
        }
        mOriginText = markdown;
        setMinHeight(0);
        mTableScrollXs.clear();
        mMarkdownParser.getMarkwon().setMarkdown(this, markdown);
    }

    public void startPrinting(String content) {
        startPrinting(content, 0);
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void startPrinting(String content, int startIndex) {
        if (mMarkdownParser == null) {
            MDLogger.e(TAG, "PrinterMarkDownTextView is not initialized. Please call init() first.");
            return;
        }
        if (isStarted) {
            MDLogger.e(TAG, "appendPrinting printing has started!");
            return;
        }
        setMinHeight(0);
        mCurrentPrintIndex = 0;
        isPrinting = true;
        isStopByUser = false;
        mOriginText = content;
        isStarted = true;
        mTableScrollXs.clear();
        if (startIndex <= 0) {
            startIndex = 0;
        }

        mMarkdownParser.setPrintingState(true);
        mParsedContentText = new SpannableStringBuilder(mMarkdownParser.getMarkwon().toMarkdown(mOriginText));
        printing(startIndex, mChunkSize);
        if (mPrintingEventListener != null) {
            mPrintingEventListener.onPrintStart();
        }
        if (mPrintData != null) {
            mPrintData.isStopByUser = false;
            mPrintData.isPrinting = isPrinting;
            mPrintData.originalText = mOriginText;
            mPrintData.parsedMarkdownText = mParsedContentText;
            mPrintData.interval = mInterval;
            mPrintData.chunkSize = mChunkSize;
        }

    }
    public void appendPrinting(String content) {
        appendPrinting(content, true);
    }

    public void appendPrinting(String content, boolean append) {
        if (!isStarted) {
            MDLogger.e(TAG, "appendPrinting printing has not started!");
            return;
        }
        if (append) {
            mOriginText += content;
        } else {
            mOriginText = content;
        }

        mParsedContentText = new SpannableStringBuilder(mMarkdownParser.getMarkwon().toMarkdown(mOriginText));
        isStopByUser = false;
        isPrinting = true;
        printing(mCurrentPrintIndex, mChunkSize);
        if (mPrintData != null) {
            mPrintData.isPrinting = isPrinting;
            mPrintData.originalText = mOriginText;
            mPrintData.parsedMarkdownText = mParsedContentText;
        }
    }
    public void setPrintData(MarkDownPrintData printData) {
        mPrintData = printData;
    }

    public MarkDownPrintData getPrintData() {
        return mPrintData;
    }

    public void stopPrinting(String endMessage) {
        if (!isStarted) {
            MDLogger.e(TAG, "appendPrinting printing has not started!");
            return;
        }
        isStarted = false;
        if (!isPrinting) {
            return;
        }
        mEndMessage = endMessage;
        isStopByUser = true;
        MAIN_HANDLER.removeCallbacks(mPrintTask);
        isPrinting = false;
        mMarkdownParser.setPrintingState(false);
        if (!TextUtils.isEmpty(endMessage)) {
            mEndMessage = endMessage;
        }
        clearGradient();
        boolean printAll = mCurrentPrintIndex >= mParsedContentText.length() - 1;
        SpannableStringBuilder span = handleSpan(mParsedContentText, mCurrentPrintIndex, printAll ? null : mEndMessage);
        if (!printAll) {
            setEndMessageStyle(span);
        }
        setTextSafely(span);
        if (mPrintingEventListener != null) {
            mPrintingEventListener.onPrintStop(printAll);
        }
        if (mPrintData != null) {
            mPrintData.showingText = span;
            mPrintData.isPrinting = isPrinting;
            mPrintData.isStopByUser = isStopByUser;
        }
    }

    public void restore(MarkDownPrintData markDownData) {
        MDLogger.d(TAG, "restore-markDownData:" + markDownData);
        if (markDownData == null || TextUtils.isEmpty(markDownData.showingText)) {
            return;
        }
        if (mPrintData == markDownData) {
            return;
        }
        mPrintData = markDownData;
        setMinHeight(0);
        mTableScrollXs.clear();
        mParsedContentText = mPrintData.parsedMarkdownText;
        mCurrentPrintIndex = mPrintData.currentIndex;
        mChunkSize = mPrintData.chunkSize;
        mInterval = mPrintData.interval;
        isPrinting = mPrintData.isPrinting;
        isStopByUser = mPrintData.isStopByUser;
        setTextSafely(mPrintData.showingText);
        mOriginText = mPrintData.originalText;
        if (isPrinting && !isStopByUser) {
            printing(mCurrentPrintIndex, mChunkSize);
        }
        handleSpm();
    }

    public void setSizeChangedListener(SizeChangedListener listener) {
        mSizeChangedListener = listener;
    }

    public void setPrintTickListener(PrintTickListener listener) {
        mPrintTickListener = listener;
    }

    public void setPrintingEventListener(PrintingEventListener listener) {
        mPrintingEventListener = listener;
    }

    public void pause() {
        if (!isStarted) {
            MDLogger.e(TAG, "appendPrinting printing has not started!");
            return;
        }
        isPrinting = false;
        mMarkdownParser.setPrintingState(false);
        MAIN_HANDLER.removeCallbacks(mPrintTask);
        if (mPrintingEventListener != null) {
            mPrintingEventListener.onPrintPaused(mCurrentPrintIndex);
        }
    }

    public void resume() {
        resume(mCurrentPrintIndex);
    }

    public void resume(int index) {
        if (!isStarted) {
            MDLogger.e(TAG, "appendPrinting printing has not started!");
            return;
        }
        if (isPrinting) {
            return;
        }
        isStopByUser = false;
        isPrinting = true;
        if (index <= mParsedContentText.length()) {
            mMarkdownParser.setPrintingState(true);
            printing(mCurrentPrintIndex, mChunkSize);
        }
        if (mPrintingEventListener != null) {
            mPrintingEventListener.onPrintResumed();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int newHeight = getMeasuredHeight();
        if (newHeight != mHeight ) {
            if (mSizeChangedListener != null) {
                mSizeChangedListener.onSizeChanged(getMeasuredWidth(), newHeight);
            }
            if(newHeight > mHeight) {
                setMinHeight(newHeight);
            }
            mHeight = newHeight;
        }
    }

    private void printing(int start, int chunkSize) {
        if (isDestroyed) {
            return;
        }
        int end = start + chunkSize;
        if (end >= mParsedContentText.length()) {
            MDLogger.i(TAG, "end >= spannablePrintText.length()");
            isPrinting = false;
            mMarkdownParser.setPrintingState(false);
            setTextSafely(mParsedContentText);
            onStopPrinting();
        } else {
            SpannableStringBuilder newSpannable = handleSpan(mParsedContentText, end);
            if (newSpannable == null) {
                return;
            }
            mCurrentPrintIndex = end;
            gradiantColorAnimateText(mCurrentPrintIndex, newSpannable);
            setTextSafely(newSpannable);
            // 每帧打印后触发 tick，外层据此持续跟滚（不等高度变化/换行）
            if (mPrintTickListener != null) {
                mPrintTickListener.onPrintTick();
            }
            if (!isStopByUser) {
                MAIN_HANDLER.removeCallbacks(mPrintTask);
                MAIN_HANDLER.postDelayed(mPrintTask, mInterval);
            } else {
                MAIN_HANDLER.removeCallbacks(mPrintTask);
                MDLogger.i(TAG, "printing---isStopByUser==true");
                isPrinting = false;
                onStopPrinting();
            }
        }
        if (mPrintData != null) {
            mPrintData.currentIndex = mCurrentPrintIndex;
        }

    }

    private void clearGradient() {
        if (mGradiantSpans == null) {
            return;
        }
        mParsedContentText.removeSpan(OpacitySpan.class);
    }

    /**
     * 实现渐变打字的动画
     */
    private void gradiantColorAnimateText(int endIndex, SpannableStringBuilder spannableStringBuilder) {
        if (endIndex == mParsedContentText.length()) {
            return;
        }
        if (mGradiantSpans == null) {
            mGradiantSpans = new OpacitySpan[GRADIANT_COUNT];
            for (int i = 0; i < GRADIANT_COUNT; i++) {
                int alpha = (int) (255f * i / GRADIANT_COUNT);
                mGradiantSpans[GRADIANT_COUNT - i - 1] = new OpacitySpan(alpha);
            }
        }

        for (int i = 0; i < GRADIANT_COUNT; i++) {
            int textIndex = endIndex - i - 1;
            if (textIndex < 0) {
                break;
            }
            spannableStringBuilder.setSpan(mGradiantSpans[GRADIANT_COUNT - i - 1], textIndex, textIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private SpannableStringBuilder handleSpan(SpannableStringBuilder source, int end) {
        return handleSpan(source, end, "");
    }

    private SpannableStringBuilder handleSpan(SpannableStringBuilder source, int end, String endTag) {
        SpannableStringBuilder newSpannable;
        if (TextUtils.isEmpty(endTag)) {
            newSpannable = new SpannableStringBuilder(source.subSequence(0, end));
        } else {
            newSpannable = new SpannableStringBuilder(source.subSequence(0, end)).append(endTag);
        }
        CharacterStyle[] spans = source.getSpans(0, end, CharacterStyle.class);
        if (spans == null) {
            return null;
        }
        for (CharacterStyle span : spans) {
            if (span == null) {
                continue;
            }
            int spanStart = source.getSpanStart(span);
            int spanEnd = source.getSpanEnd(span);
            int flags = source.getSpanFlags(span);

            newSpannable.setSpan(span, spanStart, Math.min(spanEnd, end), flags);
        }

        return newSpannable;
    }

    private void onStopPrinting() {
        if (mPrintingEventListener != null) {
            mPrintingEventListener.onPrintStop(mParsedContentText == null || mCurrentPrintIndex == mParsedContentText.length());
        }
    }

    private void setEndMessageStyle(SpannableStringBuilder span) {
        if (TextUtils.isEmpty(mEndMessage)) {
            return;
        }
        // 设置颜色
        span.setSpan(
                new ForegroundColorSpan(Color.parseColor("#999999")),
                span.length() - mEndMessage.length(),
                span.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        // 设置字体大小
        span.setSpan(
                new AbsoluteSizeSpan(Utils.dpToPx(getContext(), 13), false),
                span.length() - mEndMessage.length(),
                span.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    public void onDestroy() {
        LoadIconUtil.check();
        isDestroyed = true;
        mPrintData = null;
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner l) {
        onDestroy();
    }


    public void setMarkdownStyles(MarkdownStyles markdownStyles) {
        if (mMarkdownParser != null) {
            mMarkdownStyles = markdownStyles;
            mMarkdownParser.updateMarkdownStyles(markdownStyles);
        }
    }

    public void setTextSafely(SpannableStringBuilder spanned) {
        if (mMarkdownParser != null) {
            mMarkdownParser.getMarkwon().setParsedMarkdown(this, spanned);
        }
        if (mPrintData != null && spanned != null) {
            mPrintData.showingText = spanned;
        }
    }

    public void setMaxWidthForMeasure(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    @Override
    public int getViewMaxWidth() {
        return maxWidth;
    }

    @Override
    public String getOriginText() {
        return mOriginText;
    }

    @Override
    public int getTableScrollX(int tableIndex) {
        final Integer value = mTableScrollXs.get(tableIndex);
        return value == null ? 0 : value;
    }

    @Override
    public void setTableScrollX(int tableIndex, int scrollX) {
        if (scrollX <= 0) {
            mTableScrollXs.remove(tableIndex);
        } else {
            mTableScrollXs.put(tableIndex, scrollX);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (handleTableHorizontalScroll(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 表格横向滚动手势（Span 自绘表格无法套 HorizontalScrollView，改为共享偏移平移）：
     * - DOWN 落在可滚动表格上时先接管；若 DOWN 检测未命中，MOVE 横向主导时动态检测并接管，
     *   双保险不依赖单次检测（事件由 MovementMethod 消费时本 View 仍是 touch target，MOVE 持续到达）；
     * - 千问同款仲裁：|dy|×2 > |dx| 视为纵向，放行交还列表；横向主导则接管滚动，
     *   并 requestDisallowInterceptTouchEvent 防止拖动中被列表中途抢占；
     * - UP 未发生滚动时放行，交回原链路（单元格链接/标题栏按钮）；滚动结束则消费，避免误触。
     */
    private boolean handleTableHorizontalScroll(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // 新触摸打断进行中的惯性滑动
                stopTableFling();
                if (mFlingVelocityTracker == null) {
                    mFlingVelocityTracker = VelocityTracker.obtain();
                } else {
                    mFlingVelocityTracker.clear();
                }
                mFlingVelocityTracker.addMovement(event);
                mTableHorizontalScrolling = false;
                mDownRawX = event.getX();
                mDownRawY = event.getY();
                mActiveTableSpan = findTableRowSpanUnder(event);
                if (mActiveTableSpan == null) {
                    mDownOnScrollableTable = false;
                    return false; // 不在表格上：走原链路（链接点击等），MOVE 阶段仍可动态接管
                }
                final int range = mActiveTableSpan.getScrollRange(getTableContentWidth());
                if (range <= 0) {
                    MDLogger.d(TABLE_SCROLL_TAG, "DOWN table=" + mActiveTableSpan.getTableIndex()
                            + " not scrollable, range=0, viewW=" + getTableContentWidth());
                    mDownOnScrollableTable = false;
                    return false;
                }
                mDownOnScrollableTable = true;
                mTableDownX = event.getX();
                mTableDownY = event.getY();
                mTableScrollStartX = getTableScrollX(mActiveTableSpan.getTableIndex());
                MDLogger.d(TABLE_SCROLL_TAG, "DOWN on table=" + mActiveTableSpan.getTableIndex()
                        + " range=" + range);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mFlingVelocityTracker != null) {
                    mFlingVelocityTracker.addMovement(event);
                }
                if (mTableHorizontalScrolling && mActiveTableSpan != null) {
                    applyTableScroll(event);
                    return true;
                }
                final float rawDx = event.getX() - mDownRawX;
                final float rawDy = event.getY() - mDownRawY;
                // 千问仲裁：|dy|×2 > |dx| 视为纵向（交还列表），否则横向可接管
                final boolean horizontalDominant =
                        Math.abs(rawDx) > mTouchSlop
                                && Math.abs(rawDy) * VERTICAL_SCALE_RATIO <= Math.abs(rawDx);

                if (!mDownOnScrollableTable) {
                    // DOWN 时未接管（可能检测未命中）：横向主导时动态检测触点下方是否是可滚表格，
                    // 是则此刻接管——不依赖 DOWN 单次检测成败
                    if (horizontalDominant) {
                        final TableRowSpan span = findTableRowSpanUnder(event);
                        if (span != null && span.getScrollRange(getTableContentWidth()) > 0) {
                            mDownOnScrollableTable = true;
                            mActiveTableSpan = span;
                            mTableDownX = event.getX();
                            mTableDownY = event.getY();
                            mTableScrollStartX = getTableScrollX(span.getTableIndex());
                            beginTableScrollState();
                            MDLogger.d(TABLE_SCROLL_TAG, "MOVE takeover table=" + span.getTableIndex());
                            applyTableScroll(event);
                            return true;
                        }
                    }
                    return false;
                }

                // DOWN 已接管：等待/进行方向判定
                if (!mTableHorizontalScrolling) {
                    if (!horizontalDominant) {
                        if (Math.abs(rawDy) > mTouchSlop) {
                            // 纵向主导：放弃接管，父层列表将拦截剩余手势（随后收到 CANCEL）
                            MDLogger.d(TABLE_SCROLL_TAG, "vertical dominant, release");
                            mDownOnScrollableTable = false;
                            return false;
                        }
                        return true; // 未过 slop，继续持有
                    }
                    mTableDownX = event.getX();
                    mTableDownY = event.getY();
                    mTableScrollStartX = getTableScrollX(mActiveTableSpan.getTableIndex());
                    beginTableScrollState();
                    MDLogger.d(TABLE_SCROLL_TAG, "start horizontal scroll");
                }
                applyTableScroll(event);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                final boolean wasScrolling = mTableHorizontalScrolling;
                final boolean downOnTable = mDownOnScrollableTable;
                final TableRowSpan upSpan = mActiveTableSpan;
                resetTableScrollGesture();
                if (!downOnTable) {
                    return false;
                }
                if (wasScrolling) {
                    // 抬手后按抬起速度继续惯性滑动（不够快则自然停在当前偏移）
                    startTableFling(upSpan, event);
                    return true; // 滚动结束，消费，避免误触链接/按钮
                }
                if (wasScrolling) {
                    return true; // 滚动结束，消费，避免误触链接/按钮
                }
                // 未滚动：当作点击交回原链路（链接/标题栏按钮）
                MDLogger.d(TABLE_SCROLL_TAG, "UP as click");
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_CANCEL: {
                final boolean downOnTable = mDownOnScrollableTable;
                resetTableScrollGesture();
                return downOnTable;
            }
            default:
                return mTableHorizontalScrolling;
        }
    }

    private void applyTableScroll(MotionEvent event) {
        final int range = mActiveTableSpan.getScrollRange(getTableContentWidth());
        int newScrollX = (int) (mTableScrollStartX - (event.getX() - mTableDownX));
        newScrollX = Math.max(0, Math.min(range, newScrollX));
        final int tableIndex = mActiveTableSpan.getTableIndex();
        if (newScrollX != getTableScrollX(tableIndex)) {
            setTableScrollX(tableIndex, newScrollX);
            postInvalidateOnAnimation();
            // 实测（TABLE_SCROLL_DBG 日志）：打字机结束后 invalidate/requestLayout 族触发的
            // onDraw 会跳过 ReplacementSpan 绘制（疑似 ROM 对文本未变化时的绘制省略优化）；
            // 而渲染期间能正常滚动靠的是每帧 setTextSafely(新切片) -> setText -> 完整重绘。
            // 这里对齐该机制：重设同一文本对象，触发与打字机一致的全量重绘链路。
            final CharSequence text = getText();
            if (text instanceof SpannableStringBuilder) {
                setTextSafely((SpannableStringBuilder) text);
            }
            MDLogger.d(TABLE_SCROLL_TAG, "scrollX=" + newScrollX + "/" + range);
        }
    }

    /**
     * 进入表格横滚状态：请求父层（聊天列表 RV）停止拦截剩余手势，
     * 避免拖动轨迹中的纵向漂移让列表中途抢占（CANCEL 打断滚动）。
     * requestDisallowInterceptTouchEvent 会沿 View 树自动向上传播。
     */
    private void beginTableScrollState() {
        mTableHorizontalScrolling = true;
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    /** 恢复父层拦截权（抬手/取消后调用） */
    private void releaseTableParentIntercept() {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(false);
        }
    }

    private void resetTableScrollGesture() {
        mDownOnScrollableTable = false;
        mTableHorizontalScrolling = false;
        mActiveTableSpan = null;
        releaseTableParentIntercept();
    }

    /**
     * 抬手惯性滑动：沿用抬起时的横向速度让表格继续滚动，效果接近横向滚动列表的 fling。
     * 用 OverScroller 计算减速轨迹，postOnAnimation 逐帧驱动；每帧重设同一文本对象，
     * 走与打字机一致的全量重绘链路（ROM 优化问题见 applyTableScroll 注释）。
     * 速度方向：手指向左甩（velocityX<0）内容向左移、scrollX 增大，故取反传入 fling。
     */
    private void startTableFling(TableRowSpan span, MotionEvent event) {
        if (span == null || mFlingVelocityTracker == null) {
            return;
        }
        mFlingVelocityTracker.addMovement(event);
        mFlingVelocityTracker.computeCurrentVelocity(1000);
        final float vx = mFlingVelocityTracker.getXVelocity();
        final int range = span.getScrollRange(getTableContentWidth());
        final int startX = getTableScrollX(span.getTableIndex());
        if (range <= 0) {
            return;
        }
        final int minFlingVelocity = ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity();
        if (Math.abs(vx) < minFlingVelocity) {
            return;
        }
        mFlingTableIndex = span.getTableIndex();
        mFlingRange = range;
        mTableFlingScroller.fling(startX, 0, Math.round(-vx), 0, 0, range, 0, 0);
        mTableFlinging = true;
        MDLogger.d(TABLE_SCROLL_TAG, "fling start vx=" + vx + " startX=" + startX + " range=" + range);
        postOnAnimation(this::runTableFling);
    }

    private void runTableFling() {
        if (!mTableFlinging || mFlingTableIndex < 0) {
            return;
        }
        if (!mTableFlingScroller.computeScrollOffset()) {
            stopTableFling();
            return;
        }
        final int x = mTableFlingScroller.getCurrX();
        if (x != getTableScrollX(mFlingTableIndex)) {
            setTableScrollX(mFlingTableIndex, x);
            postInvalidateOnAnimation();
            final CharSequence text = getText();
            if (text instanceof SpannableStringBuilder) {
                setTextSafely((SpannableStringBuilder) text);
            }
            MDLogger.d(TABLE_SCROLL_TAG, "fling scrollX=" + x + "/" + mFlingRange);
        }
        postOnAnimation(this::runTableFling);
    }

    /** 停止惯性滑动：自然结束、新触摸 DOWN、视图销毁时调用 */
    private void stopTableFling() {
        if (!mTableFlinging) {
            return;
        }
        mTableFlinging = false;
        mTableFlingScroller.forceFinished(true);
        mFlingTableIndex = -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTableFling();
        if (mFlingVelocityTracker != null) {
            mFlingVelocityTracker.recycle();
            mFlingVelocityTracker = null;
        }
    }

    /** 表格可用内容宽度（与 TableRowSpan getSize/layout 基准一致） */
    private int getTableContentWidth() {
        final CharSequence text = getText();
        return SpanUtils.width(null, text);
    }

    /**
     * 定位触点下方是否是表格行（TableRowSpan）。
     * 表格行是 ReplacementSpan 只占 1 个字符，按触点 offset 精确查询在端点处可能不命中，
     * 这里用整行字符区间查询，保证该行存在表格 span 时必然命中。
     */
    private TableRowSpan findTableRowSpanUnder(MotionEvent event) {
        final Layout layout = getLayout();
        final CharSequence text = getText();
        if (layout == null || !(text instanceof Spannable)) {
            MDLogger.d(TABLE_SCROLL_TAG, "findSpan: layout/text not ready");
            return null;
        }
        final int x = (int) event.getX() - getTotalPaddingLeft() + getScrollX();
        final int y = (int) event.getY() - getTotalPaddingTop() + getScrollY();
        if (y < 0 || y > layout.getHeight()) {
            return null;
        }
        final int line = layout.getLineForVertical(y);
        final int lineStart = layout.getLineStart(line);
        final int lineEnd = Math.max(lineStart + 1, layout.getLineEnd(line));
        final TableRowSpan[] spans = ((Spannable) text).getSpans(lineStart, lineEnd, TableRowSpan.class);
        if (spans.length > 0) {
            return spans[0];
        }
        MDLogger.d(TABLE_SCROLL_TAG, "findSpan: no span at line=" + line + " range=[" + lineStart + "," + lineEnd + ")");
        return null;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        /* no-op */

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        /* no-op */
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mElementClickEventCallback == null) {
            return;
        }
        CharSequence str = getText();
        if (!(str instanceof Spannable)) {
            return;
        }
        Spanned spanned = (Spanned) str;
        if (spanned.length() == 0) {
            return;
        }
        IClickableSpan[] clickableSpans = spanned.getSpans(0, spanned.length(), IClickableSpan.class);
        if (clickableSpans == null || clickableSpans.length == mExposureNodeCount) {
            return;
        }
        mClickableSpans = clickableSpans;
        handleScrollerSpm();
        mExposureNodeCount = clickableSpans.length;
        post(() -> handleSpm(clickableSpans));
    }

    private void handleScrollerSpm() {
        if (initialedScrollerWatcher || mClickableSpans == null) {
            return;
        }
        initialedScrollerWatcher = true;
        View scroller = findScroller(getParent());
        if (scroller instanceof RecyclerView) {
            ((RecyclerView)scroller).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int state) {
                    super.onScrollStateChanged(recyclerView, state);
                    if (state == RecyclerView.SCROLL_STATE_IDLE) {
                        handleSpm(mClickableSpans);
                    }
                }
            });
        } else if (scroller != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                scroller.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> handleSpm(mClickableSpans));
            }

        }
    }

    private View findScroller(ViewParent child) {
        ViewParent parent = child.getParent();
        if (parent instanceof View && (parent instanceof RecyclerView || ((View)parent).isScrollContainer())) {
            return (View) parent;
        } else if (parent == null) {
            return null;
        }
        return findScroller(parent);
    }

    private void handleSpm() {
        if (mClickableSpans == null || mClickableSpans.length == 0) {
            return;
        }
        handleSpm(mClickableSpans);
    }
    private void handleSpm(IClickableSpan[] spans) {
        if (spans == null || spans.length == 0) {
            return;
        }
        Rect rect = new Rect();
        boolean isVisible = getGlobalVisibleRect(rect);
        MDLogger.d(TAG, "updateEventLogList:rect:" + rect + " rect isVisible:" + isVisible);

        int startY = 0;
        int endY = 0;
        if (isVisible) {
            int[] pos = new int[2];
            getLocationOnScreen(pos);
            startY = rect.top - pos[1];
            endY = startY + rect.height();
            MDLogger.d(TAG, "updateEventLogList:location:" + Arrays.toString(pos) + ",startY:" + startY + ",endY:" + endY);
        }
        List<EventModel> models = new ArrayList<>();
        for (IClickableSpan span : spans) {
            if (span instanceof AsyncDrawableSpan && !((AsyncDrawableSpan) span).isClickable()) {
                return;
            }
            boolean visible = (span.getTop() > startY && span.getTop() < endY) // visible Top
                    || span.getBottom() > startY && span.getBottom() < endY; // visible Bottom
            MDLogger.d(TAG, "updateEventLogList:" + span.getClass().getSimpleName() + " span.getTop():" + span.getTop() + ", span.getBottom()" + span.getBottom() + ",visible:" + visible);
            models.add(new EventModel(span.getType(), span.getUrl(), span.getLiteral(), visible));
        }
        if (models.equals( mEventModels)) {
            return;
        }
        this.mEventModels = models;
        if (mElementClickEventCallback != null) {
            mElementClickEventCallback.exposureSpmBehavior(models);
        }
    }

    public int getPrintIndex() {
        return mCurrentPrintIndex;
    }

    public interface SizeChangedListener {
        void onSizeChanged(int width, int height);
    }

    /**
     * 打字机每帧打印 tick 回调（独立于高度变化）。
     * 千问方案：TypewriterController 每帧（Choreographer）推进文本后都会触发外层滚动检查，
     * 而非仅高度变化（换行）时触发——否则同一行内逐字打印不滚动，视觉上"一行一行跳"。
     */
    public interface PrintTickListener {
        void onPrintTick();
    }

    public interface PrintingEventListener {
        void onPrintStart();

        void onPrintStop(boolean printAll);
        void onPrintPaused(int index);

        void onPrintResumed();
    }
    public static class MarkDownPrintData {
        public int chunkSize;
        public int interval;
        public boolean isPrinting;
        public boolean isStopByUser;
        public String originalText;
        public SpannableStringBuilder showingText; // showingData
        public SpannableStringBuilder parsedMarkdownText; // fullData
        public int currentIndex;
        public boolean hasBoundView;

        @Override
        public String toString() {
            return "MarkDownPrintData{" +
                    "chunkSize=" + chunkSize +
                    ", speed=" + interval +
                    ", isPrinting=" + isPrinting +
                    ", showingText='" + showingText + '\'' +
                    ", printingText='" + parsedMarkdownText + '\'' +
                    ", currentIndex=" + currentIndex +
                    ", createdView=" + hasBoundView +
                    '}';
        }
    }

}
