package io.noties.markwon.ext.tables;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fluid.afm.IMarkdownLayer;
import com.fluid.afm.styles.TableStyle;
import com.fluid.afm.utils.MDLogger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.core.spans.TextLayoutSpan;
import io.noties.markwon.core.spans.TextViewSpan;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.utils.LeadingMarginUtils;
import io.noties.markwon.utils.SpanUtils;

public class TableRowSpan extends ReplacementSpan {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    @IntDef(value = {ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Alignment {
    }

    public interface Invalidator {
        void invalidate();
    }

    public static class Cell {

        final int alignment;
        final CharSequence text;

        public Cell(@Alignment int alignment, CharSequence text) {
            this.alignment = alignment;
            this.text = text;
        }

        @Alignment
        public int alignment() {
            return alignment;
        }

        public CharSequence text() {
            return text;
        }

        @NonNull
        @Override
        public String toString() {
            return "Cell{" +
                    "alignment=" + alignment +
                    ", text=" + text +
                    '}';
        }
    }

    private final TableStyle mStyle;
    private final List<Cell> cells;
    private final List<Layout> layouts;
    private final TextPaint textPaint;
    public final boolean header;
    private final boolean isHideHeader;
    private final boolean odd;

    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int width;
    private int height;
    private Invalidator invalidator;
    private final int rowsNumber;
    private int currentMaxRows;
    private final int radius;
    private float leading;

    /** 本表在文档中的序号；同一 TextView 可能渲染多张表，横向滚动偏移按表区分 */
    private int tableIndex = -1;

    public TableRowSpan(
            @NonNull TableStyle tableStyle,
            @NonNull List<Cell> cells,
            boolean header,
            boolean odd,
            int rows) {
        this.mStyle = tableStyle;
        this.cells = cells;
        this.layouts = new ArrayList<>(cells.size());
        this.textPaint = new TextPaint();
        this.header = header;
        this.isHideHeader = false;
        this.odd = odd;
        this.rowsNumber = rows;
        this.radius = tableStyle.tableBlockRadius();
    }

    public TableRowSpan(
            @NonNull TableStyle tableStyle,
            @NonNull List<Cell> cells,
            boolean header,
            boolean isHideHeader,
            boolean odd,
            int rows) {
        this.mStyle = tableStyle;
        this.cells = cells;
        this.layouts = new ArrayList<>(cells.size());
        this.textPaint = new TextPaint();
        this.header = header;
        this.isHideHeader = isHideHeader;
        this.odd = odd;
        this.rowsNumber = rows;
        this.radius = tableStyle.tableBlockRadius();
    }


    /**
     * 更新当前最大行号
     * @param currentMaxRows
     */
    public void updateCurrentMaxRows(int currentMaxRows) {
        this.currentMaxRows = currentMaxRows;
    }

    @Override
    public int getSize(
            @NonNull Paint paint,
            CharSequence text,
            @IntRange(from = 0) int start,
            @IntRange(from = 0) int end,
            @Nullable Paint.FontMetricsInt fm) {
        // it's our absolute requirement to have width of the canvas here... because, well, it changes
        // the way we draw text. So, if we do not know the width of canvas we cannot correctly measure our text

        int maxWTmp = 0;
        if (text instanceof Spanned){
            final Spanned spanned = (Spanned) text;
            final TextView textView = TextViewSpan.textViewOf(spanned);
            if (textView != null){
                if (textView instanceof IMarkdownLayer){
                    maxWTmp = ((IMarkdownLayer) textView).getViewMaxWidth() - 1;
                }
            }
        }
        if (layouts.size() == 0 && paint != null){

            final int spanWidth = SpanUtils.width(null, text);

            //MDLogger.d("gavin-test", "===xxx=== getSize  targetW="+spanWidth +"_" +maxWTmp + "  view=" + stringTmp);
            int oldW = width;
            width = spanWidth;
            if (width <=0){
                width = maxWTmp;
            }
            // @since 4.3.1 it's important to cast to TextPaint in order to display links, etc
            if (paint instanceof TextPaint) {
                // there must be a reason why this method receives Paint instead of TextPaint...
                textPaint.set((TextPaint) paint);
            } else {
                textPaint.set(paint);
            }
            mStyle.applyTableTextStyle(textPaint, header);
            makeNewLayouts();
            width = oldW;
        }

        if (layouts.size() > 0) {

            if (fm != null) {

                int max = 0;
                for (Layout layout : layouts) {
                    final int height = layout.getHeight();
                    if (height > max) {
                        max = height;
                    }
                }

                // we store actual height
                height = max;

                // but apply height with padding
                int padding = mStyle.cellTopBottomPadding() * 2;
                if (isHideHeader && header) {
                    padding = 0;
                }
                fm.ascent = -(max + padding);
                fm.descent = 0;

                fm.top = fm.ascent;
                fm.bottom = 0;
            }
        }

        final int spanWidth = SpanUtils.width(null, text);
        int ret = width;
        if (maxWTmp > 0) {
            ret = Math.min(width, maxWTmp);
        } else if (spanWidth > 0) {
            ret = Math.min(width, spanWidth);
        }
        return  Math.min(1, (int) (ret - leading));
    }

    @Override
    public void draw(
            @NonNull Canvas canvas,
            CharSequence text,
            @IntRange(from = 0) int start,
            @IntRange(from = 0) int end,
            float x,
            int top,
            int y,
            int bottom,
            @NonNull Paint p) {

        final boolean isCurrentLastLine = (rowsNumber == currentMaxRows);
        final int spanWidth = SpanUtils.width(canvas, text);
        boolean invalidate = false;
        if (recreateLayouts(spanWidth, x)) {
            width = spanWidth;
            // @since 4.3.1 it's important to cast to TextPaint in order to display links, etc
            if (p instanceof TextPaint) {
                // there must be a reason why this method receives Paint instead of TextPaint...
                textPaint.set((TextPaint) p);
            } else {
                textPaint.set(p);
            }
            leading = x;
            mStyle.applyTableTextStyle(textPaint, header);
            makeNewLayouts();
            invalidate = true;
        }
        x = 0;

        int maxHeight = 0;

        final int padding = mStyle.cellTopBottomPadding();
        final int leftRightPadding = mStyle.cellLeftRightPadding();

        final int size = layouts.size();

        final float w = cellWidth(size);

        // @since 4.6.0 roundingDiff to offset last vertical border
        final float roundingDiff =(w - width / size) * size;

        // ===== 表格横向滚动 =====
        // 列宽含下限时内容总宽可能超过可视宽度 width，此时按共享偏移整体平移。
        // 标题栏(TableBlockTitleBlockSpan)是独立 Span，不参与平移，天然固定。
        final float contentWidth = w * size;
        final boolean scrollable = contentWidth - width > 0.5F;
        final int tableScrollX = scrollable ? obtainTableScrollX(text) : 0;
        final int clipTop = top; // header 让位标题栏前的行顶
        final int scrollSave = scrollable ? canvas.save() : -1;
        if (scrollable) {
            // 平移后的内容限制在可视区域内，防止画出文本区
            canvas.clipRect(0, clipTop, width, bottom);
            if (tableScrollX != 0) {
                canvas.translate(-tableScrollX, 0);
            }
        }

        // 如果是头，向下移动height空白区域给表头的顶部区块绘制使用
        if (header && !isHideHeader) {
            top += mStyle.titleBarHeight();
        }
        // @since 1.1.1
        // draw backgrounds
        {
            if (header) {
                mStyle.applyTableHeaderRowStyle(paint);
            } else if (odd) {
                mStyle.applyTableOddRowStyle(paint);
            } else {
                // even
                mStyle.applyTableEvenRowStyle(paint);
            }

            // if present (0 is transparent)
            if (paint.getColor() != 0) {
                final int save = canvas.save();
                try {
                    rect.set(0, 0, contentWidth, bottom - top);
                    canvas.translate(x, top);
                    if (isCurrentLastLine && !isHideHeader) {
                        drawRectWithBottomRound(canvas, paint, rect, radius);
                    } else if (isHideHeader && header && mStyle.drawBorder()) {
                        canvas.save();
                        canvas.clipRect(rect);
                        canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom + radius, radius, radius, paint);
                        canvas.restore();
                    } else {
                        canvas.drawRect(rect, paint);
                    }
                } finally {
                    canvas.restoreToCount(save);
                }
            }
        }

        // @since 1.1.1 reset after applying background color
        // as background changes color attribute and if not specific tableBorderColor
        // is specified then after this row all borders will have color of this row (plus alpha)
        paint.set(p);
        mStyle.applyTableBorderStyle(paint);

        final int borderWidth = mStyle.borderWidth();
        final boolean drawBorder = borderWidth > 0;

        // why divided by 4 gives a more or less good result is still not clear (shouldn't it be 2?)
        final int heightDiff = (bottom - top - height) / 3;

        // required for borderTop calculation
        final boolean isFirstTableRow;

        // @since 4.3.1
        if (drawBorder) {
            boolean first = false;
            // only if first draw the line
            {
                final Spanned spanned = (Spanned) text;
                final TableSpan[] spans = spanned.getSpans(start, end, TableSpan.class);
                if (spans != null && spans.length > 0) {
                    final TableSpan span = spans[0];
                    if (LeadingMarginUtils.selfStart(start, text, span)) {
                        first = true;
                        rect.set((int) x, top + borderWidth / 2, contentWidth, top + borderWidth + borderWidth / 2);
                        if (header && isHideHeader && mStyle.drawBorder() && radius > 0) {
                            final int save = canvas.save();
                            rect.set((int) x, top, contentWidth, bottom + radius);
                            canvas.clipRect(rect);
                            if (paint.getStrokeWidth() == 1) {
                                paint.setStrokeWidth(2);
                            }
                            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, radius, radius, paint);
                            canvas.restoreToCount(save);
                        } else {
                            canvas.drawLine(rect.left, rect.top, rect.right, rect.top, paint);
                        }
                    }
                }
            }
            // draw the line at the bottom
            if (!isCurrentLastLine) {
                rect.set((int) x, bottom - borderWidth / 2f, contentWidth, bottom);
                canvas.drawRect(rect, paint);
            } else if (isCurrentLastLine && mStyle.drawBorder()) {
                drawLineWithBottomRound(canvas, paint, x, top, contentWidth, bottom, radius);
            } else if (isCurrentLastLine && isHideHeader) {
                rect.set((int) x, bottom - borderWidth, contentWidth, bottom);
                canvas.drawRect(rect, paint);
            }
            isFirstTableRow = first;
        } else {
            isFirstTableRow = false;
        }


        // to NOT overlap borders inset top and bottom
        final int borderTop = isFirstTableRow ? borderWidth : 0;
        final int borderBottom = bottom - top - borderWidth;

        Layout layout;
        for (int i = 0; i < size; i++) {
            layout = layouts.get(i);
            final int save = canvas.save();
            try {

                canvas.translate(x + (i * w), top);

                // @since 4.3.1
                if (drawBorder) {
                    // first vertical border will have full width (it cannot exceed canvas)
                    if (i == 0) {
                        rect.set(0, borderTop, borderWidth, isCurrentLastLine ? borderBottom - radius + borderWidth : borderBottom);
                        if (header && isHideHeader && mStyle.drawBorder() && radius > 0) {
                            rect.set(rect.left, rect.top + radius, rect.right, rect.bottom + radius);
                        }
                    } else {
                        rect.set(0, borderTop, borderWidth, borderBottom);
                    }

                    if (mStyle.drawBorder() || i != 0) {
                        canvas.drawLine(rect.left + borderWidth / 2f, rect.top, rect.left + borderWidth / 2f, rect.bottom, paint);
                    }

                    if (i == (size - 1)) {
                        // @since 4.6.0 subtract rounding offset for the last vertical divider
                        rect.set(
                                w - borderWidth,
                                borderTop,
                                w - roundingDiff,
                                isCurrentLastLine ? borderBottom - radius + borderWidth : borderBottom
                        );
                        if (header && isHideHeader && mStyle.drawBorder() && radius > 0) {
                            rect.set(rect.left, rect.top + radius, rect.right, rect.bottom + radius);
                        }
                        // draw right line
                        if (mStyle.drawBorder()) {
                            canvas.drawLine(rect.right - borderWidth / 2f, rect.top, rect.right -  borderWidth / 2f, rect.bottom, paint);
                        }
                    }
                }

                canvas.translate(leftRightPadding, padding + heightDiff);
                layout.draw(canvas);

                if (layout.getHeight() > maxHeight) {
                    maxHeight = layout.getHeight();
                }

            } finally {
                canvas.restoreToCount(save);
            }
        }

        if (scrollSave >= 0) {
            canvas.restoreToCount(scrollSave);
        }

        if (height != maxHeight) {
            invalidate = true;
        }
        if (invalidate && invalidator != null) {
            invalidator.invalidate();
        }
    }

