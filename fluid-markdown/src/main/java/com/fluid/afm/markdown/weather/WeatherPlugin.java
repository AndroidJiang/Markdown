package com.fluid.afm.markdown.weather;

import androidx.annotation.NonNull;

import com.fluid.afm.StreamOutStateObserver;
import com.fluid.afm.utils.MDLogger;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonVisitor;

/**
 * 天气卡片 Plugin：拦截 ```weather 代码块中的 JSON，渲染为天气卡片。
 * 同时实现 {@link StreamOutStateObserver} 以支持流式渲染时的 Span 缓存。
 *
 * <h3>支持两种 JSON 格式</h3>
 *
 * <b>单卡片（Object）</b>
 * <pre>{@code
 * ```weather
 * {"city":"北京","temp":"26","condition":"晴","wind":"西北风 3级","humidity":"42%"}
 * ```
 * }</pre>
 *
 * <b>多卡片（Array）</b>
 * <pre>{@code
 * ```weather
 * [
 *   {"city":"北京","temp":"26","condition":"晴"},
 *   {"city":"上海","temp":"22","condition":"多云"}
 * ]
 * ```
 * }</pre>
 */
public class WeatherPlugin extends AbstractMarkwonPlugin implements StreamOutStateObserver {

    private static final String TAG = "WeatherPlugin";
    private static final String LANG_WEATHER = "weather";

    private final ConcurrentHashMap<String, WeatherCardSpan> mSpanCache = new ConcurrentHashMap<>();
    private boolean mIsStreaming = false;

    public static WeatherPlugin create() {
        return new WeatherPlugin();
    }

    @Override
    public void onStreamOutStateChanged(boolean isStreamingOutput) {
        mIsStreaming = isStreamingOutput;
        if (!isStreamingOutput) {
            mSpanCache.clear();
        }
    }

    /**
     * AST 预处理：将 info="weather" 的 FencedCodeBlock 替换为 WeatherNode，
     * 使其不会被 CorePlugin 的默认 FencedCodeBlock visitor 渲染为代码块。
     */
    @Override
    public void beforeRender(@NonNull Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            if (child instanceof FencedCodeBlock) {
                FencedCodeBlock codeBlock = (FencedCodeBlock) child;
                String info = codeBlock.getInfo();
                if (LANG_WEATHER.equalsIgnoreCase(info != null ? info.trim() : "")) {
                    replaceWithWeatherNodes(codeBlock);
                }
            }
            child = next;
        }
    }

    private void replaceWithWeatherNodes(FencedCodeBlock codeBlock) {
        String json = codeBlock.getLiteral();
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        json = json.trim();

        try {
            List<WeatherNode> weatherNodes = new ArrayList<>();
            if (json.startsWith("[")) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    weatherNodes.add(parseWeatherNode(array.getJSONObject(i)));
                }
            } else {
                weatherNodes.add(parseWeatherNode(new JSONObject(json)));
            }

            for (WeatherNode weatherNode : weatherNodes) {
                codeBlock.insertBefore(weatherNode);
            }
            codeBlock.unlink();
        } catch (Exception e) {
            MDLogger.e(TAG, "Failed to parse weather JSON: " + e.getMessage());
        }
    }

    private WeatherNode parseWeatherNode(JSONObject obj) {
        return new WeatherNode(
                obj.optString("city", "未知城市"),
                obj.optString("temp", "--"),
                obj.optString("condition", "未知"),
                obj.optString("wind", ""),
                obj.optString("humidity", "")
        );
    }

    /**
     * 注册 WeatherNode 的 visitor，渲染为 WeatherCardSpan。
     */
    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        builder.on(WeatherNode.class, (visitor, weatherNode) -> {
            String cacheKey = weatherNode.getCity() + "_" + weatherNode.getTemp()
                    + "_" + weatherNode.getCondition();

            WeatherCardSpan span;
            if (mIsStreaming && mSpanCache.containsKey(cacheKey)) {
                span = mSpanCache.get(cacheKey);
            } else {
                span = new WeatherCardSpan(
                        weatherNode.getCity(),
                        weatherNode.getTemp(),
                        weatherNode.getCondition(),
                        weatherNode.getWind(),
                        weatherNode.getHumidity()
                );
                if (mIsStreaming) {
                    mSpanCache.put(cacheKey, span);
                }
            }

            visitor.ensureNewLine();
            int length = visitor.length();
            visitor.builder().append("\u00a0");
            visitor.setSpans(length, span);
            visitor.forceNewLine();
        });
    }
}
