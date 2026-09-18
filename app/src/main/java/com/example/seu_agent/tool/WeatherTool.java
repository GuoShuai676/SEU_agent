package com.example.seu_agent.tool;

import com.example.seu_agent.rag.AgentTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherTool implements AgentTool {

    private static final String API_URL = "https://uapis.cn/api/v1/misc/weather";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的实时天气、空气质量和未来三天天气。"
                + "用户询问天气、温度、是否下雨、是否需要带伞时调用。"
                + "默认地点使用南京。"
                +"在用户提及出门或是有活动安排时调用";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"city\":{\"type\":\"string\",\"description\":"
                + "\"城市或区县名称，例如南京、玄武区、北京\"}"
                + "},\"required\":[\"city\"]}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        String city = new JSONObject(argumentsJson).optString("city", "").trim();
        if (city.isEmpty()) return "请提供要查询的城市";

        HttpUrl base = HttpUrl.parse(API_URL);
        if (base == null) return "天气接口地址无效";
        HttpUrl url = base.newBuilder()
                .addQueryParameter("city", city)
                .addQueryParameter("extended", "true")
                .addQueryParameter("forecast", "true")
                .addQueryParameter("lang", "zh")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "SEU-Agent/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = "";
                try {
                    message = new JSONObject(body).optString("message", "");
                } catch (Exception ignored) {
                }
                if (response.code() == 404) return "没有找到城市「" + city + "」";
                return "天气接口请求失败（" + response.code() + "）"
                        + (message.isEmpty() ? "" : "：" + message);
            }
            return compactResult(new JSONObject(body));
        } catch (IOException e) {
            return "天气接口连接失败，请检查网络后重试";
        }
    }

    private String compactResult(JSONObject raw) throws Exception {
        JSONObject out = new JSONObject();
        out.put("地区", joinLocation(raw));
        copy(raw, out, "weather", "当前天气");
        copy(raw, out, "weather_icon", "天气代码");
        copy(raw, out, "temperature", "温度℃");
        copy(raw, out, "feels_like", "体感温度℃");
        copy(raw, out, "humidity", "湿度%");
        copy(raw, out, "wind_direction", "风向");
        copy(raw, out, "wind_power", "风力");
        copy(raw, out, "aqi", "AQI");
        copy(raw, out, "aqi_category", "空气质量");
        out.put("更新时间", new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
        JSONArray sourceForecast = raw.optJSONArray("forecast");
        if (sourceForecast != null) {
            JSONArray forecast = new JSONArray();
            for (int i = 0; i < Math.min(3, sourceForecast.length()); i++) {
                JSONObject sourceDay = sourceForecast.optJSONObject(i);
                if (sourceDay == null) continue;
                JSONObject day = new JSONObject();
                copy(sourceDay, day, "date", "日期");
                copy(sourceDay, day, "week", "星期");
                copy(sourceDay, day, "weather_day", "白天天气");
                copy(sourceDay, day, "weather_night", "夜间天气");
                copy(sourceDay, day, "temp_min", "最低温℃");
                copy(sourceDay, day, "temp_max", "最高温℃");
                copy(sourceDay, day, "precip", "降水量mm");
                forecast.put(day);
            }
            out.put("未来三天", forecast);
        }

        JSONArray alerts = raw.optJSONArray("alerts");
        if (alerts != null && alerts.length() > 0) out.put("气象预警", alerts);
        out.put("数据来源", "UAPI 天气服务");
        return out.toString();
    }

    private String joinLocation(JSONObject raw) {
        String province = raw.optString("province", "");
        String city = raw.optString("city", "");
        String district = raw.optString("district", "");
        StringBuilder result = new StringBuilder(province);
        if (!city.isEmpty() && !province.contains(city)) result.append(city);
        if (!district.isEmpty() && !result.toString().contains(district)) result.append(district);
        return result.toString();
    }

    private void copy(JSONObject from, JSONObject to, String oldName, String newName)
            throws Exception {
        if (from.has(oldName) && !from.isNull(oldName)) to.put(newName, from.get(oldName));
    }
}
