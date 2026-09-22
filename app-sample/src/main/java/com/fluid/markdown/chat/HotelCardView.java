package com.fluid.markdown.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 酒店卡片视图（参考千问 MDFliggyHotelCardViewHolder 的卡片 UI）。
 * <p>
 * 白底圆角卡片，展示酒店名称、价格、评分、位置、标签。
 */
public class HotelCardView extends LinearLayout {

    public HotelCardView(Context context) {
        super(context);
        init();
    }

    public HotelCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFFE8E8E8);
        setBackground(bg);

        LayoutInflater.from(getContext()).inflate(
                getResources().getIdentifier("view_hotel_card", "layout",
                        getContext().getPackageName()),
                this, true);
    }

    public void bind(HotelCardData data) {
        setText(R_id("tv_hotel_name"), data.name);
        setText(R_id("tv_hotel_price"), data.price);
        setText(R_id("tv_hotel_rating"), String.format("★ %.1f", data.rating));
        setText(R_id("tv_hotel_location"), "📍 " + data.location);

        LinearLayout tagContainer = findViewById(R_id("tag_container"));
        if (tagContainer != null) {
            tagContainer.removeAllViews();
            if (data.tags != null) {
                for (String tag : data.tags) {
                    if (tag == null || tag.isEmpty()) continue;
                    TextView tv = new TextView(getContext());
                    tv.setText(tag);
                    tv.setTextSize(11);
                    tv.setTextColor(0xFF1677FF);
                    tv.setPadding(dp(8), dp(3), dp(8), dp(3));
                    GradientDrawable tagBg = new GradientDrawable();
                    tagBg.setColor(0xFFF0F6FF);
                    tagBg.setCornerRadius(dp(10));
                    tv.setBackground(tagBg);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    lp.setMarginEnd(dp(6));
                    tv.setLayoutParams(lp);
                    tagContainer.addView(tv);
                }
            }
        }
    }

    private void setText(int resId, String text) {
        TextView tv = findViewById(resId);
        if (tv != null && text != null) {
            tv.setText(text);
        }
    }

    private int R_id(String name) {
        return getResources().getIdentifier(name, "id", getContext().getPackageName());
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
