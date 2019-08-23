package com.bajie.uvccamera.rtmplive.base;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.bajie.uvccamera.rtmplive.callback.IBase;

/**
 * desc: 基类activity
 * create at: 2016/9/19 9:41
 * create by: yyw
 */
public abstract class BaseActivity extends AppCompatActivity implements IBase {
    protected AppCompatActivity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this;
        setContentView(getLayout());
        initView(savedInstanceState);
        initData();
        setListener();
    }

    @Override
    public void initData() {

    }

    @Override
    public void setListener() {

    }

}
