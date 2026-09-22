package com.fluid.afm.markdown.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import android.text.style.LineHeightSpan;
import android.text.style.ReplacementSpan;

import com.fluid.afm.ContextHolder;
import com.fluid.afm.R;
import com.fluid.afm.utils.Utils;

import io.noties.markwon.utils.SpanUtils;

/**
 * 天气卡片 ReplacementSpan，通过 inflate XML 布局的方式渲染天气卡片。
 */
public class WeatherCardSpan extends ReplacementSpan implements LineHeightSpan {

    private static final int CARD_CORNER = Utils.dpToPx(12f);

    private final String mCity;
    private final String mTemp;
    private final String mCondition;
    private final String mWind;
    private final String mHumidity;
    private final String mEmoji;
    private final int[] mGradientColors;

    private int mCardWidth;
    private int mCardHeight;
    private View mCardView;

    public WeatherCardSpan(String city, String temp, String condition,
                           String wind, String humidity) {
        mCity = city != null ? city : "未知城市";
        mTemp = temp != null ? temp : "--";
        mCondition = condition != null ? condition : "未知";
        mWind = wind != null ? wind : "";
        mHumidity = humidity != null ? humidity : "";
        mEmoji = resolveEmoji(mCondition);
        mGradientColors = resolveGradient(mCondition);
    }

    private void ensureCardView() {
        if (mCardView != null) return;

        Context context = ContextHolder.getContext();
        if (context == null) return;

        mCardView = LayoutInflater.from(context).inflate(R.layout.layout_weather_card, null);

        TextView tvCity = mCardView.findViewById(R.id.tv_city);
        TextView tvCondition = mCardView.findViewById(R.id.tv_condition);
        TextView tvDetail = mCardView.findViewById(R.id.tv_detail);
        TextView tvTemp = mCardView.findViewById(R.id.tv_temp);
        TextView tvEmoji = mCardView.findViewById(R.id.tv_emoji);

        tvCity.setText(mCity);
        tvCondition.setText(mCondition);
        tvTemp.setText(mTemp + "°");
        tvEmoji.setText(mEmoji);

        String detailText = "";
        if (!mWind.isEmpty()) detailText += mWind;
        if (!mWind.isEmpty() && !mHumidity.isEmpty()) detailText += "  |  ";
        if (!mHumidity.isEmpty()) detailText += "湿度 " + mHumidity;
        if (detailText.isEmpty()) {
            tvDetail.setVisibility(View.GONE);
        } else {
            tvDetail.setText(detailText);
        }

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, mGradientColors);
        bg.setCornerRadius(CARD_CORNER);
        mCardView.setBackground(bg);

        mCardWidth = Utils.getScreenWidth() - Utils.dpToPx(32f);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(mCardWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        mCardView.measure(widthSpec, heightSpec);
        mCardHeight = mCardView.getMeasuredHeight();
        mCardView.layout(0, 0, mCardWidth, mCardHeight);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        ensureCardView();

        if (fm != null) {
            fm.ascent = -mCardHeight;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }

        return mCardWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        ensureCardView();
        if (mCardView == null) return;

        canvas.save();
        canvas.translate(x, y - mCardHeight);
        mCardView.draw(canvas);
        canvas.restore();
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end,
                             int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
        if (fm == null) return;
        if (SpanUtils.isSelf(start, end, text, this)) {
            fm.ascent = -mCardHeight;
            fm.descent = Utils.dpToPx(4f);
            fm.top = fm.ascent;
            fm.bottom = fm.descent;
        }
    }

    private String resolveEmoji(String condition) {
        if (condition == null) return "\u2601";
        if (condition.contains("晴")) return "\u2600\uFE0F";
        if (condition.contains("云") || condition.contains("阴")) return "\u26C5";
        if (condition.contains("雨")) return "\uD83C\uDF27\uFE0F";
        if (condition.contains("雪")) return "\u2744\uFE0F";
        if (condition.contains("雷")) return "\u26A1";
        if (condition.contains("雾") || condition.contains("霾")) return "\uD83C\uDF2B\uFE0F";
        return "\u2601";
    }

    private int[] resolveGradient(String condition) {
        if (condition == null) return new int[]{0xFF4FC3F7, 0xFF0288D1};
        if (condition.contains("晴"))
            return new int[]{0xFFFF8A65, 0xFFFFA726, 0xFFFFCA28};
        if (condition.contains("云") || condition.contains("阴"))
            return new int[]{0xFF90A4AE, 0xFF78909C, 0xFF607D8B};
        if (condition.contains("雨"))
            return new int[]{0xFF4FC3F7, 0xFF0288D1, 0xFF01579B};
        if (condition.contains("雪"))
            return new int[]{0xFFE1F5FE, 0xFFB3E5FC, 0xFF81D4FA};
        if (condition.contains("雷"))
            return new int[]{0xFF5C6BC0, 0xFF3949AB, 0xFF283593};
        if (condition.contains("雾") || condition.contains("霾"))
            return new int[]{0xFFBDBDBD, 0xFF9E9E9E, 0xFF757575};
        return new int[]{0xFF4FC3F7, 0xFF0288D1, 0xFF0277BD};
    }
}
