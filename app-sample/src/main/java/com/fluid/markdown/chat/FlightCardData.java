package com.fluid.markdown.chat;

/**
 * 机票卡片数据模型。
 */
public class FlightCardData {
    public String flightNo;
    public String airline;
    public String from;
    public String to;
    public String departTime;
    public String arriveTime;
    public String duration;
    public String price;
    public String cabin;

    public FlightCardData(String flightNo, String airline, String from, String to,
                          String departTime, String arriveTime, String duration,
                          String price, String cabin) {
        this.flightNo = flightNo;
        this.airline = airline;
        this.from = from;
        this.to = to;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.duration = duration;
        this.price = price;
        this.cabin = cabin;
    }

    public static FlightCardData fromJson(String json) {
        try {
            return new FlightCardData(
                    es(json, "flightNo"),
                    es(json, "airline"),
                    es(json, "from"),
                    es(json, "to"),
                    es(json, "departTime"),
                    es(json, "arriveTime"),
                    es(json, "duration"),
                    es(json, "price"),
                    es(json, "cabin")
            );
        } catch (Exception e) {
            return new FlightCardData("未知", "未知", "未知", "未知", "--", "--", "--", "¥--", "经济舱");
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
}
