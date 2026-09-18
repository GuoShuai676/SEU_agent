package com.example.seu_agent.tool;

import android.content.Context;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.rag.AgentTool;

import org.json.JSONObject;

public class GetMyProfileTool implements AgentTool
{

    private final Context ctx;

    public GetMyProfileTool(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public String getName() {
        return "GetMyProfile";
    }

    @Override
    public String getDescription() {
        return "用于获取我的个人信息，包括姓名，专业，年级，爱好，学号，"
                +"在回复时可以据此生成更符合用户的回复";

    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JSONObject res=new JSONObject();
        try{
            res.put("姓名", AppConfig.getProfileName(ctx));
            res.put("学号", AppConfig.getStudentId(ctx));
            res.put("专业", AppConfig.getMajor(ctx));
            res.put("年级", AppConfig.getGrade(ctx));
            res.put("爱好", AppConfig.getHobbies(ctx));

        } catch (Exception e) {
            return "读取个人信息出现错误";
        }

        return res.toString();
    }
}