    private void drawLineWithBottomRound(Canvas canvas, Paint paint,
                                         float left, float top, float right, float bottom,
                                         float radius) {
        final int save = canvas.save();
        final float oldStrokeWidth = paint.getStrokeWidth();
        final int oldColor = paint.getColor();
        final Paint.Style oldStyle = paint.getStyle();
        mStyle.applyTableBorderStyle(paint);
        if (paint.getStrokeWidth() == 1) {
            paint.setStrokeWidth(2);
        }
        float halfWidth = paint.getStrokeWidth() / 2;
        canvas.clipRect(left, bottom -  radius, right, bottom + halfWidth);
        canvas.drawRoundRect(left + halfWidth, bottom -  radius - radius, right - halfWidth, bottom, radius, radius, paint);

        paint.setStrokeWidth(oldStrokeWidth);
        paint.setColor(oldColor);
        paint.setStyle(oldStyle);

        canvas.restoreToCount(save);
    }

    private void drawRectWithBottomRound(Canvas canvas, Paint paint, RectF rect, float radius) {
        drawRectWithBottomRound(canvas, paint, rect.left, rect.top, rect.right, rect.bottom, radius);
    }

    private void drawRectWithBottomRound(Canvas canvas, Paint paint,
                                         float left, float top, float right, float bottom,
                                         float radius) {
        final int save = canvas.save();
        final float oldStrokeWidth = paint.getStrokeWidth();
        final int oldColor = paint.getColor();
        final Paint.Style oldStyle = paint.getStyle();

        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        RectF rect = new RectF(left, top, right, bottom);

        // Move to the top-left corner
        path.moveTo(rect.left, rect.top);

        // Top line
        path.lineTo(rect.right, rect.top);

        // Right line
        path.lineTo(rect.right, rect.bottom - radius);

        // Bottom-right corner
        path.arcTo(new RectF(rect.right - 2*radius, rect.bottom - 2*radius, rect.right, rect.bottom), 0, 90, false);

        // Bottom line
        path.lineTo(rect.left + radius, rect.bottom);

        // Bottom-left corner
        path.arcTo(new RectF(rect.left, rect.bottom - 2*radius, rect.left + 2*radius, rect.bottom), 90, 90, false);

        // Left line
        path.lineTo(rect.left, rect.top);

        // Close the path
        path.close();

        canvas.drawPath(path, paint);

        paint.setStrokeWidth(oldStrokeWidth);
        paint.setColor(oldColor);
        paint.setStyle(oldStyle);

        canvas.restoreToCount(save);
    }

