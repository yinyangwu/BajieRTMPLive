package com.bajie.uvccamera.rtmplive.activity;

import android.content.Intent;
import android.view.View;
import android.widget.Button;

import com.bajie.uvccamera.rtmplive.R;
import com.bajie.uvccamera.rtmplive.base.BaseActivity;
import com.bajie.uvccamera.rtmplive.util.ToastUtils;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

/**
 * Desc:UVCCamera直播
 * <p>
 * Created by YoungWu on 2019/7/8.
 */
public class MainActivity extends BaseActivity implements View.OnClickListener {
    /**
     * 动态申请的权限
     */
    private static final String[] PERMISSIONS = {Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE,
            Permission.CAMERA, Permission.RECORD_AUDIO};

    private Button btn_internal_camera_live, btn_external_camera_live;

    @Override
    public int getLayout() {
        return R.layout.activity_main;
    }

    @Override
    public void initView(Object obj) {
        btn_internal_camera_live = findViewById(R.id.btn_internal_camera_live);
        btn_external_camera_live = findViewById(R.id.btn_external_camera_live);
    }

    @Override
    public void initData() {
        super.initData();
        requestPermission();
    }

    /**
     * 请求权限
     */
    private void requestPermission() {
        if (!XXPermissions.isHasPermission(MainActivity.this, PERMISSIONS)) {
            XXPermissions.with(this).constantRequest().permission(PERMISSIONS)
                    .request(new OnPermission() {
                        @Override
                        public void hasPermission(List<String> granted, boolean isAll) {
                            if (isAll) {
                                ToastUtils.toast(MainActivity.this, "获取权限成功");
                            } else {
                                ToastUtils.toast(MainActivity.this, "获取权限成功，部分权限未正常授予");
                            }
                        }

                        @Override
                        public void noPermission(List<String> denied, boolean quick) {
                            if (quick) {
                                ToastUtils.toast(MainActivity.this, "被永久拒绝授权，请手动授予权限");
                                //如果是被永久拒绝就跳转到应用权限系统设置页面
                                XXPermissions.gotoPermissionSettings(MainActivity.this);
                            } else {
                                ToastUtils.toast(MainActivity.this, "获取权限失败");
                            }
                        }
                    });
        }
    }

    @Override
    public void setListener() {
        super.setListener();
        btn_internal_camera_live.setOnClickListener(this);
        btn_external_camera_live.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_internal_camera_live:
                //打开机身摄像头直播页面
                openInternalCameraLiveActivity();
                break;
            case R.id.btn_external_camera_live:
                //打开外置摄像头直播页面
                openExternalCameraLiveActivity();
                break;
        }
    }

    /**
     * 打开机身摄像头直播页面
     */
    private void openInternalCameraLiveActivity() {
        Intent intent = new Intent(this, InternalCameraLiveActivity.class);
        startActivity(intent);
    }

    /**
     * 打开外置摄像头直播页面
     */
    private void openExternalCameraLiveActivity() {
        Intent intent = new Intent(this, ExternalCameraLiveActivity.class);
        startActivity(intent);
    }
}
