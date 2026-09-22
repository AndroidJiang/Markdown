package com.fluid.markdown.chat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 天气卡片视图。
 */
public class WeatherCardView extends LinearLayout {

    public WeatherCardView(Context context) {
        super(context);
        init();
    }

    public WeatherCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        setBackground(bg);

        LayoutInflater.from(getContext()).inflate(
                getResources().getIdentifier("view_weather_card_chat", "layout",
                        getContext().getPackageName()), this, true);
    }

    public void bind(WeatherCardData data) {
        setText(R_id("tv_w_city"), data.city);
        setText(R_id("tv_w_temp"), data.temp + "°");
        setText(R_id("tv_w_condition"), data.condition);
        setText(R_id("tv_w_emoji"), data.getEmoji());
        setText(R_id("tv_w_detail"), data.wind + "  |  湿度 " + data.humidity);

        // 渐变背景
        int[] colors = data.getGradientColors();
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, colors);
        bg.setCornerRadius(dp(12));
        setBackground(bg);
    }

    private void setText(int resId, String text) {
        TextView tv = findViewById(resId);
        if (tv != null && text != null) tv.setText(text);
    }

    private int R_id(String name) {
        return getResources().getIdentifier(name, "id", getContext().getPackageName());
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
