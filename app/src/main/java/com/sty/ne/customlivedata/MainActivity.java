package com.sty.ne.customlivedata;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private Button btnLogin;
    private TextView tvText;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        btnLogin = findViewById(R.id.btn_login);
        tvText = findViewById(R.id.tv_text);
        mHandler = new Handler();

        //观察数据变化
        MyEngine.getInstance().getData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                tvText.setText(s);
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyEngine.getInstance().getData().setValue("李四");
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                MyEngine.getInstance().getData().setValue("张三");
            }
        }, 5000);
    }
}