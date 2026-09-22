package com.fluid.markdown.chat;

/**
 * 酒店卡片数据模型。
 */
public class HotelCardData {
    public String name;
    public String price;
    public float rating;
    public String location;
    public String[] tags;

    public HotelCardData(String name, String price, float rating, String location, String[] tags) {
        this.name = name;
        this.price = price;
        this.rating = rating;
        this.location = location;
        this.tags = tags;
    }

    /**
     * 从 JSON 字符串解析酒店卡片数据（简化版，不引入外部库）。
     */
    public static HotelCardData fromJson(String json) {
        try {
            String name = extractString(json, "name");
            String price = extractString(json, "price");
            float rating = extractFloat(json, "rating");
            String location = extractString(json, "location");
            String tagsStr = extractString(json, "tags");
            String[] tags = null;
            if (tagsStr != null) {
                tagsStr = tagsStr.replace("[", "").replace("]", "").replace("\"", "");
                tags = tagsStr.split(",");
                for (int i = 0; i < tags.length; i++) {
                    tags[i] = tags[i].trim();
                }
            }
            return new HotelCardData(name, price, rating, location, tags);
        } catch (Exception e) {
            return new HotelCardData("未知酒店", "¥--/晚", 0, "未知", new String[0]);
        }
    }

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private static float extractFloat(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return 0;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
            end++;
        }
        try {
            return Float.parseFloat(json.substring(start, end));
        } catch (Exception e) {
            return 0;
        }
    }
}
