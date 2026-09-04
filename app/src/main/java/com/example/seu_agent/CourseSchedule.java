package com.example.seu_agent;

import android.content.Context;

import java.util.Calendar;

public class CourseSchedule {
    public static final int TOTAL_WEEKS = 16;
    public static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    public static final String[] SECTION_TIMES = {
            "",
            "08:00-08:45", "08:50-09:35", "09:50-10:35", "10:40-11:25", "11:30-12:15",
            "14:00-14:45", "14:50-15:35", "15:50-16:35", "16:40-17:25", "17:30-18:15",
            "19:00-19:45", "19:50-20:35", "20:40-21:25"
    };

    public static int getTeachingWeek(Context ctx, long dateMillis) {
        long firstMonday = AppConfig.getFirstMonday(ctx);
        if (firstMonday == 0L) return 0;
        long days = (dayStart(dateMillis) - dayStart(firstMonday)) / (24L * 60 * 60 * 1000);
        int week = (int) Math.floorDiv(days, 7) + 1;
        return week >= 1 && week <= TOTAL_WEEKS ? week : 0;
    }

    public static int getDayOfWeek(long dateMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dateMillis);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    public static String sectionRange(int start, int end) {
        if (start < 1 || end >= SECTION_TIMES.length || end < start) return "";
        String startTime = SECTION_TIMES[start].substring(0, 5);
        String endTime = SECTION_TIMES[end].substring(6);
        return "第" + start + "-" + end + "节（" + startTime + "-" + endTime + "）";
    }

    private static long dayStart(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
