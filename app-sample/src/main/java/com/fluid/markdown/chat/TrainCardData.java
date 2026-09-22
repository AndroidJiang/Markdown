package com.fluid.markdown.chat;

/**
 * 火车票卡片数据模型。
 */
public class TrainCardData {
    public String trainNo;
    public String from;
    public String to;
    public String departTime;
    public String arriveTime;
    public String duration;
    public String price;
    public String[] seats;

    public TrainCardData(String trainNo, String from, String to, String departTime,
                         String arriveTime, String duration, String price, String[] seats) {
        this.trainNo = trainNo;
        this.from = from;
        this.to = to;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.duration = duration;
        this.price = price;
        this.seats = seats;
    }

    public static TrainCardData fromJson(String json) {
        try {
            return new TrainCardData(
                    es(json, "trainNo"),
                    es(json, "from"),
                    es(json, "to"),
                    es(json, "departTime"),
                    es(json, "arriveTime"),
                    es(json, "duration"),
                    es(json, "price"),
                    null
            );
        } catch (Exception e) {
            return new TrainCardData("未知", "未知", "未知", "--", "--", "--", "¥--", new String[0]);
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
