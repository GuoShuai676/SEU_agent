package com.example.seu_agent.tool;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Course;
import com.example.seu_agent.CourseSchedule;
import com.example.seu_agent.rag.AgentTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 查询用户手动录入的课程，不经过 RAG。 */
public class GetCoursesTool implements AgentTool {

    private final Context ctx;

    public GetCoursesTool(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public String getName() {
        return "get_my_courses";
    }

    @Override
    public String getDescription() {
        return "查询用户手动录入的个人课表。可以按具体日期、教学周和星期查询；"
                + "不传参数时列出全部课程。用户询问今天、明天、某天或某周有什么课时调用。";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"date\":{\"type\":\"string\",\"description\":"
                + "\"要查询的日期，格式YYYY-MM-DD。查询今天或明天时先结合当前时间确定日期\"},"
                + "\"week\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":16,"
                + "\"description\":\"教学周，范围1到16\"},"
                + "\"day_of_week\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":7,"
                + "\"description\":\"星期，周一为1，周日为7\"}"
                + "}}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JSONObject args = new JSONObject(argumentsJson);
        String dateText = args.optString("date", "").trim();

        if (!dateText.isEmpty()) return queryByDate(dateText);

        int week = args.optInt("week", 0);
        int day = args.optInt("day_of_week", 0);
        if (week == 0 && day == 0) return allCourses();

        if (week == 0) {
            week = CourseSchedule.getTeachingWeek(ctx, System.currentTimeMillis());
            if (week == 0) return "无法确定当前教学周，请先在“我的”页面设置本学期第一周周一";
        }
        if (week < 1 || week > CourseSchedule.TOTAL_WEEKS) return "教学周必须在1到16之间";
        if (day < 0 || day > 7) return "星期必须在1到7之间，周一为1";

        if (day == 0) {
            JSONArray days = new JSONArray();
            for (int i = 1; i <= 7; i++) {
                List<Course> courses = AppDatabase.get(ctx).courseDao().getForDay(i, week);
                if (!courses.isEmpty()) days.put(dayResult(i, week, courses));
            }
            JSONObject result = new JSONObject();
            result.put("教学周", week);
            result.put("有课的日期", days);
            if (days.length() == 0) result.put("提示", "这一周没有查到课程");
            return result.toString();
        }

        return dayResult(day, week,
                AppDatabase.get(ctx).courseDao().getForDay(day, week)).toString();
    }

    private String queryByDate(String dateText) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        parser.setLenient(false);
        Date date;
        try {
            date = parser.parse(dateText);
        } catch (Exception e) {
            return "日期格式错误，请使用YYYY-MM-DD，例如2026-09-03";
        }
        if (date == null) return "无法解析日期";

        int week = CourseSchedule.getTeachingWeek(ctx, date.getTime());
        if (week == 0) {
            return "该日期不在已设置的1到16教学周内，或尚未设置本学期第一周周一";
        }
        int day = CourseSchedule.getDayOfWeek(date.getTime());
        JSONObject result = dayResult(day, week,
                AppDatabase.get(ctx).courseDao().getForDay(day, week));
        result.put("日期", dateText);
        return result.toString();
    }

    private String allCourses() throws Exception {
        List<Course> courses = AppDatabase.get(ctx).courseDao().getAll();
        JSONObject result = new JSONObject();
        result.put("查询范围", "全部已录入课程");
        result.put("课程数量", courses.size());
        result.put("课程", courseArray(courses));
        if (courses.isEmpty()) result.put("提示", "还没有录入课程，请先在“我的”页面添加");
        return result.toString();
    }

    private JSONObject dayResult(int day, int week, List<Course> courses) throws Exception {
        JSONObject result = new JSONObject();
        result.put("教学周", week);
        result.put("星期", CourseSchedule.DAYS[day - 1]);
        result.put("课程数量", courses.size());
        result.put("课程", courseArray(courses));
        if (courses.isEmpty()) result.put("提示", "当天没有查到课程");
        return result;
    }

    private JSONArray courseArray(List<Course> courses) throws Exception {
        JSONArray result = new JSONArray();
        for (Course course : courses) {
            JSONObject item = new JSONObject();
            item.put("课程名称", safe(course.name));
            item.put("教师", safe(course.teacher));
            item.put("地点", safe(course.location));
            item.put("星期", CourseSchedule.DAYS[course.dayOfWeek - 1]);
            item.put("节次", CourseSchedule.sectionRange(course.startSection, course.endSection));
            item.put("起止周", course.startWeek + "-" + course.endWeek);
            item.put("周类型", weekType(course.weekType));
            item.put("备注", safe(course.description));
            result.put(item);
        }
        return result;
    }

    private String weekType(int type) {
        if (type == 1) return "单周";
        if (type == 2) return "双周";
        return "每周";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
