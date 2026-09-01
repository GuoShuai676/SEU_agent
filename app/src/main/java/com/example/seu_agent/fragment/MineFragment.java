package com.example.seu_agent.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
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

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 我的页面，尚未开发
 */
public class MineFragment extends Fragment {

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
            return insets;
        });

        // 设置的按钮监听
        view.findViewById(R.id.menu_settings).setOnClickListener(v -> showSettingsDialog());
    }

    /** 设置弹出的URL配置对话框 */
    private void showSettingsDialog() {

        View dlg = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_settings, null, false);

        EditText etLlmUrl = dlg.findViewById(R.id.et_llm_url);
        EditText etApiKey = dlg.findViewById(R.id.et_api_key);
        LinearLayout llModels = dlg.findViewById(R.id.ll_models);
        EditText etNewModel = dlg.findViewById(R.id.et_new_model);

        etLlmUrl.setText(AppConfig.getLlmUrl(requireContext()));
        etApiKey.setText(AppConfig.getApiKey(requireContext()));

        refreshModels(llModels);

        dlg.findViewById(R.id.btn_add_model).setOnClickListener(v -> {
            String name = etNewModel.getText().toString().trim();
            if (name.isEmpty()) return;
            AppConfig.addModel(requireContext(), name);
            AppConfig.setModel(requireContext(), name);
            etNewModel.setText("");
            refreshModels(llModels);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dlg)
                .setPositiveButton("保存", (d, w) -> {
                    AppConfig.save(requireContext(),
                            AppConfig.getLlmModel(requireContext()),
                            etLlmUrl.getText().toString(),
                            etApiKey.getText().toString());
                    Toast.makeText(getContext(), "已保存，立即生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();

        Window window = dialog.getWindow();
        if (window != null) {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int width = Math.min((int) (screenW * 0.88f), dp(420));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }


    private void refreshModels(LinearLayout container) {
        List<String> models = AppConfig.getModels(requireContext());
        String current = AppConfig.getLlmModel(requireContext());
        container.removeAllViews();
        for (String name : models) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_model_row, container, false);
            TextView tvName = row.findViewById(R.id.tv_model_name);
            TextView tvCheck = row.findViewById(R.id.tv_model_check);
            tvName.setText(name);
            boolean active = name.equals(current);
            tvName.setTextColor(getResources().getColor(
                    active ? R.color.brand_primary : R.color.text_primary, null));
            tvName.setTypeface(tvName.getTypeface(), active ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            tvCheck.setVisibility(active ? View.VISIBLE : View.GONE);

            row.setOnClickListener(v -> {
                AppConfig.setModel(requireContext(), name);
                refreshModels(container);
            });
            row.findViewById(R.id.btn_del_model).setOnClickListener(v -> {
                if (AppConfig.getModels(requireContext()).size() <= 1) {
                    Toast.makeText(getContext(), "至少保留一个模型", Toast.LENGTH_SHORT).show();
                    return;
                }
                AppConfig.removeModel(requireContext(), name);
                if (name.equals(AppConfig.getLlmModel(requireContext()))) {

                    List<String> rest = AppConfig.getModels(requireContext());
                    AppConfig.setModel(requireContext(), rest.isEmpty() ? AppConfig.DEFAULT_MODEL : rest.get(0));
                }
                refreshModels(container);
            });
            container.addView(row);
        }
    }
}
