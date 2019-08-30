package com.bajie.uvccamera.rtmplive.base;

import android.app.Activity;
import android.os.Bundle;

import com.bajie.uvccamera.rtmplive.callback.IBase;

/**
 * desc: 基类activity
 * create at: 2016/9/19 9:41
 * create by: yyw
 */
public abstract class BaseActivity extends Activity implements IBase {
    protected Activity activity;

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