    private boolean recreateLayouts(int newWidth, float x) {
        return width != newWidth || x != leading;
    }

    private void makeNewLayouts() {

        final int columns = cells.size();
        final int padding = mStyle.cellLeftRightPadding() * 2;
        float w = cellWidth(columns) - padding + mStyle.cellTopBottomPadding();
        if (w < 0) {
            w = padding;
            MDLogger.d("MYA_TableRowSpan", "w:" + w + ",padding:" + padding);
        }

        this.layouts.clear();

        for (int i = 0, size = cells.size(); i < size; i++) {
            makeLayout(i, (int) w, cells.get(i));
        }
    }

    private void makeLayout(final int index, final int width, @NonNull final Cell cell) {

        final Runnable recreate = () -> {
            final Invalidator invalidator = TableRowSpan.this.invalidator;
            if (invalidator != null) {
                layouts.remove(index);
                makeLayout(index, width, cell);
                invalidator.invalidate();
            }
        };

        final Spannable spannable;

        if (cell.text instanceof Spannable) {
            spannable = (Spannable) cell.text;
        } else {
            spannable = new SpannableString(cell.text);
        }

        final Layout layout = new StaticLayout(
                spannable,
                textPaint,
                width,
                alignment(cell.alignment),
                1.0F,
                0.0F,
                false
        );

        // @since 4.4.0
        TextLayoutSpan.applyTo(spannable, layout);

        // @since 4.4.0
        scheduleAsyncDrawables(spannable, recreate);

        layouts.add(index, layout);
    }

