package com.example.seu_agent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.View;

import com.example.seu_agent.fragment.AgentFragment;
import com.example.seu_agent.fragment.MineFragment;
import com.example.seu_agent.fragment.NewsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String NEWS_TAG = "news";
    private static final String AGENT_TAG = "agent";
    private static final String MINE_TAG = "mine";

    static {
        System.loadLibrary("seu_agent");
    }
    private Fragment newsFragment;
    private Fragment agentFragment;
    private Fragment mineFragment;
    private Fragment currentFragment;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomNavigation = findViewById(R.id.navigation);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            newsFragment = new NewsFragment();
            agentFragment = new AgentFragment();
            mineFragment = new MineFragment();
            currentFragment = newsFragment;

            fm.beginTransaction()
                    .add(R.id.container, newsFragment, NEWS_TAG)
                    .add(R.id.container, agentFragment, AGENT_TAG).hide(agentFragment)
                    .add(R.id.container, mineFragment, MINE_TAG).hide(mineFragment)
                    .commit();
        } else {
            newsFragment = fm.findFragmentByTag(NEWS_TAG);
            agentFragment = fm.findFragmentByTag(AGENT_TAG);
            mineFragment = fm.findFragmentByTag(MINE_TAG);

            if (agentFragment != null && !agentFragment.isHidden())
                currentFragment = agentFragment;
            else if (mineFragment != null && !mineFragment.isHidden())
                currentFragment = mineFragment;
            else
                currentFragment = newsFragment;
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            View capsule = bottomNavigation;
            if (capsule != null) {
                capsule.animate().cancel();
                if (imeVisible) {
                    capsule.animate().alpha(0f).setDuration(180)
                            .withEndAction(() -> {
                                if (capsule.getAlpha() == 0f) capsule.setVisibility(View.GONE);
                            });
                } else {
                    capsule.setVisibility(View.VISIBLE);
                    capsule.animate().alpha(1f).setDuration(180);
                }
            }
            return insets;
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_news) showFragment(newsFragment);
            else if (id == R.id.menu_agent) showFragment(agentFragment);
            else showFragment(mineFragment);
            return true;
        });
    }


    private void showFragment(Fragment fragment) {
        if (fragment == null || fragment == currentFragment) return;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (currentFragment != null) ft.hide(currentFragment);
        ft.show(fragment).commit();
        currentFragment = fragment;
    }
}
