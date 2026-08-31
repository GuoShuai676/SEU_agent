package com.example.seu_agent.fragment;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.seu_agent.R;

import java.util.Calendar;

/**
 * 课表页：表格在 XML 里写死（外层以天为单位，每天 13 节课）。
 * 本类只负责：① 左右切换周数 ② 设置第一周日期 ③ 打开时按今天自动跳转当前周。
 */
public class ClassTableFragment extends Fragment {

    private static final String PREFS = "classtable_prefs";
    private static final String KEY_FIRST_MONDAY = "first_monday";

    private TextView tvWeek;
    private int currentWeek = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_classtable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 只给顶部状态栏留高度，底部不加 padding（避免与底部导航之间出现空隙）
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        tvWeek = view.findViewById(R.id.tv_week);

        // 打开页面：按"第一周日期"自动跳到当前周
        applyCurrentWeek();

        TextView btnPrev = view.findViewById(R.id.btn_prev_week);
        TextView btnNext = view.findViewById(R.id.btn_next_week);
        TextView btnSetDate = view.findViewById(R.id.btn_set_first_date);

        btnPrev.setOnClickListener(v -> {
            if (currentWeek > 1) {
                currentWeek--;
                tvWeek.setText("第 " + currentWeek + " 周");
            }
        });
        btnNext.setOnClickListener(v -> {
            currentWeek++;
            tvWeek.setText("第 " + currentWeek + " 周");
        });
        btnSetDate.setOnClickListener(v -> showFirstWeekPicker());
    }

    /** 弹出日期选择框：设置第一周（开学周）的日期 */
    private void showFirstWeekPicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar first = Calendar.getInstance();
            first.set(year, month, day, 0, 0, 0);
            first.set(Calendar.MILLISECOND, 0);
            prefs().edit().putLong(KEY_FIRST_MONDAY, first.getTimeInMillis()).apply();
            Toast.makeText(getContext(),
                    "第一周已设为 " + (month + 1) + " 月 " + day + " 日", Toast.LENGTH_SHORT).show();
            applyCurrentWeek();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** 按今天日期自动计算当前是第几周 */
    private void applyCurrentWeek() {
        long firstMonday = prefs().getLong(KEY_FIRST_MONDAY, 0L);
        if (firstMonday > 0) {
            long today = dayStartOf(System.currentTimeMillis());
            long first = dayStartOf(firstMonday);
            int diffDays = (int) ((today - first) / (1000L * 60 * 60 * 24));
            currentWeek = Math.max(1, diffDays / 7 + 1);
        } else {
            currentWeek = 1;
        }
        tvWeek.setText("第 " + currentWeek + " 周");
    }

    /** 去掉时分秒，只保留日期（避免跨天误差） */
    private long dayStartOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
