package com.sty.ne.customlivedata;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.os.Bundle;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {
    private TextView tvText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initView();
    }

    private void initView() {
        tvText = findViewById(R.id.tv_text);

        //监听数据变化
        MyEngine.getInstance().getData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                tvText.setText(s);
            }
        });
    }
}