package com.fluid.markdown.chat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fluid.afm.AFMInitializer;
import com.fluid.afm.ContextHolder;
import com.fluid.afm.R;
import com.fluid.afm.markdown.ElementClickEventCallback;
import com.fluid.afm.markdown.model.EventModel;
import com.fluid.afm.markdown.html.SpanTextClickableSpan;

import java.util.List;
import java.util.Map;

/**
 * AI 对话页（模拟千问聊天主界面）。
 * <p>
 * 核心功能：
 * 1. RecyclerView 列表展示对话消息
 * 2. 用户输入消息后，模拟 AI 流式回复
 * 3. 流式 Markdown 渲染（基于 PrinterMarkDownTextView）
 * 4. 卡片嵌入在消息流中（酒店卡片）
 * 5. 自动滚动到底部 + 遇到卡片暂停滚动
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

        // 确保 AFM 初始化
        if (ContextHolder.getContext() == null) {
            AFMInitializer.init(this, null, null, null);
        }

        initViews();
        initRecyclerView();

        // 首次进入发送一条模拟消息
        handler.postDelayed(this::sendInitialMessage, 300);
    }

    private void initViews() {
        int rvId = getResourceId("chat_recycler_view");
        int inputId =getResourceId("et_input");
        int sendId = getResourceId("btn_send");
        int backId = getResourceId("chat_back");

        recyclerView = findViewById(rvId);
        inputEdit = findViewById(inputId);
        sendButton = findViewById(sendId);

        findViewById(backId).setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> handleSend());

        // 输入框有内容时按钮高亮
        inputEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                sendButton.setEnabled(s.length() > 0);
                sendButton.setAlpha(s.length() > 0 ? 1.0f : 0.4f);
            }
        });
    }

    private void initRecyclerView() {
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);

        adapter = new ChatAdapter(new ElementClickEventCallback() {
            @Override
            public boolean onLinkClicked(Map<String, Object> params) {
                return false;
            }
            @Override
            public void onFootnoteClicked(String index) {}
            @Override
            public void onImageClicked(String url, String description) {}
            @Override
            public boolean onTextClickableSpanClicked(View widget, String link, String entityID,
                                                       SpanTextClickableSpan.ClickableTextType type) {
                return false;
            }
            @Override
            public void exposureSpmBehavior(List<EventModel> models) {}
        });
        recyclerView.setAdapter(adapter);
    }

    private void sendInitialMessage() {
        // 添加一条 AI 欢迎消息
        ChatMessage welcome = new ChatMessage(ChatMessage.TYPE_AI,
                "您好！我是 AI 助手，可以帮您推荐酒店。\n\n" +
                "请在下方输入您的问题，例如：**帮我推荐明天酒店**");
        welcome.isStreaming = true;
        adapter.addMessage(welcome);
        recyclerView.forceScrollToBottom();
    }

    private void handleSend() {
        String text = inputEdit.getText().toString().trim();
        if (text.isEmpty()) return;

        // 添加用户消息
        adapter.addMessage(new ChatMessage(ChatMessage.TYPE_USER, text));
        inputEdit.setText("");

        // 模拟 AI 回复（延迟 500ms 模拟网络）
        handler.postDelayed(() -> {
            // 根据用户输入选择回复内容
            String response;
            if (text.contains("酒店") || text.contains("hotel") || text.contains("住宿")) {
                response = MockSSESource.buildHotelRecommendation();
            } else {
                response = MockSSESource.buildSimpleText();
            }

            ChatMessage aiMsg = new ChatMessage(ChatMessage.TYPE_AI, response);
            aiMsg.isStreaming = true;
            adapter.addMessage(aiMsg);
            recyclerView.forceScrollToBottom();
            // 卡片暂停滚动逻辑已移到 ChatViewHolder.startStreaming() 内部
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private int getResourceId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private int getLayoutId(String name) {
        return getResources().getIdentifier(name, "layout", getPackageName());
    }
}
