package com.fluid.afm.markdown.weather;

import org.commonmark.node.CustomBlock;

/**
 * 天气卡片 AST 节点，用于替代 info="weather" 的 FencedCodeBlock。
 */
public class WeatherNode extends CustomBlock {

    private final String city;
    private final String temp;
    private final String condition;
    private final String wind;
    private final String humidity;

    public WeatherNode(String city, String temp, String condition, String wind, String humidity) {
        this.city = city;
        this.temp = temp;
        this.condition = condition;
        this.wind = wind;
        this.humidity = humidity;
    }

    public String getCity() {
        return city;
    }

    public String getTemp() {
        return temp;
    }

    public String getCondition() {
        return condition;
    }

    public String getWind() {
        return wind;
    }

    public String getHumidity() {
        return humidity;
    }
}
