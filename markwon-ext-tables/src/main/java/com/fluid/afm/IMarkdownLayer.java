package com.fluid.afm;

public interface IMarkdownLayer {
    int getViewMaxWidth();

    String getOriginText();

    /**
     * 获取指定表格的横向滚动偏移（像素）。
     * 表格内容宽度超出可视宽度时，TableRowSpan 绘制时整体平移 -scrollX，
     * 该状态由宿主 TextView 持有，保证同一张表的所有行同步滚动。
     */
    int getTableScrollX(int tableIndex);

    /**
     * 记录指定表格的横向滚动偏移（像素），取值范围 [0, getScrollRange()]。
     */
    void setTableScrollX(int tableIndex, int scrollX);
}
