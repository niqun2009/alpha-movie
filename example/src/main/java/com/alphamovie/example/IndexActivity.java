package com.alphamovie.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

/**
 * Created by luotian on 2018/1/5.
 */

public class IndexActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);

        findViewById(R.id.shader_mode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IndexActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.above_mode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IndexActivity.this, IndependentActivity.class);
                startActivity(intent);
            }
        });
    }
}
