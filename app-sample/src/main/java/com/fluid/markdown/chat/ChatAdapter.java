package com.fluid.markdown.chat;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;
import com.fluid.afm.styles.MarkdownStyles;

import java.util.ArrayList;
import java.util.List;

/**
 * 扁平化聊天 Adapter（参考 egame ChatAdapter）。
 * <p>
 * 架构：
 * - 外层只有三种 Row：OwnerRow（用户消息）、AnswerRow（回答 item）、LoadingRow
 * - 每个 AnswerEntry 持有一个 AnswerCardAdapter（仅用作回调中转）
 * - 逐段同步添加 row 到 rows 列表，不经过异步 submitList
 * - 头像只在每轮回答的首个 AnswerRow 上方显示（通过 margin 控制）
 * - 气泡背景通过 AnswerBgDecoration 绘制，同组 AnswerRow 共用 entryId 形成连续圆角
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VT_USER = 10_001;
    private static final int VT_LOADING = 10_002;

    // ======== Entry / Row ========

    private interface Entry {
        long getEntryId();
    }

    private static class OwnerEntry implements Entry {
        final long entryId;
        final String text;
        OwnerEntry(long entryId, String text) { this.entryId = entryId; this.text = text; }
        @Override public long getEntryId() { return entryId; }
    }

    private static class AnswerEntry implements Entry {
        final long entryId;
        final AnswerCardAdapter adapter;
        List<ChatItem> items = new ArrayList<>();
        /** 数据源一次性解析出的全部 item（显示前全量就绪，与打字机解耦） */
        List<ChatItem> pendingItems = new ArrayList<>();
        /** 下一个待放行显示的 item 下标 */
        int showIndex = 0;
        /** 打字机等待中：流式文本段放行后置位、自然打完复位；期间新数据只入列不放行 */
        boolean waitingTypewriter;
        AnswerEntry(long entryId, AnswerCardAdapter adapter) { this.entryId = entryId; this.adapter = adapter; }
        @Override public long getEntryId() { return entryId; }
    }

    /** 流式回答句柄：配合 beginStream / appendStreamText / appendStreamCard 按真实时间轴注入数据。 */
    public static class StreamHandle {
        final AnswerEntry entry;
        /** 当前正在增长的文本段（卡片之后的新文本 delta 会开新段） */
        ChatItem.TextItem currentText;
        /**
         * 行程规划模式（参考 egame TravelCardParser.isItineraryMode）：
         * 卡片数据不穿插到文本中，而是累积在此列表，endStream 时统一生成 ItineraryCardItem 插入底部。
         */
        boolean itineraryMode;
        final List<TrainCardData> pendingTrains = new ArrayList<>();
        final List<FlightCardData> pendingFlights = new ArrayList<>();
        final List<HotelCardData> pendingHotels = new ArrayList<>();
        StreamHandle(AnswerEntry entry) { this.entry = entry; }
    }

    private static class LoadingEntry implements Entry {
        final long entryId;
        LoadingEntry(long entryId) { this.entryId = entryId; }
        @Override public long getEntryId() { return entryId; }
    }

    private interface Row {
        long getStableId();
    }

    private static class OwnerRow implements Row {
        final long entryId;
        final String text;
        OwnerRow(long entryId, String text) { this.entryId = entryId; this.text = text; }
        @Override public long getStableId() { return stableId("owner:" + entryId); }
    }

    private static class AnswerRow implements Row {
        final long entryId;
        final AnswerCardAdapter adapter;
        final int localPosition;
        final int localItemCount;
        final ChatItem item;
        AnswerRow(long entryId, AnswerCardAdapter adapter, int localPosition, int localItemCount, ChatItem item) {
            this.entryId = entryId; this.adapter = adapter;
            this.localPosition = localPosition; this.localItemCount = localItemCount; this.item = item;
        }
        @Override public long getStableId() {
            return stableId("answer:" + entryId + ":" + item.getClass().getSimpleName() + ":" + item.getId());
        }
    }

    private static class LoadingRow implements Row {
        final long entryId;
        LoadingRow(long entryId) { this.entryId = entryId; }
        @Override public long getStableId() { return stableId("loading:" + entryId); }
    }

    // ======== Adapter ========

    private final List<Entry> entries = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long nextEntryId = 0;
    private final ElementClickEventCallback callback;
    private ChatRecyclerView chatRV;
    private Drawable answerBgDrawable;
    private int answerBgMarginDp = 0; // 左右边距，0 = 全宽

    public ChatAdapter(ElementClickEventCallback callback) {
        this.callback = callback;
        setHasStableIds(true);
    }

    public void setChatRecyclerView(ChatRecyclerView rv) {
        this.chatRV = rv;
    }

    public void setAnswerBackgroundDrawable(Drawable drawable) {
        this.answerBgDrawable = drawable;
    }

    /**
     * 添加用户消息。
     */
    public void addUserMessage(String text) {
        OwnerEntry entry = new OwnerEntry(nextEntryId++, text);
        entries.add(entry);
        int pos = rows.size();
        rows.add(new OwnerRow(entry.entryId, text));
        notifyItemInserted(pos);
    }

    /**
     * 添加 AI 回复。
     * <p>
     * 数据源与显示节奏解耦：
     * - 数据源：全文一次性解析并构建全部 pendingItems（与打字机无关，数据即刻全部就绪）；
     * - 显示节奏：从 pendingItems 逐个放行——文本段打字机打完（onPrintStop）才放行下一个，
     *   卡片段放行后立即放行下一个，保持"文本打完 → 卡片出现 → 下一段继续打字机"的效果。
     */
    public void addAIResponse(String fullResponse) {
        AnswerEntry entry = createAnswerEntry();

        // ===== 数据源：一次性全量解析，与打字机完全解耦 =====
        List<ChatMessage.Segment> segments = ChatMessage.parseSegments(fullResponse);
        for (int i = 0; i < segments.size(); i++) {
            ChatMessage.Segment seg = segments.get(i);
            String itemId = "seg_" + i;
            if (seg.type == ChatMessage.Segment.TYPE_TEXT) {
                if (seg.content == null || seg.content.isEmpty()) continue;
                entry.pendingItems.add(new ChatItem.TextItem(itemId, seg.content, true));
            } else {
                ChatItem cardItem;
                switch (seg.type) {
                    case ChatMessage.Segment.TYPE_HOTEL_CARD:
                        cardItem = new ChatItem.HotelCardItem(itemId, seg.content);
                        break;
                    case ChatMessage.Segment.TYPE_TRAIN_CARD:
                        cardItem = new ChatItem.TrainCardItem(itemId, seg.content);
                        break;
                    case ChatMessage.Segment.TYPE_FLIGHT_CARD:
                        cardItem = new ChatItem.FlightCardItem(itemId, seg.content);
                        break;
                    case ChatMessage.Segment.TYPE_WEATHER_CARD:
                        cardItem = new ChatItem.WeatherCardItem(itemId, seg.content);
                        break;
                    default:
                        cardItem = new ChatItem.TextItem(itemId, seg.content, true);
                        break;
                }
                entry.pendingItems.add(cardItem);
            }
        }

        // ===== 显示节奏：从数据源逐个放行 =====
        releaseNext(entry);
    }

    /**
     * 创建回答 Entry（addAIResponse 全量路径与 beginStream 流式路径共用）：
     * - 高度变化 → 外层 RV 滚动；
     * - 文本段打字机自然打完 → 解除等待、放行下一个 item。
     * <p>
     * 推进必须 post：onPrintStop 可能发生在 payload 增量绑定的同步调用栈中
     * （appendPrinting 恰好打完），此时直接 notifyItemInserted 会抛
     * "Cannot call this method while RecyclerView is computing a layout"。
     */
    private AnswerEntry createAnswerEntry() {
        AnswerCardAdapter answerAdapter = new AnswerCardAdapter(callback);
        answerAdapter.setOnHeightChangedListener(() -> {
            if (chatRV != null) chatRV.requestScrollToBottom();
        });
        AnswerEntry entry = new AnswerEntry(nextEntryId++, answerAdapter);
        answerAdapter.setOnShowNextListener(() -> {
            entry.waitingTypewriter = false;
            mainHandler.post(() -> releaseNext(entry));
        });
        entries.add(entry);
        return entry;
    }

    // ======== 流式回答 API（mock 真实 SSE/WebSocket 下发时间轴） ========

    /**
     * 开始一段流式回答：数据通过 appendStreamText / appendStreamCard 按真实节奏分批到达，
     * 显示时机仍由闸门控制（流式文本段打完才放行下一个 item）。
     */
    public StreamHandle beginStream() {
        return new StreamHandle(createAnswerEntry());
    }

    /**
     * 追加一段文本 delta：
     * - 当前无增长中的文本段：新建 TextItem 入列并尝试放行显示；
     * - 已有：追加到该段，若该段已放行显示则 payload 增量刷新（打字机续打），未放行仅更新数据。
     */
    public void appendStreamText(StreamHandle handle, String delta) {
        if (handle == null || delta == null || delta.isEmpty()) return;
        AnswerEntry entry = handle.entry;
        if (handle.currentText == null) {
            ChatItem.TextItem textItem = new ChatItem.TextItem(
                    "seg_" + entry.pendingItems.size(), delta, true);
            entry.pendingItems.add(textItem);
            handle.currentText = textItem;
        } else {
            handle.currentText.text += delta;
            notifyTextItemChanged(entry, handle.currentText);
        }
        releaseNext(entry);
    }

    /**
     * 设置行程规划模式（参考 egame onItineraryDetected）。
     * 开启后，后续 appendStreamCard 的出行卡片（酒店/火车/机票）不穿插到文本中，
     * 而是累积到 handle 内部，在 endStream 时统一生成 ItineraryCardItem 插入底部。
     */
    public void setItineraryMode(StreamHandle handle, boolean enabled) {
        if (handle != null) handle.itineraryMode = enabled;
    }

    /**
     * 追加一张卡片。
     * <p>
     * 行程规划模式下（参考 egame TravelAdapterSetupHelper.setupTravelParsers）：
     * 酒店/火车/机票卡片数据累积到 handle，不立即显示，endStream 时统一汇总插入底部。
     * 天气等非出行卡片仍然正常穿插显示。
     * <p>
     * 普通模式下：卡片数据到达即入列，显示时机由闸门决定。
     */
    public void appendStreamCard(StreamHandle handle, ChatItem cardItem) {
        if (handle == null || cardItem == null) return;

        if (handle.itineraryMode) {
            if (cardItem instanceof ChatItem.HotelCardItem) {
                handle.pendingHotels.add(HotelCardData.fromJson(((ChatItem.HotelCardItem) cardItem).json));
                return;
            } else if (cardItem instanceof ChatItem.TrainCardItem) {
                handle.pendingTrains.add(TrainCardData.fromJson(((ChatItem.TrainCardItem) cardItem).json));
                return;
            } else if (cardItem instanceof ChatItem.FlightCardItem) {
                handle.pendingFlights.add(FlightCardData.fromJson(((ChatItem.FlightCardItem) cardItem).json));
                return;
            }
        }

        AnswerEntry entry = handle.entry;
        entry.pendingItems.add(cardItem);
        handle.currentText = null;
        releaseNext(entry);
    }

    /**
     * 结束当前增长的文本段（下次 appendStreamText 会新建独立文本段）。
     * <p>
     * 用于流式数据源按"完整段落块"注入的场景：每块都是完整 markdown 结构，
     * 每个文本段用 startPrinting 一次性渲染，规避 appendPrinting 全量重解析
     * 在结构闭合瞬间产生的高度突变。
     */
    public void endStreamText(StreamHandle handle) {
        if (handle == null) return;
        handle.currentText = null;
    }

    /**
     * 结束流式回答。
     * <p>
     * 行程规划模式下：将本轮累积的出行卡片数据汇聚为一个 ItineraryCardItem，
     * 加入 pendingItems 尾部，由闸门控制显示时机——
     * 前面的文本段打字机全部打完后，ItineraryCardItem 才会出现在底部。
     */
    public void endStream(StreamHandle handle) {
        if (handle == null) return;
        if (handle.itineraryMode) {
            boolean hasData = !handle.pendingTrains.isEmpty()
                    || !handle.pendingFlights.isEmpty()
                    || !handle.pendingHotels.isEmpty();
            if (hasData) {
                ChatItem.ItineraryCardItem itineraryItem = new ChatItem.ItineraryCardItem(
                        "itinerary_" + System.currentTimeMillis(),
                        new ArrayList<>(handle.pendingTrains),
                        new ArrayList<>(handle.pendingFlights),
                        new ArrayList<>(handle.pendingHotels)
                );
                handle.entry.pendingItems.add(itineraryItem);
                handle.currentText = null;
                releaseNext(handle.entry);
            }
        }
    }

    /** 已放行显示的文本段数据变化 → payload 增量刷新；未放行的无需刷新。 */
    private void notifyTextItemChanged(AnswerEntry entry, ChatItem.TextItem textItem) {
        int local = entry.items.indexOf(textItem);
        if (local < 0) return;
        int entryIndex = entries.indexOf(entry);
        if (entryIndex < 0) return;
        int pos = entryStartPosition(entryIndex) + local;
        if (pos >= 0 && pos < rows.size()) {
            notifyItemChanged(pos, "PAYLOAD_TEXT_UPDATE");
        }
    }

    /**
     * 放行显示下一个 item（数据源全量就绪，此处只控制显示时序）：
     * - 文本段放行后暂停，等打字机自然打完（onPrintStop → setOnShowNextListener）再放行下一个；
     * - 卡片段放行后立即放行下一个。
     */
    private void releaseNext(AnswerEntry entry) {
        if (entry == null || entry.showIndex >= entry.pendingItems.size()) return;
        // 打字机等待中：流式数据到达只入列，不提前放行（保持"文本打完→卡片出现"）
        if (entry.waitingTypewriter) return;

        ChatItem item = entry.pendingItems.get(entry.showIndex);
        if (item instanceof ChatItem.TextItem) {
            ChatItem.TextItem textItem = (ChatItem.TextItem) item;
            if (textItem.text == null || textItem.text.isEmpty()) {
                entry.showIndex++;
                releaseNext(entry);
                return;
            }
        }

        addAnswerRow(entry, item);
        entry.showIndex++;

        if (item instanceof ChatItem.TextItem && ((ChatItem.TextItem) item).isStreaming) {
            // 流式文本段：进入等待，等打字机自然打完（onPrintStop → notifyShowNext）再放行下一个
            entry.waitingTypewriter = true;
        } else {
            // 卡片等非流式 item：立即放行下一个
            mainHandler.post(() -> releaseNext(entry));
        }
    }

    /** 直接向 rows 列表末尾添加一个 AnswerRow 并通知 RecyclerView。 */
    private void addAnswerRow(AnswerEntry entry, ChatItem item) {
        int entryIndex = entries.indexOf(entry);
        entry.items.add(item);
        int localIdx = entry.items.size() - 1;
        int insertPos = entryStartPosition(entryIndex) + localIdx;
        AnswerRow row = new AnswerRow(entry.entryId, entry.adapter,
                localIdx, entry.items.size(), item);
        rows.add(insertPos, row);
        notifyItemInserted(insertPos);
        Log.d("SCROLL_DBG", "addAnswerRow: item=" + item.getId() + " insertPos=" + insertPos + " -> requestScroll");
        if (chatRV != null) chatRV.requestScrollToBottom();
    }

    private int entryStartPosition(int entryIndex) {
        int pos = 0;
        for (int i = 0; i < entryIndex; i++) {
            Entry e = entries.get(i);
            if (e instanceof OwnerEntry || e instanceof LoadingEntry) pos += 1;
            else if (e instanceof AnswerEntry) pos += ((AnswerEntry) e).items.size();
        }
        return pos;
    }

    // ======== RecyclerView.Adapter ========

    @Override
    public int getItemCount() { return rows.size(); }

    @Override
    public long getItemId(int position) { return rows.get(position).getStableId(); }

    @Override
    public int getItemViewType(int position) {
        Row row = rows.get(position);
        if (row instanceof OwnerRow) return VT_USER;
        if (row instanceof LoadingRow) return VT_LOADING;
        if (row instanceof AnswerRow) return ((AnswerRow) row).item.getViewType();
        return 0;
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
        }
        if (viewType == VT_LOADING) {
            TextView tv = new TextView(parent.getContext());
            tv.setText("思考中...");
            tv.setPadding(dp(parent, 16), dp(parent, 8), dp(parent, 16), dp(parent, 8));
            return new LoadingVH(tv);
        }

        // Answer item：用 item_chat_answer 布局
        View view = inflater.inflate(
                parent.getContext().getResources().getIdentifier("item_chat_answer", "layout", pkg),
                parent, false);
        return new AnswerVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row instanceof OwnerRow) {
            ((UserVH) holder).bind(((OwnerRow) row).text);
        } else if (row instanceof AnswerRow) {
            AnswerRow ar = (AnswerRow) row;
            // 间距控制：首项有头像 + topMargin，其余项无
            applyAnswerItemSpacing(holder, ar.localPosition, ar.localItemCount);
            // 委托给 AnswerCardAdapter 的 onBindViewHolder
            ((AnswerVH) holder).bind(ar, callback);
        }
    }

    /** payload 增量绑定：流式文本段 delta 续打，其余兜底全量 bind。 */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        Row row = rows.get(position);
        if (row instanceof AnswerRow) {
            AnswerRow ar = (AnswerRow) row;
            applyAnswerItemSpacing(holder, ar.localPosition, ar.localItemCount);
            ((AnswerVH) holder).bindIncremental(ar, callback);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    /**
     * 回答 item 间距（参考 egame AnswerItemUiHelper）。
     * 首项显示头像 + topMargin，其余项 topMargin=0 无头像。
     */
    private void applyAnswerItemSpacing(RecyclerView.ViewHolder holder, int localPosition, int localItemCount) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (localPosition == 0) {
            lp.topMargin = dp(holder.itemView, 16);
        } else {
            lp.topMargin = 0;
        }
        lp.bottomMargin = 0;
        holder.itemView.setLayoutParams(lp);

        // 控制头像可见性
        AnswerVH avh = (AnswerVH) holder;
        avh.avatarContainer.setVisibility(localPosition == 0 ? View.VISIBLE : View.GONE);
    }

    // ======== ViewHolders ========

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView textView;
        UserVH(@NonNull View itemView) {
            super(itemView);
            int id = itemView.getContext().getResources().getIdentifier(
                    "tv_user_message", "id", itemView.getContext().getPackageName());
            textView = itemView.findViewById(id);
        }
        void bind(String text) { textView.setText(text); }
    }

    static class LoadingVH extends RecyclerView.ViewHolder {
        LoadingVH(@NonNull View itemView) { super(itemView); }
    }

    /**
     * 回答 item ViewHolder。
     * 布局包含：头像区（首项可见）+ 内容容器（交给 AnswerCardAdapter 渲染）。
     */
    static class AnswerVH extends RecyclerView.ViewHolder {
        final View avatarContainer;
        final LinearLayout contentContainer;
        /** 当前 holder 绑定的文本段（增量续打时校验归属，防止复用串段） */
        private ChatItem.TextItem boundTextItem;

        AnswerVH(@NonNull View itemView) {
            super(itemView);
            String pkg = itemView.getContext().getPackageName();
            int avatarId = itemView.getContext().getResources().getIdentifier("avatar_container", "id", pkg);
            int containerId = itemView.getContext().getResources().getIdentifier("content_container", "id", pkg);
            avatarContainer = itemView.findViewById(avatarId);
            contentContainer = itemView.findViewById(containerId);
        }

        void bind(AnswerRow row, ElementClickEventCallback callback) {
            ChatItem item = row.item;
            boundTextItem = item instanceof ChatItem.TextItem ? (ChatItem.TextItem) item : null;

            contentContainer.removeAllViews();

            if (item instanceof ChatItem.TextItem) {
                PrinterMarkDownTextView tv = new PrinterMarkDownTextView(itemView.getContext());
                MarkdownStyles styles = MarkdownStyles.getDefaultStyles();
                tv.init(styles, callback);
                tv.setPrintParams(25, 1);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tv.setLayoutParams(lp);
                contentContainer.addView(tv);

                ChatItem.TextItem textItem = (ChatItem.TextItem) item;
                if (!textItem.isStreaming && textItem.printData != null
                        && textItem.printData.showingText != null) {
                    // 已完成打印且有缓存：直接 restore（不先 setPrintData，否则 restore 会跳过）
                    tv.restore(textItem.printData);
                } else if (!textItem.isStreaming) {
                    // 已完成但无缓存（历史 item 等）：直接 setMarkdownText
                    tv.setMarkdownText(textItem.text);
                    tv.post(() -> tv.setMinHeight(0));
                } else {
                    // 流式打印（printData 记录有进度时从上次位置续打，滚动回收复用不重头）
                    if (textItem.printData == null) {
                        textItem.printData = new PrinterMarkDownTextView.MarkDownPrintData();
                    }
                    tv.setPrintData(textItem.printData);
                    int resumeIndex = textItem.printData.currentIndex > 0
                            ? textItem.printData.currentIndex : 0;
                    tv.startPrinting(textItem.text, resumeIndex);
                    tv.setPrintingEventListener(new PrinterMarkDownTextView.PrintingEventListener() {
                        @Override public void onPrintStart() {}
                        @Override public void onPrintStop(boolean printAll) {
                            // 非自然完成（holder 复用被 stopPrinting）不算该段打完
                            if (textItem.printData != null && textItem.printData.isStopByUser) return;
                            textItem.isStreaming = false;
                            // 保存 showingText 供复用时 restore
                            if (textItem.printData != null && textItem.printData.parsedMarkdownText != null) {
                                textItem.printData.showingText = textItem.printData.parsedMarkdownText;
                            }
                            // 文本段显示完成 → 放行下一个 item（数据源已全量就绪，仅控制显示时序）
                            row.adapter.notifyShowNext();
                            row.adapter.notifyTextContentHeightChanged();
                        }
                        @Override public void onPrintPaused(int index) {}
                        @Override public void onPrintResumed() {}
                    });
                    tv.setSizeChangedListener((width, height) -> {
                        // 通知外层 RV 滚动
                        View p = itemView;
                        while (p != null && !(p.getParent() instanceof ChatRecyclerView)) {
                            p = (View) p.getParent();
                        }
                        if (p != null && p.getParent() instanceof ChatRecyclerView) {
                            ((ChatRecyclerView) p.getParent()).requestScrollToBottom();
                        }
                    });
                }
            } else {
                // 卡片
                if (item instanceof ChatItem.ItineraryCardItem) {
                    ChatItem.ItineraryCardItem itinerary = (ChatItem.ItineraryCardItem) item;
                    ItineraryCardView v = new ItineraryCardView(itemView.getContext());
                    v.bind(itinerary.trains, itinerary.flights, itinerary.hotels);
                    contentContainer.addView(v);
                } else if (item instanceof ChatItem.HotelCardItem) {
                    HotelCardView v = new HotelCardView(itemView.getContext());
                    v.bind(HotelCardData.fromJson(((ChatItem.HotelCardItem) item).json));
                    contentContainer.addView(v);
                } else if (item instanceof ChatItem.TrainCardItem) {
                    TrainCardView v = new TrainCardView(itemView.getContext());
                    v.bind(TrainCardData.fromJson(((ChatItem.TrainCardItem) item).json));
                    contentContainer.addView(v);
                } else if (item instanceof ChatItem.FlightCardItem) {
                    FlightCardView v = new FlightCardView(itemView.getContext());
                    v.bind(FlightCardData.fromJson(((ChatItem.FlightCardItem) item).json));
                    contentContainer.addView(v);
                } else if (item instanceof ChatItem.WeatherCardItem) {
                    WeatherCardView v = new WeatherCardView(itemView.getContext());
                    v.bind(WeatherCardData.fromJson(((ChatItem.WeatherCardItem) item).json));
                    contentContainer.addView(v);
                }
            }
        }

        /**
         * payload 增量绑定：同一流式文本段的新 delta 从当前打印进度续打（replace 全文模式），
         * 其余情况兜底全量 bind。
         * 打字机曾"暂时追平"（isStreaming 被提前置 false）时，delta 到达即恢复流式态自愈；
         * 真正完成的段不再有数据，不会被触发。
         */
        void bindIncremental(AnswerRow row, ElementClickEventCallback callback) {
            ChatItem item = row.item;
            if (boundTextItem == item && item instanceof ChatItem.TextItem
                    && contentContainer.getChildCount() == 1
                    && contentContainer.getChildAt(0) instanceof PrinterMarkDownTextView) {
                ChatItem.TextItem textItem = (ChatItem.TextItem) item;
                PrinterMarkDownTextView tv = (PrinterMarkDownTextView) contentContainer.getChildAt(0);
                if (tv.isStarted()) {
                    textItem.isStreaming = true;
                    tv.appendPrinting(textItem.text, false);
                    return;
                }
            }
            bind(row, callback);
        }
    }

    // ======== ItemDecoration：连续圆角背景 ========

    /**
     * 回答背景 ItemDecoration（参考 egame ConcatAdapterBackgroundDecoration）。
     * 同一 entryId 的 AnswerRow 形成连续圆角背景块。
     */
    public static class AnswerBgDecoration extends RecyclerView.ItemDecoration {
        private final Drawable background;
        private final ChatAdapter adapter;
        private final int marginPx;

        public AnswerBgDecoration(Drawable background, ChatAdapter adapter, int marginDp) {
            this.background = background;
            this.adapter = adapter;
            this.marginPx = (int) (marginDp * background.getBounds().width() / background.getIntrinsicWidth());
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (background == null) return;

            float density = parent.getContext().getResources().getDisplayMetrics().density;
            int baseMargin = (int) (0 * density); // 左右边距 0
            int padTop = (int) (6 * density);
            int padBottom = (int) (6 * density);

            Long currentGroupKey = null;
            int groupLeft = Integer.MAX_VALUE, groupTop = Integer.MAX_VALUE;
            int groupRight = Integer.MIN_VALUE, groupBottom = Integer.MIN_VALUE;

            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (pos < 0) continue;

                Row row = adapter.rows.get(pos);
                if (!(row instanceof AnswerRow)) {
                    // 非回答行，切断背景
                    drawGroup(c, groupLeft, groupTop, groupRight, groupBottom, baseMargin, padTop, padBottom);
                    groupLeft = Integer.MAX_VALUE; groupTop = Integer.MAX_VALUE;
                    groupRight = Integer.MIN_VALUE; groupBottom = Integer.MIN_VALUE;
                    currentGroupKey = null;
                    continue;
                }

                AnswerRow ar = (AnswerRow) row;
                long groupKey = ar.entryId;

                if (currentGroupKey != null && currentGroupKey != groupKey) {
                    drawGroup(c, groupLeft, groupTop, groupRight, groupBottom, baseMargin, padTop, padBottom);
                    groupLeft = Integer.MAX_VALUE; groupTop = Integer.MAX_VALUE;
                    groupRight = Integer.MIN_VALUE; groupBottom = Integer.MIN_VALUE;
                }

                currentGroupKey = groupKey;
                groupLeft = Math.min(groupLeft, child.getLeft());
                groupTop = Math.min(groupTop, child.getTop());
                groupRight = Math.max(groupRight, child.getRight());
                groupBottom = Math.max(groupBottom, child.getBottom());
            }
            drawGroup(c, groupLeft, groupTop, groupRight, groupBottom, baseMargin, padTop, padBottom);
        }

        private void drawGroup(Canvas c, int left, int top, int right, int bottom, int margin, int padTop, int padBottom) {
            if (left == Integer.MAX_VALUE) return;
            background.setBounds(left + margin, top - padTop, right - margin, bottom + padBottom);
            background.draw(c);
        }
    }

    // ======== Utils ========

    private static long stableId(String value) {
        long result = 1469598103934665603L;
        for (int i = 0; i < value.length(); i++) {
            result ^= value.charAt(i);
            result *= 1099511628211L;
        }
        return result;
    }

    private int dp(View view, float v) {
        return (int) (v * view.getContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private int dp(ViewGroup parent, float v) {
        return (int) (v * parent.getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
