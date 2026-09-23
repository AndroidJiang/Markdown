package com.fluid.markdown.chat;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fluid.afm.AFMInitializer;
import com.fluid.afm.ContextHolder;
import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.html.SpanTextClickableSpan;
import com.fluid.afm.markdown.model.EventModel;

import java.util.List;
import java.util.Map;

/**
 * AI 对话页（模拟千问聊天主界面）。
 * <p>
 * 架构（参考 egame_cloud_phone 扁平化）：
 * - 只有一个 ChatRecyclerView，所有 item 在同一层
 * - 外层 ChatAdapter 扁平化展开 AnswerCardAdapter 的 item
 * - 头像只在每轮回答首个 item 上方显示
 * - 气泡背景通过 ItemDecoration 绘制，同组共用连续圆角
 * - 流式打印时高度变化直接驱动 RV 滚动
 */
public class ChatActivity extends AppCompatActivity {

    private ChatRecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText inputEdit;
    private TextView sendButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId("activity_chat"));

        if (ContextHolder.getContext() == null) {
            AFMInitializer.init(this, null, null, null);
        }

        initViews();
        initRecyclerView();
        handler.postDelayed(this::sendInitialMessage, 300);
    }

    private void initViews() {
        recyclerView = findViewById(getResourceId("chat_recycler_view"));
        inputEdit = getResourceId("et_input") == 0 ? null : findViewById(getResourceId("et_input"));
        sendButton = findViewById(getResourceId("btn_send"));
        findViewById(getResourceId("chat_back")).setOnClickListener(v -> finish());
        sendButton.setOnClickListener(v -> handleSend());
        if (inputEdit != null) {
            inputEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    sendButton.setEnabled(s.length() > 0);
                    sendButton.setAlpha(s.length() > 0 ? 1.0f : 0.4f);
                }
            });
        }
    }

    private void initRecyclerView() {
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);

        // 流式场景禁用 item 动画：item 插入/更新（打字机段落推进）直接呈现，
        // 避免 DefaultItemAnimator 的淡入/位移动画与打字机滚动叠加造成视觉干扰
        recyclerView.setItemAnimator(null);

        adapter = new ChatAdapter(new ElementClickEventCallback() {
            @Override public boolean onLinkClicked(Map<String, Object> params) { return false; }
            @Override public void onFootnoteClicked(String index) {}
            @Override public void onImageClicked(String url, String description) {}
            @Override public boolean onTextClickableSpanClicked(View widget, String link, String entityID,
                                                               SpanTextClickableSpan.ClickableTextType type) { return false; }
            @Override public void exposureSpmBehavior(List<EventModel> models) {}
        });
        adapter.setChatRecyclerView(recyclerView);

        // 气泡背景（圆角白色）
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, 0xFFEAEAEA);
        adapter.setAnswerBackgroundDrawable(bg);

        recyclerView.setAdapter(adapter);

        // 添加背景 ItemDecoration
        recyclerView.addItemDecoration(new ChatAdapter.AnswerBgDecoration(bg, adapter, 0));
    }

    private void sendInitialMessage() {
        adapter.addUserMessage("帮我规划明天杭州出行");
        handler.postDelayed(() -> {
            adapter.addAIResponse(MockSSESource.buildTravelPlan());
            recyclerView.forceScrollToBottom();
        }, 500);
    }

    private void handleSend() {
        String text = inputEdit != null ? inputEdit.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        adapter.addUserMessage(text);
        inputEdit.setText("");

        handler.postDelayed(() -> {
            if (containsAny(text, "行程规划", "规划行程", "一日游", "1日游", "一日行程")) {
                // 行程规划：mock 真实 WebSocket 下发时间轴（数据分批到达，显示由闸门控节奏）
                MockSSESource.streamItineraryPlan(adapter, handler);
            } else {
                adapter.addAIResponse(selectResponse(text));
            }
            recyclerView.forceScrollToBottom();
        }, 500);
    }

    private String selectResponse(String text) {
        if (containsAny(text, "出行", "旅游", "旅行", "行程", "规划")) {
            return MockSSESource.buildTravelPlan();
        }
        if (containsAny(text, "酒店", "hotel", "住宿", "宾馆")) {
            return MockSSESource.buildHotelRecommendation();
        }
        if (containsAny(text, "火车", "高铁", "train", "动车", "车票")) {
            return MockSSESource.buildTrainRecommendation();
        }
        if (containsAny(text, "飞机", "机票", "flight", "航班", "航空")) {
            return MockSSESource.buildFlightRecommendation();
        }
        if (containsAny(text, "天气", "weather", "气温")) {
            return MockSSESource.buildWeatherRecommendation();
        }
        return MockSSESource.buildSimpleText();
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getResourceId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private int getLayoutId(String name) {
        return getResources().getIdentifier(name, "layout", getPackageName());
    }
}
