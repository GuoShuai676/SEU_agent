package com.example.seu_agent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import com.example.seu_agent.fragment.AgentFragment;
import com.example.seu_agent.fragment.ClassTableFragment;
import com.example.seu_agent.fragment.MineFragment;
import com.example.seu_agent.fragment.NewsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'seu_agent' library on application startup.
    static {
        System.loadLibrary("seu_agent");
    }
    List<Fragment> list;
    BottomNavigationView bottomnavigation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomnavigation=findViewById(R.id.navigation);
        list = new ArrayList<>();
        list.add(new NewsFragment());
        list.add(new AgentFragment());
        list.add(new ClassTableFragment());
        list.add(new MineFragment());
        ShowFragment(list.get(0));

        bottomnavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id=item.getItemId();
                if(id==R.id.menu_news)
                    ShowFragment(list.get(0));
                else if (id==R.id.menu_agent)
                    ShowFragment(list.get(1));
                else if (id==R.id.menu_classtable) {
                    ShowFragment(list.get(2));
                }
                else ShowFragment(list.get(3));
                return true;
            }
        });
    }


    private void ShowFragment(Fragment f)
    {
        FragmentManager fragmentmanager=getSupportFragmentManager();
        FragmentTransaction ft=fragmentmanager.beginTransaction();
        ft.replace(R.id.container,f);
        ft.commit();
    }


    /**
     * A native method that is implemented by the 'seu_agent' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}