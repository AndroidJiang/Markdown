package com.fluid.markdown.chat;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 火车票卡片视图。
 */
public class TrainCardView extends LinearLayout {

    public TrainCardView(Context context) {
        super(context);
        init();
    }

    public TrainCardView(Context context, @Nullable AttributeSet attrs) {
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
                getResources().getIdentifier("view_train_card", "layout",
                        getContext().getPackageName()), this, true);
    }

    public void bind(TrainCardData data) {
        setText(R_id("tv_train_no"), data.trainNo);
        setText(R_id("tv_train_from"), data.from);
        setText(R_id("tv_train_to"), data.to);
        setText(R_id("tv_train_depart"), data.departTime);
        setText(R_id("tv_train_arrive"), data.arriveTime);
        setText(R_id("tv_train_duration"), data.duration);
        setText(R_id("tv_train_price"), data.price);
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