    private void scheduleAsyncDrawables(@NonNull Spannable spannable, @NonNull final Runnable recreate) {

        final AsyncDrawableSpan[] spans = spannable.getSpans(0, spannable.length(), AsyncDrawableSpan.class);
        if (spans != null
                && spans.length > 0) {

            for (AsyncDrawableSpan span : spans) {

                final AsyncDrawable drawable = span.getDrawable();

                // it is absolutely crucial to check if drawable is already attached,
                //  otherwise we would end up with a loop
                if (drawable.isAttached()) {
                    continue;
                }

                drawable.setCallback2(new CallbackAdapter() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        recreate.run();
                    }
                });
            }
        }
    }

    /**
     * Obtain Layout given horizontal offset. Primary usage target - MovementMethod
     *
     * @since 4.6.0
     */
    @Nullable
    public Layout findLayoutForHorizontalOffset(int x) {
        final int size = layouts.size();
        final float w = cellWidth(size);
        final int i = (int) (x / w);
        if (i >= size) {
            return null;
        }
        return layouts.get(i);
    }

    /**
     * @since 4.6.0
     */
    public int cellWidth() {
        return (int) cellWidth(layouts.size());
    }

    /** 列宽下限：每列最小可视宽度的 1/3（参考千问：列数超过约 3 列时表格超宽横滚） */
    private static final float MIN_CELL_WIDTH_DIVISOR = 3F;

    // 列宽 = max(等分宽, 可视宽/3)。列数 > 3 时总宽超过可视宽度，配合横向滚动查看全部列。
    // isHideHeader = 独立展示模式（全屏预览页，外部已按列数提供超宽画布），
    // 保持等分铺满，由外层横向 RecyclerView 负责滚动。
    // @since 4.6.0
    protected float cellWidth(int size) {
        if (size <= 0) {
            return 0;
        }
        final float equalWidth = 1F * width / size;
        if (isHideHeader) {
            return equalWidth;
        }
        final float minCellWidth = width / MIN_CELL_WIDTH_DIVISOR;
        return Math.max(equalWidth, minCellWidth);
    }

    @SuppressLint("SwitchIntDef")
    private static Layout.Alignment alignment(@Alignment int alignment) {
        final Layout.Alignment out;
        switch (alignment) {
            case ALIGN_CENTER:
                out = Layout.Alignment.ALIGN_CENTER;
                break;
            case ALIGN_RIGHT:
                out = Layout.Alignment.ALIGN_OPPOSITE;
                break;
            default:
                out = Layout.Alignment.ALIGN_NORMAL;
                break;
        }
        return out;
    }

    public void invalidator(@Nullable Invalidator invalidator) {
        this.invalidator = invalidator;
    }

    public void setTableIndex(int tableIndex) {
        this.tableIndex = tableIndex;
    }

    public int getTableIndex() {
        return tableIndex;
    }

    /**
     * 表格可横向滚动的范围：内容总宽 - 可视宽度（<=0 表示当前不可滚）。
     * 显式传入 viewWidth 计算，不依赖 draw 阶段才更新的内部 width 状态。
     * isHideHeader = 独立展示模式（全屏预览，外层已有超宽画布+横向 RV），不启用 Span 内横滚。
     */
    public int getScrollRange(int viewWidth) {
        if (isHideHeader) {
            return 0;
        }
        final int size = layouts.size();
        if (size == 0 || viewWidth <= 0) {
            return 0;
        }
        final float cellW = Math.max(1F * viewWidth / size, viewWidth / MIN_CELL_WIDTH_DIVISOR);
        return Math.max(0, (int) (cellW * size) - viewWidth);
    }

    private static final String TABLE_SCROLL_TAG = "TABLE_SCROLL_DBG";

    /**
     * 从宿主 TextView 读取本表的横向滚动偏移；宿主未实现 IMarkdownLayer 时视为不滚动。
     */
    private int obtainTableScrollX(CharSequence text) {
        if (!(text instanceof Spanned)) {
            MDLogger.d(TABLE_SCROLL_TAG, "obtainScrollX: text not Spanned");
            return 0;
        }
        if (tableIndex < 0) {
            MDLogger.d(TABLE_SCROLL_TAG, "obtainScrollX: tableIndex not set");
            return 0;
        }
        final TextView textView = TextViewSpan.textViewOf((Spanned) text);
        if (textView instanceof IMarkdownLayer) {
            return ((IMarkdownLayer) textView).getTableScrollX(tableIndex);
        }
        MDLogger.d(TABLE_SCROLL_TAG, "obtainScrollX: no IMarkdownLayer host, tv=" + textView);
        return 0;
    }

    private static abstract class CallbackAdapter implements Drawable.Callback {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {

        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {

        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {

        }
    }
}
