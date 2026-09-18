package com.example.seu_agent.fragment;

import android.content.Context;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Course;
import com.example.seu_agent.CourseSchedule;
import com.example.seu_agent.R;
import com.example.seu_agent.tool.UserToolStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MineFragment extends Fragment {

    private TextView tvAvatar;
    private TextView tvName;
    private TextView tvSubtitle;
    private TextView tvStudentId;
    private TextView tvMajor;
    private TextView tvGrade;
    private TextView tvHobbies;
    private TextView tvFirstMonday;
    private LinearLayout courseList;
    private View mineRoot;
    private View mineScroll;
    private View.OnLayoutChangeListener navLayoutListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            v.post(this::updateScrollBoundary);
            return insets;
        });

        mineRoot = view;
        mineScroll = view.findViewById(R.id.mine_scroll);
        tvAvatar = view.findViewById(R.id.tv_avatar);
        tvName = view.findViewById(R.id.tv_profile_name);
        tvSubtitle = view.findViewById(R.id.tv_profile_subtitle);
        tvStudentId = view.findViewById(R.id.tv_student_id);
        tvMajor = view.findViewById(R.id.tv_major);
        tvGrade = view.findViewById(R.id.tv_grade);
        tvHobbies = view.findViewById(R.id.tv_hobbies);
        tvFirstMonday = view.findViewById(R.id.tv_first_monday);
        courseList = view.findViewById(R.id.ll_courses);
        applyNavBarClearance();

        showProfile();
        showFirstMonday();
        loadCourses();
        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v -> showProfileDialog());
        view.findViewById(R.id.btn_add_course).setOnClickListener(v -> showCourseDialog());
        view.findViewById(R.id.btn_set_first_monday).setOnClickListener(v -> selectFirstMonday());
        view.findViewById(R.id.menu_settings).setOnClickListener(v -> showSettingsMenuDialog());
    }

    private void showProfile() {
        String name = AppConfig.getProfileName(requireContext());
        tvName.setText(name.equals("未填写") ? "未填写姓名" : name);
        tvAvatar.setText(name.equals("未填写") ? "我" : name.substring(0, 1));

        String major = AppConfig.getMajor(requireContext());
        String grade = AppConfig.getGrade(requireContext());
        if (major.equals("未填写") && grade.equals("未填写")) {
            tvSubtitle.setText("完善你的个人信息");
        } else {
            tvSubtitle.setText(major + " · " + grade);
        }

        tvStudentId.setText("学号：" + AppConfig.getStudentId(requireContext()));
        tvMajor.setText("专业：" + major);
        tvGrade.setText("年级：" + grade);
        tvHobbies.setText("爱好：" + AppConfig.getHobbies(requireContext()));
    }

    private void showProfileDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile, null, false);
        EditText name = content.findViewById(R.id.et_profile_name);
        EditText studentId = content.findViewById(R.id.et_profile_student_id);
        EditText major = content.findViewById(R.id.et_profile_major);
        EditText grade = content.findViewById(R.id.et_profile_grade);
        EditText hobbies = content.findViewById(R.id.et_profile_hobbies);

        setProfileText(name, AppConfig.getProfileName(requireContext()));
        setProfileText(studentId, AppConfig.getStudentId(requireContext()));
        setProfileText(major, AppConfig.getMajor(requireContext()));
        setProfileText(grade, AppConfig.getGrade(requireContext()));
        setProfileText(hobbies, AppConfig.getHobbies(requireContext()));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑个人资料")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    AppConfig.saveProfile(requireContext(),
                            name.getText().toString(), studentId.getText().toString(),
                            major.getText().toString(), grade.getText().toString(),
                            hobbies.getText().toString());
                    showProfile();
                    Toast.makeText(requireContext(), "个人资料已保存", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setProfileText(EditText input, String value) {
        input.setText(value.equals("未填写") ? "" : value);
    }

    private void showCourseDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_course, null, false);
        EditText name = content.findViewById(R.id.et_course_name);
        EditText teacher = content.findViewById(R.id.et_course_teacher);
        EditText location = content.findViewById(R.id.et_course_location);
        EditText description = content.findViewById(R.id.et_course_description);
        Spinner day = content.findViewById(R.id.sp_course_day);
        Spinner startSection = content.findViewById(R.id.sp_start_section);
        Spinner endSection = content.findViewById(R.id.sp_end_section);
        Spinner startWeek = content.findViewById(R.id.sp_start_week);
        Spinner endWeek = content.findViewById(R.id.sp_end_week);
        Spinner weekType = content.findViewById(R.id.sp_week_type);

        fillSpinner(day, Arrays.asList(CourseSchedule.DAYS), 0);
        fillSpinner(startSection, sectionLabels(), 0);
        fillSpinner(endSection, sectionLabels(), 1);
        int currentWeek = CourseSchedule.getTeachingWeek(requireContext(), System.currentTimeMillis());
        int selectedWeek = currentWeek == 0 ? 0 : currentWeek - 1;
        fillSpinner(startWeek, weekLabels(), selectedWeek);
        fillSpinner(endWeek, weekLabels(), CourseSchedule.TOTAL_WEEKS - 1);
        fillSpinner(weekType, Arrays.asList("每周", "单周", "双周"), 0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("添加课程")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String courseName = name.getText().toString().trim();
                    if (courseName.isEmpty()) {
                        name.setError("请填写课程名称");
                        return;
                    }
                    Course course = new Course();
                    course.name = courseName;
                    course.teacher = teacher.getText().toString().trim();
                    course.location = location.getText().toString().trim();
                    course.dayOfWeek = day.getSelectedItemPosition() + 1;
                    course.startSection = startSection.getSelectedItemPosition() + 1;
                    course.endSection = endSection.getSelectedItemPosition() + 1;
                    course.startWeek = startWeek.getSelectedItemPosition() + 1;
                    course.endWeek = endWeek.getSelectedItemPosition() + 1;
                    course.weekType = weekType.getSelectedItemPosition();
                    course.description = description.getText().toString().trim();
                    if (course.endSection < course.startSection || course.endWeek < course.startWeek) {
                        Toast.makeText(requireContext(), "结束节次或周次不能早于开始值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveCourse(course);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void saveCourse(Course course) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            course.id = AppDatabase.get(app).courseDao().insert(course);
            if (getActivity() != null) requireActivity().runOnUiThread(this::loadCourses);
        }).start();
    }

    private void loadCourses() {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            List<Course> courses = AppDatabase.get(app).courseDao().getAll();
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> showCourses(courses));
        }).start();
    }

    private void showCourses(List<Course> courses) {
        if (courseList == null) return;
        courseList.removeAllViews();
        if (courses.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("还没有添加课程");
            empty.setTextColor(getResources().getColor(R.color.text_secondary, null));
            empty.setPadding(dp(4), dp(10), 0, dp(10));
            courseList.addView(empty);
            return;
        }

        for (Course course : courses) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_course, courseList, false);
            ((TextView) row.findViewById(R.id.tv_course_name)).setText(course.name);
            ((TextView) row.findViewById(R.id.tv_course_detail)).setText(courseSummary(course));
            row.findViewById(R.id.btn_delete_course).setOnClickListener(v -> deleteCourse(course));
            courseList.addView(row);
        }
    }

    private String courseSummary(Course course) {
        StringBuilder text = new StringBuilder();
        if (course.teacher != null && !course.teacher.isEmpty()) text.append(course.teacher);
        if (text.length() > 0) text.append(" · ");
        text.append(CourseSchedule.DAYS[course.dayOfWeek - 1]).append(" ")
                .append(CourseSchedule.sectionRange(course.startSection, course.endSection));
        text.append(" · 第").append(course.startWeek);
        if (course.endWeek != course.startWeek) text.append("-").append(course.endWeek);
        text.append("周");
        if (course.weekType == 1) text.append("（单周）");
        else if (course.weekType == 2) text.append("（双周）");
        if (course.location != null && !course.location.isEmpty()) {
            if (text.length() > 0) text.append(" · ");
            text.append(course.location);
        }
        return text.length() == 0 ? "暂无补充信息" : text.toString();
    }

    private void fillSpinner(Spinner spinner, List<String> items, int selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
    }

    private List<String> sectionLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i < CourseSchedule.SECTION_TIMES.length; i++) {
            labels.add("第" + i + "节 " + CourseSchedule.SECTION_TIMES[i]);
        }
        return labels;
    }

    private List<String> weekLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= CourseSchedule.TOTAL_WEEKS; i++) labels.add("第" + i + "周");
        return labels;
    }

    private void selectFirstMonday() {
        Calendar initial = Calendar.getInstance();
        long saved = AppConfig.getFirstMonday(requireContext());
        if (saved != 0L) initial.setTimeInMillis(saved);
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            int offset = (selected.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            selected.add(Calendar.DAY_OF_MONTH, -offset);
            AppConfig.setFirstMonday(requireContext(), selected.getTimeInMillis());
            showFirstMonday();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showFirstMonday() {
        long saved = AppConfig.getFirstMonday(requireContext());
        if (saved == 0L) {
            tvFirstMonday.setText("未设置，Tool 暂时无法判断教学周");
            return;
        }
        String date = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(new Date(saved));
        int week = CourseSchedule.getTeachingWeek(requireContext(), System.currentTimeMillis());
        tvFirstMonday.setText(week == 0 ? date : date + " · 当前第" + week + "周");
    }

    private void deleteCourse(Course course) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            AppDatabase.get(app).courseDao().delete(course);
            if (getActivity() != null) requireActivity().runOnUiThread(this::loadCourses);
        }).start();
    }

    private void showSettingsMenuDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_settings_menu, null, false);
        TextView summary = content.findViewById(R.id.tv_tool_config_summary);
        int count = UserToolStore.load(requireContext()).size();
        if (count > 0) summary.setText("已添加 " + count + " 个 HTTP API 工具");

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置")
                .setView(content)
                .setNegativeButton("关闭", null)
                .create();
        content.findViewById(R.id.menu_model_config).setOnClickListener(v -> {
            dialog.dismiss();
            SettingsDialogs.showModelSettings(this);
        });
        content.findViewById(R.id.menu_tool_config).setOnClickListener(v -> {
            dialog.dismiss();
            SettingsDialogs.showHttpTool(this);
        });
        content.findViewById(R.id.menu_prompt_config).setOnClickListener(v -> {
            dialog.dismiss();
            SettingsDialogs.showPromptSettings(this);
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applyNavBarClearance() {
        View nav = requireActivity().findViewById(R.id.navigation);
        if (nav == null) return;
        nav.post(this::updateScrollBoundary);
        navLayoutListener = (v, l, t, r, b, oldL, oldT, oldR, oldB) ->
                updateScrollBoundary();
        nav.addOnLayoutChangeListener(navLayoutListener);
    }

    private void updateScrollBoundary() {
        if (mineRoot == null || mineScroll == null || getActivity() == null) return;
        View nav = getActivity().findViewById(R.id.navigation);
        if (nav == null || mineRoot.getHeight() == 0 || nav.getHeight() == 0) return;

        int[] rootLocation = new int[2];
        int[] navLocation = new int[2];
        mineRoot.getLocationInWindow(rootLocation);
        nav.getLocationInWindow(navLocation);
        int navTopInRoot = navLocation[1] - rootLocation[1];
        int bottomMargin = Math.max(0, mineRoot.getHeight() - navTopInRoot + dp(8));

        ViewGroup.LayoutParams raw = mineScroll.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        if (params.bottomMargin == bottomMargin) return;
        params.bottomMargin = bottomMargin;
        mineScroll.setLayoutParams(params);
    }

    @Override
    public void onDestroyView() {
        if (navLayoutListener != null && getActivity() != null) {
            View nav = getActivity().findViewById(R.id.navigation);
            if (nav != null) nav.removeOnLayoutChangeListener(navLayoutListener);
        }
        navLayoutListener = null;
        super.onDestroyView();
        tvAvatar = null;
        tvName = null;
        tvSubtitle = null;
        tvStudentId = null;
        tvMajor = null;
        tvGrade = null;
        tvHobbies = null;
        tvFirstMonday = null;
        courseList = null;
        mineScroll = null;
        mineRoot = null;
    }
}
