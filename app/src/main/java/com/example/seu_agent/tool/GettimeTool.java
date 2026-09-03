package com.example.seu_agent.tool;

import com.example.seu_agent.rag.AgentTool;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 读取手机当前的日期、时间和时区。 */
public class GettimeTool implements AgentTool {

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "获取手机当前日期、时间、星期和时区。"
                + "用户询问现在几点、今天几号、今天星期几时调用。";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        Date now = new Date();
        TimeZone zone = TimeZone.getDefault();
        JSONObject result = new JSONObject();
        result.put("当前时间", format(now, "yyyy-MM-dd HH:mm:ss"));
        result.put("星期", format(now, "EEEE"));
        result.put("时区", zone.getID());
        result.put("时区名称", zone.getDisplayName(false, TimeZone.LONG, Locale.CHINA));
        result.put("时间戳", now.getTime());
        return result.toString();
    }

    private String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(date);
    }
}
