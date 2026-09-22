package com.fluid.markdown.chat;

/**
 * 天气卡片数据模型。
 */
public class WeatherCardData {
    public String city;
    public String temp;
    public String condition;
    public String wind;
    public String humidity;

    public WeatherCardData(String city, String temp, String condition, String wind, String humidity) {
        this.city = city;
        this.temp = temp;
        this.condition = condition;
        this.wind = wind;
        this.humidity = humidity;
    }

    public static WeatherCardData fromJson(String json) {
        try {
            return new WeatherCardData(
                    es(json, "city"),
                    es(json, "temp"),
                    es(json, "condition"),
                    es(json, "wind"),
                    es(json, "humidity")
            );
        } catch (Exception e) {
            return new WeatherCardData("未知", "--", "未知", "", "");
        }
    }

    private static String es(String json, String key) {
        String p = "\"" + key + "\":\"";
        int s = json.indexOf(p);
        if (s < 0) return null;
        s += p.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? null : json.substring(s, e);
    }

    /**
     * 根据天气状况返回 emoji。
     */
    public String getEmoji() {
        if (condition == null) return "🌤️";
        switch (condition) {
            case "晴": return "☀️";
            case "多云": return "⛅";
            case "阴": return "☁️";
            case "小雨":
            case "中雨":
            case "大雨": return "🌧️";
            case "雷阵雨": return "⛈️";
            case "小雪":
            case "中雪":
            case "大雪": return "❄️";
            case "雾":
            case "霾": return "🌫️";
            default: return "🌤️";
        }
    }

    /**
     * 根据天气状况返回渐变色。
     */
    public int[] getGradientColors() {
        if (condition == null) return new int[]{0xFF64B5F6, 0xFF1976D2};
        switch (condition) {
            case "晴": return new int[]{0xFFFFB74D, 0xFFFF9800};
            case "多云":
            case "阴": return new int[]{0xFF90A4AE, 0xFF607D8B};
            case "小雨":
            case "中雨":
            case "大雨":
            case "雷阵雨": return new int[]{0xFF64B5F6, 0xFF1976D2};
            case "小雪":
            case "中雪":
            case "大雪": return new int[]{0xFFB3E5FC, 0xFF4FC3F7};
            case "雾":
            case "霾": return new int[]{0xFFBDBDBD, 0xFF757575};
            default: return new int[]{0xFF64B5F6, 0xFF1976D2};
        }
    }
}
