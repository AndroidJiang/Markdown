package com.fluid.markdown.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 行程规划汇总卡片（参考 egame ItineraryCardViewHolder）。
 * 汇聚本轮交通（火车票/机票）和住宿（酒店）数据，统一展示在回答底部。
 * 内部按区块分组：交通区块（火车 + 机票横向列表）、住宿区块（酒店横向列表）。
 */
public class ItineraryCardView extends LinearLayout {

    public ItineraryCardView(Context context) {
        super(context);
        init();
    }

    public ItineraryCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF7F8FA);
        bg.setCornerRadius(dp(12));
        setBackground(bg);
    }

    public void bind(List<TrainCardData> trains, List<FlightCardData> flights, List<HotelCardData> hotels) {
        removeAllViews();

        boolean hasTransport = (trains != null && !trains.isEmpty())
                || (flights != null && !flights.isEmpty());
        boolean hasHotel = hotels != null && !hotels.isEmpty();

        if (hasTransport) {
            addSectionTitle("交通方案");

            if (trains != null && !trains.isEmpty()) {
                addHorizontalCardGroup(trains, null, null);
            }
            if (flights != null && !flights.isEmpty()) {
                if (trains != null && !trains.isEmpty()) {
                    addView(createSpacer(dp(8)));
                }
                addHorizontalCardGroup(null, flights, null);
            }
        }

        if (hasHotel) {
            if (hasTransport) {
                addView(createSpacer(dp(12)));
            }
            addSectionTitle("住宿推荐");
            addHorizontalCardGroup(null, null, hotels);
        }
    }

    private void addSectionTitle(String title) {
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(0xFF333333);
        tv.getPaint().setFakeBoldText(true);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        addView(tv);
    }

    private void addHorizontalCardGroup(List<TrainCardData> trains,
                                        List<FlightCardData> flights,
                                        List<HotelCardData> hotels) {
        HorizontalScrollView hsv = new HorizontalScrollView(getContext());
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(OVER_SCROLL_NEVER);
        hsv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(HORIZONTAL);
        container.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        int cardWidth = calcCardWidth();

        if (trains != null) {
            for (int i = 0; i < trains.size(); i++) {
                if (i > 0) container.addView(createSpacer(dp(8)));
                container.addView(createTrainMiniCard(trains.get(i), cardWidth));
            }
        }
        if (flights != null) {
            for (int i = 0; i < flights.size(); i++) {
                if (i > 0) container.addView(createSpacer(dp(8)));
                container.addView(createFlightMiniCard(flights.get(i), cardWidth));
            }
        }
        if (hotels != null) {
            for (int i = 0; i < hotels.size(); i++) {
                if (i > 0) container.addView(createSpacer(dp(8)));
                container.addView(createHotelMiniCard(hotels.get(i), cardWidth));
            }
        }

        hsv.addView(container);
        addView(hsv);
    }

    /**
     * 卡片宽度：屏幕宽 - 左右内边距 - 露出量，使后续卡片可窥见。
     */
    private int calcCardWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = dp(12) * 2 + dp(14) * 2;
        int peek = dp(32);
        return screenWidth - padding - peek;
    }

    private View createTrainMiniCard(TrainCardData data, int width) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams lp = new LayoutParams(width, LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, 0xFFE8E8E8);
        card.setBackground(bg);

        TextView title = makeText(data.trainNo != null ? data.trainNo : "火车", 14, 0xFF333333, true);
        card.addView(title);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView from = makeText(data.from, 13, 0xFF666666, false);
        from.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.addView(from);

        TextView arrow = makeText(" → ", 13, 0xFF999999, false);
        row.addView(arrow);

        TextView to = makeText(data.to, 13, 0xFF666666, false);
        to.setGravity(Gravity.END);
        to.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.addView(to);
        card.addView(row);

        LinearLayout infoRow = new LinearLayout(getContext());
        infoRow.setOrientation(HORIZONTAL);
        LayoutParams infoLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(4);
        infoRow.setLayoutParams(infoLp);

        String timeInfo = (data.departTime != null ? data.departTime : "--")
                + " - " + (data.arriveTime != null ? data.arriveTime : "--");
        TextView time = makeText(timeInfo, 12, 0xFF999999, false);
        time.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        infoRow.addView(time);

        TextView price = makeText(data.price != null ? data.price : "", 13, 0xFFFF6600, true);
        infoRow.addView(price);
        card.addView(infoRow);

        return card;
    }

    private View createFlightMiniCard(FlightCardData data, int width) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams lp = new LayoutParams(width, LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, 0xFFE8E8E8);
        card.setBackground(bg);

        String titleText = (data.airline != null ? data.airline : "") + " " + (data.flightNo != null ? data.flightNo : "");
        TextView title = makeText(titleText.trim(), 14, 0xFF333333, true);
        card.addView(title);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);
        row.setLayoutParams(rowLp);

        TextView from = makeText(data.from, 13, 0xFF666666, false);
        from.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.addView(from);

        TextView arrow = makeText(" → ", 13, 0xFF999999, false);
        row.addView(arrow);

        TextView to = makeText(data.to, 13, 0xFF666666, false);
        to.setGravity(Gravity.END);
        to.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.addView(to);
        card.addView(row);

        LinearLayout infoRow = new LinearLayout(getContext());
        infoRow.setOrientation(HORIZONTAL);
        LayoutParams infoLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(4);
        infoRow.setLayoutParams(infoLp);

        String timeInfo = (data.departTime != null ? data.departTime : "--")
                + " - " + (data.arriveTime != null ? data.arriveTime : "--");
        TextView time = makeText(timeInfo, 12, 0xFF999999, false);
        time.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        infoRow.addView(time);

        TextView price = makeText(data.price != null ? data.price : "", 13, 0xFFFF6600, true);
        infoRow.addView(price);
        card.addView(infoRow);

        return card;
    }

    private View createHotelMiniCard(HotelCardData data, int width) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LayoutParams lp = new LayoutParams(width, LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, 0xFFE8E8E8);
        card.setBackground(bg);

        TextView title = makeText(data.name != null ? data.name : "酒店", 14, 0xFF333333, true);
        card.addView(title);

        if (data.location != null) {
            TextView loc = makeText(data.location, 12, 0xFF999999, false);
            LayoutParams locLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            locLp.topMargin = dp(4);
            loc.setLayoutParams(locLp);
            card.addView(loc);
        }

        LinearLayout bottomRow = new LinearLayout(getContext());
        bottomRow.setOrientation(HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams bottomLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bottomLp.topMargin = dp(6);
        bottomRow.setLayoutParams(bottomLp);

        String ratingText = String.format("★ %.1f", data.rating);
        TextView rating = makeText(ratingText, 12, 0xFFFF9900, false);
        rating.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        bottomRow.addView(rating);

        TextView price = makeText(data.price != null ? data.price : "", 13, 0xFFFF6600, true);
        bottomRow.addView(price);
        card.addView(bottomRow);

        if (data.tags != null && data.tags.length > 0) {
            LinearLayout tagRow = new LinearLayout(getContext());
            tagRow.setOrientation(HORIZONTAL);
            LayoutParams tagRowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            tagRowLp.topMargin = dp(6);
            tagRow.setLayoutParams(tagRowLp);

            for (String tag : data.tags) {
                if (tag == null || tag.trim().isEmpty()) continue;
                TextView tv = new TextView(getContext());
                tv.setText(tag.trim());
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                tv.setTextColor(0xFF1677FF);
                tv.setPadding(dp(6), dp(2), dp(6), dp(2));
                GradientDrawable tagBg = new GradientDrawable();
                tagBg.setColor(0xFFF0F6FF);
                tagBg.setCornerRadius(dp(8));
                tv.setBackground(tagBg);
                LayoutParams tagLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                tagLp.setMarginEnd(dp(4));
                tv.setLayoutParams(tagLp);
                tagRow.addView(tv);
            }
            card.addView(tagRow);
        }

        return card;
    }

    private TextView makeText(String text, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setText(text != null ? text : "");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        if (bold) tv.getPaint().setFakeBoldText(true);
        tv.setSingleLine(true);
        return tv;
    }

    private View createSpacer(int widthPx) {
        View v = new View(getContext());
        v.setLayoutParams(new LayoutParams(widthPx, 1));
        return v;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
