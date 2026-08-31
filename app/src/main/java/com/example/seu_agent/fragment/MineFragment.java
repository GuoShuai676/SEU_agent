package com.example.seu_agent.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.seu_agent.AppConfig;
import com.example.seu_agent.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 我的页：个人信息 + 设置入口（服务器 URL / 模型 / 模型 URL / API Key）。
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

        // 只给顶部状态栏留高度，底部不加 padding（避免与底部导航之间出现空隙）
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        // 点"设置"打开配置对话框
        view.findViewById(R.id.menu_settings).setOnClickListener(v -> showSettingsDialog());
    }

    /** 设置对话框：服务器 URL / 模型 / 模型 URL / API Key */
    private void showSettingsDialog() {
        // 加载对话框布局（经典写法：findViewById）
        View dlg = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_settings, null, false);

        EditText etServerUrl = dlg.findViewById(R.id.et_server_url);
        EditText etLlmUrl = dlg.findViewById(R.id.et_llm_url);
        EditText etApiKey = dlg.findViewById(R.id.et_api_key);
        Spinner spModel = dlg.findViewById(R.id.sp_model);

        // 回填当前值
        etServerUrl.setText(AppConfig.getServerUrl(requireContext()));
        etLlmUrl.setText(AppConfig.getLlmUrl(requireContext()));
        etApiKey.setText(AppConfig.getApiKey(requireContext()));

        String[] models = {"deepseek-chat", "deepseek-reasoner"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(adapter);
        spModel.setSelection(AppConfig.getLlmModel(requireContext()).equals("deepseek-reasoner") ? 1 : 0);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("设置")
                .setView(dlg)
                .setPositiveButton("保存", (d, w) -> {
                    AppConfig.save(requireContext(),
                            etServerUrl.getText().toString(),
                            (String) spModel.getSelectedItem(),
                            etLlmUrl.getText().toString(),
                            etApiKey.getText().toString());
                    Toast.makeText(getContext(), "已保存，立即生效", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
