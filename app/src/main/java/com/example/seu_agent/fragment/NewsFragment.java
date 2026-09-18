package com.example.seu_agent.fragment;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.CampusTask;
import com.example.seu_agent.Course;
import com.example.seu_agent.CourseSchedule;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeDao;
import com.example.seu_agent.R;
import com.example.seu_agent.adapter.NewsAdapter;
import com.example.seu_agent.tool.WeatherTool;
import com.example.seu_agent.weather.WeatherDialog;
import com.example.seu_agent.weather.WeatherStyle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NewsFragment extends Fragment {

    private static final long WEATHER_REFRESH_INTERVAL = 10 * 60 * 1000L;
    private static final String WEATHER_CACHE = "weather_cache";

    private RecyclerView rvNews;
    private final NewsAdapter adapter = new NewsAdapter();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final WeatherTool weatherTool = new WeatherTool();
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());

    private TextView weatherTemp;
    private TextView weatherCity;
    private TextView weatherDesc;
    private TextView weatherIconView;
    private boolean weatherLoading;
    private JSONObject latestWeather;
    private WeatherDialog weatherDialog;
    private final Runnable weatherRefreshTask = new Runnable() {
        @Override
        public void run() {
            loadWeather();
            weatherHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL);
        }
    };

    private String currentTag = "";
    private int queryVersion = 0;

    private View todayPanel;
    private View dashboardRoot;
    private LinearLayout todayCourseList;
    private LinearLayout todayTaskList;
    private TextView todayDate;
    private TextView todayGreeting;
    private TextView todaySummary;
    private NewsSheetController newsSheet;

    private ValueAnimator padAnimator;
    private View.OnLayoutChangeListener navLayoutListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_news, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });
        dashboardRoot = view.findViewById(R.id.dashboard_root);
        todayPanel = view.findViewById(R.id.today_panel);
        todayCourseList = view.findViewById(R.id.today_course_list);
        todayTaskList = view.findViewById(R.id.today_task_list);
        todayDate = view.findViewById(R.id.tv_today_date);
        todayGreeting = view.findViewById(R.id.tv_today_greeting);
        todaySummary = view.findViewById(R.id.tv_today_summary);
        newsSheet = new NewsSheetController(this, view, savedInstanceState);
        view.findViewById(R.id.today_course_card).setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.navigation);
            nav.setSelectedItemId(R.id.menu_mine);
        });
        view.findViewById(R.id.btn_add_task).setOnClickListener(v -> showTaskDialog());

        weatherTemp = view.findViewById(R.id.tv_weather_temp);
        weatherCity = view.findViewById(R.id.tv_weather_city);
        weatherDesc = view.findViewById(R.id.tv_weather_desc);
        weatherIconView = view.findViewById(R.id.tv_weather_icon);
        weatherDialog = new WeatherDialog(requireContext());
        showCachedWeather();
        view.findViewById(R.id.weather_card)
                .setOnClickListener(v -> weatherDialog.show(latestWeather));

        rvNews = view.findViewById(R.id.rv_news);
        rvNews.setLayoutManager(new LinearLayoutManager(getContext()));
        rvNews.setAdapter(adapter);

        rvNews.setItemAnimator(null);
        rvNews.setHasFixedSize(true);

        applyNavBarClearance();

        loadNotices();

        adapter.setOnItemClickListener(item -> {
            if (item.url == null || item.url.isEmpty()) {
                Toast.makeText(getContext(), "该资讯暂无链接", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            startActivity(intent);
        });

        TabLayout tabCategory = view.findViewById(R.id.tab_category);
        tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String tag = tab.getText() == null ? "" : tab.getText().toString();
                loadByTag(tag);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

    }

    private void loadTodayCampus() {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || activity == null) return;
        final Context app = context.getApplicationContext();
        final long now = System.currentTimeMillis();

        new Thread(() -> {
            int week = CourseSchedule.getTeachingWeek(app, now);
            int day = CourseSchedule.getDayOfWeek(now);
            boolean configured = AppConfig.getFirstMonday(app) != 0L;
            List<Course> courses = week == 0
                    ? new ArrayList<>()
                    : AppDatabase.get(app).courseDao().getForDay(day, week);
            List<CampusTask> tasks = AppDatabase.get(app).campusTaskDao().getPending(2);
            activity.runOnUiThread(() -> showTodayCampus(
                    now, week, day, configured, courses, tasks));
        }).start();
    }

    private void showTodayCampus(long now, int week, int day, boolean configured,
                                 List<Course> courses, List<CampusTask> tasks) {
        if (todayCourseList == null || todayDate == null) return;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        String weekText = week == 0 ? "非教学周" : "第" + week + "周";
        todayDate.setText(String.format(Locale.CHINA, "%d月%d日 · %s · %s",
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH), weekText,
                CourseSchedule.DAYS[day - 1]));

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 6 ? "夜深了" : hour < 11 ? "上午好"
                : hour < 14 ? "中午好" : hour < 18 ? "下午好" : "晚上好";
        String name = AppConfig.getProfileName(requireContext());
        if (name != null && !name.isEmpty() && !"未填写".equals(name)) greeting += "，" + name;
        todayGreeting.setText(greeting);

        if (!configured) {
            todaySummary.setText("设置本学期第一周后，即可显示今日课程");
        } else if (week == 0) {
            todaySummary.setText("当前不在已设置的 1–16 教学周内");
        } else if (courses.isEmpty()) {
            todaySummary.setText("今天没有课程安排");
        } else {
            todaySummary.setText("今天有 " + courses.size() + " 门课");
        }
        showTodayCourses(courses, calendar);
        showTodayTasks(tasks, now);
    }

    private void showTodayCourses(List<Course> courses, Calendar now) {
        todayCourseList.removeAllViews();
        if (courses.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(AppConfig.getFirstMonday(requireContext()) == 0L
                    ? "点击这里去设置学期和课表" : "今天可以自由安排时间");
            empty.setTextColor(getResources().getColor(R.color.text_secondary, null));
            empty.setTextSize(12);
            empty.setPadding(0, dp(9), 0, dp(7));
            todayCourseList.addView(empty);
            return;
        }

        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        List<Course> visible = new ArrayList<>();
        for (Course course : courses) {
            if (courseEndMinutes(course) >= nowMinutes && visible.size() < 1) {
                visible.add(course);
            }
        }
        if (visible.isEmpty()) {
            int from = Math.max(0, courses.size() - 1);
            visible.addAll(courses.subList(from, courses.size()));
        }

        for (Course course : visible) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.item_today_course, todayCourseList, false);
            TextView time = row.findViewById(R.id.tv_today_course_time);
            TextView name = row.findViewById(R.id.tv_today_course_name);
            TextView detail = row.findViewById(R.id.tv_today_course_detail);
            TextView status = row.findViewById(R.id.tv_today_course_status);

            time.setText(courseStartTime(course));
            name.setText(course.name == null || course.name.isEmpty() ? "未命名课程" : course.name);
            StringBuilder info = new StringBuilder();
            if (course.location != null && !course.location.isEmpty()) info.append(course.location);
            if (course.teacher != null && !course.teacher.isEmpty()) {
                if (info.length() > 0) info.append(" · ");
                info.append(course.teacher);
            }
            if (info.length() == 0) info.append(CourseSchedule.sectionRange(
                    course.startSection, course.endSection));
            detail.setText(info.toString());

            int start = courseStartMinutes(course);
            int end = courseEndMinutes(course);
            status.setText(nowMinutes < start ? "待上课"
                    : nowMinutes <= end ? "进行中" : "已结束");
            todayCourseList.addView(row);
        }
    }

    private void showTodayTasks(List<CampusTask> tasks, long now) {
        if (todayTaskList == null) return;
        todayTaskList.removeAllViews();
        if (tasks.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("暂无待办，点击右上角添加");
            empty.setTextColor(getResources().getColor(R.color.text_secondary, null));
            empty.setTextSize(12);
            empty.setPadding(0, dp(3), 0, dp(7));
            todayTaskList.addView(empty);
            return;
        }

        for (CampusTask task : tasks) {
            View row = LayoutInflater.from(requireContext()).inflate(
                    R.layout.item_today_task, todayTaskList, false);
            CheckBox check = row.findViewById(R.id.check_task);
            TextView title = row.findViewById(R.id.tv_task_title);
            TextView due = row.findViewById(R.id.tv_task_due);
            title.setText(task.title == null || task.title.isEmpty() ? "未命名待办" : task.title);
            String dueText = formatTaskTime(task.dueAt, now);
            if ("ai".equals(task.source)) dueText += " · AI 添加";
            due.setText(dueText);
            check.setOnClickListener(v -> setTaskCompleted(task.id));
            row.setOnLongClickListener(v -> {
                confirmDeleteTask(task);
                return true;
            });
            todayTaskList.addView(row);
        }
    }

    private String formatTaskTime(long dueAt, long now) {
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(dueAt);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        String prefix;
        if (sameDay(due, today)) prefix = "今天 ";
        else if (sameDay(due, tomorrow)) prefix = "明天 ";
        else prefix = new SimpleDateFormat("M月d日 ", Locale.CHINA).format(new Date(dueAt));
        String time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(dueAt));
        return (dueAt < now ? "已逾期 · " : "") + prefix + time;
    }

    private boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private void showTaskDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(
                R.layout.dialog_task, null, false);
        EditText title = content.findViewById(R.id.et_task_title);
        EditText note = content.findViewById(R.id.et_task_note);
        TextView dateButton = content.findViewById(R.id.btn_task_date);
        TextView timeButton = content.findViewById(R.id.btn_task_time);
        Calendar due = Calendar.getInstance();
        due.add(Calendar.DAY_OF_MONTH, 1);
        due.set(Calendar.HOUR_OF_DAY, 18);
        due.set(Calendar.MINUTE, 0);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);

        Runnable refreshTime = () -> {
            dateButton.setText(new SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
                    .format(due.getTime()));
            timeButton.setText(new SimpleDateFormat("HH:mm", Locale.CHINA)
                    .format(due.getTime()));
        };
        refreshTime.run();

        dateButton.setOnClickListener(v -> new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    due.set(year, month, day);
                    refreshTime.run();
                }, due.get(Calendar.YEAR), due.get(Calendar.MONTH),
                due.get(Calendar.DAY_OF_MONTH)).show());
        timeButton.setOnClickListener(v -> new TimePickerDialog(requireContext(),
                (picker, hour, minute) -> {
                    due.set(Calendar.HOUR_OF_DAY, hour);
                    due.set(Calendar.MINUTE, minute);
                    refreshTime.run();
                }, due.get(Calendar.HOUR_OF_DAY), due.get(Calendar.MINUTE), true).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加待办日程")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String taskTitle = title.getText().toString().trim();
                    if (taskTitle.isEmpty()) {
                        title.setError("请填写待办标题");
                        return;
                    }
                    if (due.getTimeInMillis() <= System.currentTimeMillis()) {
                        Toast.makeText(requireContext(), "截止时间需要晚于现在",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveTask(taskTitle, note.getText().toString().trim(), due.getTimeInMillis());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void saveTask(String title, String note, long dueAt) {
        Context app = requireContext().getApplicationContext();
        CampusTask task = new CampusTask();
        task.title = title;
        task.note = note;
        task.dueAt = dueAt;
        task.completed = false;
        task.createdAt = System.currentTimeMillis();
        task.source = "manual";
        new Thread(() -> {
            AppDatabase.get(app).campusTaskDao().insert(task);
            if (getActivity() != null) requireActivity().runOnUiThread(this::loadTodayCampus);
        }).start();
    }

    private void setTaskCompleted(long id) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            AppDatabase.get(app).campusTaskDao().setCompleted(id, true);
            if (getActivity() != null) requireActivity().runOnUiThread(this::loadTodayCampus);
        }).start();
    }

    private void confirmDeleteTask(CampusTask task) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除待办？")
                .setMessage(task.title)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteTask(task.id))
                .show();
    }

    private void deleteTask(long id) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            AppDatabase.get(app).campusTaskDao().deleteById(id);
            if (getActivity() != null) requireActivity().runOnUiThread(this::loadTodayCampus);
        }).start();
    }

    private String courseStartTime(Course course) {
        if (course.startSection < 1 || course.startSection >= CourseSchedule.SECTION_TIMES.length) {
            return "--:--";
        }
        return CourseSchedule.SECTION_TIMES[course.startSection].substring(0, 5);
    }

    private int courseStartMinutes(Course course) {
        if (course.startSection < 1 || course.startSection >= CourseSchedule.SECTION_TIMES.length) {
            return 0;
        }
        return timeToMinutes(CourseSchedule.SECTION_TIMES[course.startSection].substring(0, 5));
    }

    private int courseEndMinutes(Course course) {
        if (course.endSection < 1 || course.endSection >= CourseSchedule.SECTION_TIMES.length) {
            return 24 * 60;
        }
        return timeToMinutes(CourseSchedule.SECTION_TIMES[course.endSection].substring(6));
    }

    private int timeToMinutes(String value) {
        String[] parts = value.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private void loadWeather() {
        if (weatherLoading || weatherTemp == null) return;
        weatherLoading = true;

        new Thread(() -> {
            try {
                String raw = weatherTool.execute("{\"city\":\"南京\"}");
                JSONObject weather = new JSONObject(raw);
                String city = weather.optString("地区", "南京");
                String temp = String.valueOf(weather.opt("温度℃"));
                String desc = weather.optString("当前天气", "天气未知");
                String humidity = String.valueOf(weather.opt("湿度%"));

                if ("null".equals(temp)) temp = "--";
                String cardDesc = desc;
                if (!"null".equals(humidity)) cardDesc += " · 湿度" + humidity + "%";

                String finalTemp = temp;
                String finalDesc = cardDesc;
                weatherHandler.post(() -> {
                    weatherLoading = false;
                    if (weatherTemp == null) return;
                    latestWeather = weather;
                    showWeather(finalTemp, city, finalDesc);
                    if (weatherDialog != null) weatherDialog.update(weather);
                    requireContext().getSharedPreferences(WEATHER_CACHE, Context.MODE_PRIVATE)
                            .edit()
                            .putString("temp", finalTemp)
                            .putString("city", city)
                            .putString("desc", finalDesc)
                            .putString("raw", weather.toString())
                            .putLong("time", System.currentTimeMillis())
                            .apply();
                });
            } catch (Exception e) {
                weatherHandler.post(() -> {
                    weatherLoading = false;
                    if (weatherDesc != null) weatherDesc.setText("天气暂不可用");
                });
            }
        }).start();
    }

    private void showCachedWeather() {
        Context context = getContext();
        if (context == null) return;
        android.content.SharedPreferences cache = context.getSharedPreferences(
                WEATHER_CACHE, Context.MODE_PRIVATE);
        String temp = cache.getString("temp", "--");
        String city = cache.getString("city", "南京");
        String desc = cache.getString("desc", "获取天气中");
        String raw = cache.getString("raw", "");
        if (raw != null && !raw.isEmpty()) {
            try {
                latestWeather = new JSONObject(raw);
            } catch (Exception ignored) {
                latestWeather = null;
            }
        }
        showWeather(temp, city, desc);
    }

    private void showWeather(String temp, String city, String desc) {
        String temperature = temp + "°";
        if (!temperature.contentEquals(weatherTemp.getText())) weatherTemp.setText(temperature);
        if (!city.contentEquals(weatherCity.getText())) weatherCity.setText(city);
        if (!desc.contentEquals(weatherDesc.getText())) weatherDesc.setText(desc);
        if (weatherIconView != null) {
            String code = latestWeather == null ? ""
                    : latestWeather.optString("天气代码",
                    latestWeather.optString("weather_icon", ""));
            weatherIconView.setText(WeatherStyle.iconFor(desc, code));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTodayCampus();
        if (!isHidden()) refreshNoticesFromServer();
        weatherHandler.removeCallbacks(weatherRefreshTask);
        loadWeather();
        weatherHandler.postDelayed(weatherRefreshTask, WEATHER_REFRESH_INTERVAL);
    }

    @Override
    public void onPause() {
        weatherHandler.removeCallbacks(weatherRefreshTask);
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            loadTodayCampus();
            refreshNoticesFromServer();
        }
    }

    private void refreshNoticesFromServer() {
        loadNotices();
        syncNoticesFromCloud();
    }

    private void loadByTag(String tag) {
        currentTag = tag;
        loadNotices();
    }

    private void loadNotices() {
        final int version = ++queryVersion;
        final String tag = currentTag;
        final Activity act = getActivity();
        if (act == null) return;
        new Thread(() -> {
            NoticeDao dao = AppDatabase.get(act).noticeDao();
            boolean all = tag.isEmpty() || tag.equals("全部");
            List<Notice> rows = all ? dao.getAll() : dao.getByTag(tag);
            if (version != queryVersion) return;
            List<NewsAdapter.NewsItem> items = toItems(rows);
            act.runOnUiThread(() -> {
                if (version == queryVersion) adapter.submit(items);
            });
        }).start();
    }

    private void syncNoticesFromCloud() {
        final Context ctx = getContext();
        final Activity act = getActivity();
        if (ctx == null || act == null) return;
        new Thread(() -> {
            try {
                String url = AppConfig.getServerUrl(ctx) + "/api/notices?limit=50";
                Request req = new Request.Builder().url(url).build();
                try (Response resp = httpClient.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";

                    JSONObject json = new JSONObject(body);
                    JSONArray items = json.getJSONObject("data").getJSONArray("items");
                    List<Notice> list = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        Notice n = new Notice();
                        n.tag = it.optString("tag");
                        n.title = it.optString("title");
                        n.content = it.optString("content");
                        n.publishDate = it.optString("publish_date");
                        n.url = it.optString("url");
                        list.add(n);
                    }

                    AppDatabase.get(ctx).noticeDao().insertAll(list);
                    act.runOnUiThread(this::loadNotices);
                }
            } catch (Exception e) {
                e.printStackTrace();
                act.runOnUiThread(() -> Toast.makeText(ctx,
                        "云端同步失败，正在显示本地缓存", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void applyNavBarClearance() {
        View nav = requireActivity().findViewById(R.id.navigation);
        if (nav == null) return;

        nav.post(() -> {
            int h = nav.getHeight() + dp(16);
            if (h > 0) applyNavClearance(h);
        });

        navLayoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t + dp(16);
            if (h > 0) applyNavClearance(h);
        };
        nav.addOnLayoutChangeListener(navLayoutListener);
    }

    private void applyNavClearance(int navHeight) {
        if (dashboardRoot == null) return;
        if (rvNews != null) animateBottomPadding(rvNews, navHeight);
        dashboardRoot.post(() -> {
            if (newsSheet != null && dashboardRoot != null)
                newsSheet.updateCollapsedDistance(dashboardRoot.getHeight() / 2f);
        });
    }

    private void animateBottomPadding(View v, int target) {
        if (padAnimator != null) padAnimator.cancel();
        padAnimator = ValueAnimator.ofInt(v.getPaddingBottom(), target);
        padAnimator.addUpdateListener(a -> v.setPadding(
                v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                (int) a.getAnimatedValue()));
        padAnimator.setDuration(250);
        padAnimator.setInterpolator(new DecelerateInterpolator());
        padAnimator.start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private List<NewsAdapter.NewsItem> toItems(List<Notice> rows) {
        List<NewsAdapter.NewsItem> items = new ArrayList<>();
        for (Notice n : rows) {
            items.add(new NewsAdapter.NewsItem(n.title, n.publishDate, n.tag, n.url));
        }
        return items;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (newsSheet != null) newsSheet.saveState(outState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        weatherHandler.removeCallbacks(weatherRefreshTask);
        weatherTemp = null;
        weatherCity = null;
        weatherDesc = null;
        weatherIconView = null;
        weatherLoading = false;
        if (weatherDialog != null) weatherDialog.dismiss();
        weatherDialog = null;
        if (padAnimator != null) {
            padAnimator.cancel();
            padAnimator = null;
        }
        if (navLayoutListener != null && getActivity() != null) {
            View nav = getActivity().findViewById(R.id.navigation);
            if (nav != null) nav.removeOnLayoutChangeListener(navLayoutListener);
            navLayoutListener = null;
        }
        rvNews = null;
        if (newsSheet != null) newsSheet.destroy();
        newsSheet = null;
        dashboardRoot = null;
        todayPanel = null;
        todayCourseList = null;
        todayTaskList = null;
        todayDate = null;
        todayGreeting = null;
        todaySummary = null;
    }
}
