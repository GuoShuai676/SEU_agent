package com.example.seu_agent.fragment;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Course;
import com.example.seu_agent.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 动态本地课表：按周显示，支持添加课程和长按删除。 */
public class ClassTableFragment extends Fragment {
    private static final String PREFS = "classtable_prefs";
    private static final String KEY_FIRST_MONDAY = "first_monday";
    private static final int MAX_WEEK = 30;
    private static final int MAX_SECTION = 13;
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private ExecutorService databaseExecutor;
    private TextView tvWeek;
    private TextView tvWeekDates;
    private TableLayout table;
    private int currentWeek = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        // MainActivity 当前会复用同一个 Fragment 实例；页面重建时必须同时重建线程池。
        if (databaseExecutor == null || databaseExecutor.isShutdown()) {
            databaseExecutor = Executors.newSingleThreadExecutor();
        }
        return inflater.inflate(R.layout.fragment_classtable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        super.onViewCreated(view, state);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });
        tvWeek = view.findViewById(R.id.tv_week);
        tvWeekDates = view.findViewById(R.id.tv_week_dates);
        table = view.findViewById(R.id.table_schedule);
        applyCurrentWeek();

        view.findViewById(R.id.btn_prev_week).setOnClickListener(v -> {
            if (currentWeek > 1) { currentWeek--; refreshWeek(); }
        });
        view.findViewById(R.id.btn_next_week).setOnClickListener(v -> {
            if (currentWeek < MAX_WEEK) { currentWeek++; refreshWeek(); }
        });
        view.findViewById(R.id.btn_set_first_date).setOnClickListener(v -> showFirstWeekPicker());
        view.findViewById(R.id.btn_add_course).setOnClickListener(v -> showCourseDialog(1, 1));
    }

    private void refreshWeek() {
        updateWeekHeader();
        loadCourses();
    }

    private void loadCourses() {
        if (databaseExecutor == null || databaseExecutor.isShutdown()) return;
        final int requestedWeek = currentWeek;
        final Context app = requireContext().getApplicationContext();
        databaseExecutor.execute(() -> {
            List<Course> courses = AppDatabase.get(app).courseDao().getForWeek(requestedWeek);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (isAdded() && table != null && requestedWeek == currentWeek) renderTable(courses);
            });
        });
    }

    private void renderTable(List<Course> courses) {
        table.removeAllViews();
        Map<String, Course> occupied = new HashMap<>();
        for (Course c : courses) {
            for (int section = c.startSection; section <= c.endSection; section++) {
                occupied.put(c.dayOfWeek + ":" + section, c);
            }
        }

        TableRow header = new TableRow(requireContext());
        header.addView(makeCell("节", R.style.CellHeader, 0.72f));
        for (String day : DAYS) header.addView(makeCell(day, R.style.CellHeader, 1f));
        table.addView(header);

        for (int section = 1; section <= MAX_SECTION; section++) {
            TableRow row = new TableRow(requireContext());
            row.addView(makeCell(String.valueOf(section), R.style.CellHeader, 0.72f));
            for (int day = 1; day <= 7; day++) {
                Course course = occupied.get(day + ":" + section);
                TextView cell;
                if (course == null) {
                    cell = makeCell("", R.style.CellEmpty, 1f);
                    final int selectedDay = day;
                    final int selectedSection = section;
                    cell.setOnClickListener(v -> showCourseDialog(selectedDay, selectedSection));
                } else {
                    String label = section == course.startSection ? compactCourseLabel(course) : "·";
                    cell = makeCell(label, courseStyle(course.colorIndex), 1f);
                    cell.setContentDescription(courseDetails(course));
                    cell.setOnClickListener(v -> Toast.makeText(requireContext(),
                            courseDetails(course), Toast.LENGTH_SHORT).show());
                    cell.setOnLongClickListener(v -> { confirmDelete(course); return true; });
                }
                row.addView(cell);
            }
            table.addView(row);
        }
    }

    private TextView makeCell(String text, int style, float weight) {
        TextView cell = new TextView(requireContext(), null, 0, style);
        TableRow.LayoutParams params = new TableRow.LayoutParams(0, dp(40), weight);
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        cell.setLayoutParams(params);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setText(text);
        cell.setMaxLines(2);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return cell;
    }

    private int courseStyle(int colorIndex) {
        switch (Math.floorMod(colorIndex, 4)) {
            case 1: return R.style.CellCourse2;
            case 2: return R.style.CellCourse3;
            case 3: return R.style.CellCourse4;
            default: return R.style.CellCourse1;
        }
    }

    private String compactCourseLabel(Course c) {
        if (c.location == null || c.location.trim().isEmpty()) return c.name;
        return c.name + "\n" + c.location;
    }

    private String courseDetails(Course c) {
        StringBuilder text = new StringBuilder(c.name == null ? "课程" : c.name)
                .append(" · ").append(DAYS[c.dayOfWeek - 1])
                .append(" 第").append(c.startSection).append("-").append(c.endSection).append("节")
                .append(" · 第").append(c.startWeek).append("-").append(c.endWeek).append("周");
        if (c.location != null && !c.location.trim().isEmpty()) text.append(" · ").append(c.location);
        if (c.teacher != null && !c.teacher.trim().isEmpty()) text.append(" · ").append(c.teacher);
        return text.toString();
    }

    private void showCourseDialog(int defaultDay, int defaultSection) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_course, null, false);
        EditText etName = content.findViewById(R.id.et_course_name);
        EditText etTeacher = content.findViewById(R.id.et_course_teacher);
        EditText etLocation = content.findViewById(R.id.et_course_location);
        Spinner spDay = content.findViewById(R.id.sp_course_day);
        Spinner spStartSection = content.findViewById(R.id.sp_start_section);
        Spinner spEndSection = content.findViewById(R.id.sp_end_section);
        Spinner spStartWeek = content.findViewById(R.id.sp_start_week);
        Spinner spEndWeek = content.findViewById(R.id.sp_end_week);

        setSpinner(spDay, Arrays.asList(DAYS), defaultDay - 1);
        setSpinner(spStartSection, numberLabels(1, MAX_SECTION, "第", "节"), defaultSection - 1);
        setSpinner(spEndSection, numberLabels(1, MAX_SECTION, "第", "节"), defaultSection - 1);
        setSpinner(spStartWeek, numberLabels(1, MAX_WEEK, "第", "周"), currentWeek - 1);
        setSpinner(spEndWeek, numberLabels(1, MAX_WEEK, "第", "周"), currentWeek - 1);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加课程")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText().toString().trim();
                    int startSection = spStartSection.getSelectedItemPosition() + 1;
                    int endSection = spEndSection.getSelectedItemPosition() + 1;
                    int startWeek = spStartWeek.getSelectedItemPosition() + 1;
                    int endWeek = spEndWeek.getSelectedItemPosition() + 1;
                    if (name.isEmpty()) { etName.setError("请输入课程名称"); return; }
                    if (endSection < startSection || endWeek < startWeek) {
                        Toast.makeText(requireContext(), "结束节次/周次不能早于开始值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Course course = new Course();
                    course.name = name;
                    course.teacher = etTeacher.getText().toString().trim();
                    course.location = etLocation.getText().toString().trim();
                    course.dayOfWeek = spDay.getSelectedItemPosition() + 1;
                    course.startSection = startSection;
                    course.endSection = endSection;
                    course.startWeek = startWeek;
                    course.endWeek = endWeek;
                    course.colorIndex = Math.abs(name.hashCode()) % 4;
                    insertCourse(course, dialog);
                }));
        dialog.show();
    }

    private void insertCourse(Course course, AlertDialog dialog) {
        if (databaseExecutor == null || databaseExecutor.isShutdown()) return;
        Context app = requireContext().getApplicationContext();
        databaseExecutor.execute(() -> {
            int conflicts = AppDatabase.get(app).courseDao().countConflicts(
                    course.dayOfWeek, course.startSection, course.endSection,
                    course.startWeek, course.endWeek);
            if (conflicts == 0) AppDatabase.get(app).courseDao().insert(course);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (conflicts > 0) {
                    Toast.makeText(requireContext(), "该时间段已有课程，请调整节次或周次", Toast.LENGTH_SHORT).show();
                } else {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), "课程已添加", Toast.LENGTH_SHORT).show();
                    loadCourses();
                }
            });
        });
    }

    private void confirmDelete(Course course) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除课程")
                .setMessage("确定删除“" + course.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (databaseExecutor == null || databaseExecutor.isShutdown()) return;
                    Context app = requireContext().getApplicationContext();
                    databaseExecutor.execute(() -> {
                        AppDatabase.get(app).courseDao().delete(course);
                        if (isAdded()) requireActivity().runOnUiThread(this::loadCourses);
                    });
                }).show();
    }

    private void setSpinner(Spinner spinner, List<String> values, int selection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selection, values.size() - 1)));
    }

    private List<String> numberLabels(int from, int to, String prefix, String suffix) {
        List<String> values = new ArrayList<>();
        for (int i = from; i <= to; i++) values.add(prefix + i + suffix);
        return values;
    }

    private void showFirstWeekPicker() {
        Calendar initial = getFirstMonday();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            int offset = (selected.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            selected.add(Calendar.DAY_OF_MONTH, -offset);
            prefs().edit().putLong(KEY_FIRST_MONDAY, selected.getTimeInMillis()).apply();
            Toast.makeText(getContext(), "第一周周一已设置", Toast.LENGTH_SHORT).show();
            applyCurrentWeek();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void applyCurrentWeek() {
        long saved = prefs().getLong(KEY_FIRST_MONDAY, 0L);
        if (saved > 0) {
            long diff = dayStartOf(System.currentTimeMillis()) - dayStartOf(saved);
            currentWeek = Math.max(1, Math.min(MAX_WEEK,
                    (int) Math.floorDiv(diff, 7L * 24 * 60 * 60 * 1000) + 1));
        } else currentWeek = 1;
        refreshWeek();
    }

    private void updateWeekHeader() {
        tvWeek.setText("第 " + currentWeek + " 周");
        long saved = prefs().getLong(KEY_FIRST_MONDAY, 0L);
        if (saved == 0L) {
            tvWeekDates.setText("设置开学日期后显示本周日期");
            return;
        }
        Calendar monday = Calendar.getInstance();
        monday.setTimeInMillis(saved);
        monday.add(Calendar.DAY_OF_MONTH, (currentWeek - 1) * 7);
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);
        SimpleDateFormat md = new SimpleDateFormat("M月d日", Locale.CHINA);
        tvWeekDates.setText(md.format(monday.getTime()) + " — " + md.format(sunday.getTime()));
    }

    private Calendar getFirstMonday() {
        Calendar c = Calendar.getInstance();
        long saved = prefs().getLong(KEY_FIRST_MONDAY, 0L);
        if (saved > 0) c.setTimeInMillis(saved);
        return c;
    }

    private long dayStartOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private SharedPreferences prefs() { return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (databaseExecutor != null) {
            databaseExecutor.shutdownNow();
            databaseExecutor = null;
        }
        table = null;
        tvWeek = null;
        tvWeekDates = null;
    }
}
