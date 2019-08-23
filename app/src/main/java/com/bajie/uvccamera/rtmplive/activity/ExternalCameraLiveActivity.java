package com.bajie.uvccamera.rtmplive.activity;

import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bajie.uvccamera.rtmplive.R;
import com.bajie.uvccamera.rtmplive.base.BaseActivity;
import com.bajie.uvccamera.rtmplive.util.TestConfig;
import com.bajie.uvccamera.rtmplive.util.ToastUtils;
import com.github.faucamp.simplertmp.RtmpHandler;
import com.orhanobut.logger.Logger;
import com.serenegiant.usb.USBMonitor;
import com.seu.magicfilter.utils.MagicFilterType;
import com.youngwu.live.UVCCameraGLSurfaceView;
import com.youngwu.live.UVCPublisher;
import com.zhy.adapter.recyclerview.CommonAdapter;
import com.zhy.adapter.recyclerview.base.ViewHolder;

import net.ossrs.yasea.SrsEncodeHandler;
import net.ossrs.yasea.SrsLiveConfig;
import net.ossrs.yasea.SrsRecordHandler;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Desc:外置摄像头直播，UVC摄像头因没有setDisplayOrientation方法，故不能竖屏预览
 * <p>
 * Created by YoungWu on 2019/7/8.
 */
public class ExternalCameraLiveActivity extends BaseActivity implements View.OnClickListener {
    private UVCCameraGLSurfaceView uvcCameraGLSurfaceView;
    private TextView tv_info;
    private Button btn_start;
    private Button btn_soft_encoder;
    private Button btn_video_hd_mode;
    private LinearLayout ll_choose_definition;
    private Button btn_normal_definition;
    private Button btn_high_definition;
    private Button btn_full_high_definition;
    private Button btn_close_choose_definition;
    private Button btn_magic_filter;
    private LinearLayout ll_choose_magic_filter;
    private RecyclerView rv_magic_filter_list;
    private Button btn_close_choose_magic_filter;
    private Button btn_send_video_only;
    private LinearLayout ll_choose_transform_type;
    private Button btn_transform_audio;
    private Button btn_transform_video;
    private Button btn_transform_audio_video;
    private Button btn_close_choose_transform_type;
    private Button btn_start_record_video;

    private USBMonitor usbMonitor;
    private USBMonitor.UsbControlBlock mCtrlBlock;
    private UVCPublisher publisher;
    private static Handler handler = new Handler();
    private int flag = 0;

    private int currentPreviewWidth;
    private int currentPreviewHeight;
    private int currentOrientation;
    private MagicFilterType currentMagicFilterType;
    private int currentTransformType;
    private int currentEncodeType;
    private int currentVideoMode;

    @Override
    public int getLayout() {
        return R.layout.activity_external_camera_live;
    }

    @Override
    public void initView(Object obj) {
        uvcCameraGLSurfaceView = findViewById(R.id.uvcCameraView);
        tv_info = findViewById(R.id.tv_info);
        btn_start = findViewById(R.id.btn_start);
        btn_soft_encoder = findViewById(R.id.btn_soft_encoder);
        btn_video_hd_mode = findViewById(R.id.btn_video_hd_mode);
        ll_choose_definition = findViewById(R.id.ll_choose_definition);
        btn_normal_definition = findViewById(R.id.btn_normal_definition);
        btn_high_definition = findViewById(R.id.btn_high_definition);
        btn_full_high_definition = findViewById(R.id.btn_full_high_definition);
        btn_close_choose_definition = findViewById(R.id.btn_close_choose_definition);
        btn_magic_filter = findViewById(R.id.btn_magic_filter);
        ll_choose_magic_filter = findViewById(R.id.ll_choose_magic_filter);
        rv_magic_filter_list = findViewById(R.id.rv_magic_filter_list);
        btn_close_choose_magic_filter = findViewById(R.id.btn_close_choose_magic_filter);
        btn_send_video_only = findViewById(R.id.btn_send_video_only);
        ll_choose_transform_type = findViewById(R.id.ll_choose_transform_type);
        btn_transform_audio = findViewById(R.id.btn_transform_audio);
        btn_transform_video = findViewById(R.id.btn_transform_video);
        btn_transform_audio_video = findViewById(R.id.btn_transform_audio_video);
        btn_close_choose_transform_type = findViewById(R.id.btn_close_choose_transform_type);
        btn_start_record_video = findViewById(R.id.btn_start_record_video);
    }

    @Override
    public void initData() {
        super.initData();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        usbMonitor = new USBMonitor(this, onDeviceConnectListener);
        initUVCPublisher();
        initMagicFilterList();
    }

    private USBMonitor.OnDeviceConnectListener onDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Logger.e("USBMonitor:onAttach");
            //请求获取外部USB设备临时权限
            usbMonitor.requestPermission(device);
        }

        @Override
        public void onDetach(UsbDevice device) {
            Logger.e("USBMonitor:onDetach");
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Logger.e("USBMonitor:onConnect");
            if (mCtrlBlock == null) {
                mCtrlBlock = ctrlBlock;
                runOnUiThread(() -> {
                    btn_start.setText("结束直播");
                    startLive(mCtrlBlock);
                });
            }
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            Logger.e("USBMonitor:onDisconnect");
            if (mCtrlBlock != null) {
                mCtrlBlock = null;
                runOnUiThread(() -> {
                    btn_start.setText("开始直播");
                    stopLive();
                });
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
            Logger.e("USBMonitor:onCancel");
        }
    };

    /**
     * 初始化推流器
     */
    private void initUVCPublisher() {
        currentPreviewWidth = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
        currentPreviewHeight = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
        currentOrientation = Configuration.ORIENTATION_LANDSCAPE;
        currentMagicFilterType = MagicFilterType.NONE;
        currentTransformType = 3;
        currentEncodeType = 0;
        currentVideoMode = 2;

        publisher = new UVCPublisher(uvcCameraGLSurfaceView);
        publisher.setEncodeHandler(new SrsEncodeHandler(srsEncodeListener));
        publisher.setRecordHandler(new SrsRecordHandler(srsRecordListener));
        publisher.setRtmpHandler(new RtmpHandler(rtmpListener));
        publisher.setErrorCallback(errorCallback);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (flag % 2 == 0) {
                tv_info.setTextColor(Color.RED);
            } else {
                tv_info.setTextColor(Color.BLUE);
            }
            flag++;
            tv_info.setText("帧率：");
            tv_info.append(String.format(Locale.CHINESE, "%.2f", publisher.getSamplingFps()));
            tv_info.append("fps");
            handler.postDelayed(runnable, 1000);
        }
    };

    /**
     * 初始化滤镜效果
     */
    private void initMagicFilterList() {
        List<MagicFilterType> list = new ArrayList<>();
        list.add(MagicFilterType.NONE);
        list.add(MagicFilterType.SUNRISE);
        list.add(MagicFilterType.SUNSET);
        list.add(MagicFilterType.WHITECAT);
        list.add(MagicFilterType.BLACKCAT);
        list.add(MagicFilterType.SKINWHITEN);
        list.add(MagicFilterType.BEAUTY);
        list.add(MagicFilterType.HEALTHY);
        list.add(MagicFilterType.ROMANCE);
        list.add(MagicFilterType.SAKURA);
        list.add(MagicFilterType.WARM);
        list.add(MagicFilterType.ANTIQUE);
        list.add(MagicFilterType.NOSTALGIA);
        list.add(MagicFilterType.CALM);
        list.add(MagicFilterType.LATTE);
        list.add(MagicFilterType.TENDER);
        list.add(MagicFilterType.COOL);
        list.add(MagicFilterType.EMERALD);
        list.add(MagicFilterType.EVERGREEN);
        list.add(MagicFilterType.SKETCH);
        list.add(MagicFilterType.AMARO);
        list.add(MagicFilterType.BRANNAN);
        list.add(MagicFilterType.BROOKLYN);
        list.add(MagicFilterType.EARLYBIRD);
        list.add(MagicFilterType.FREUD);
        list.add(MagicFilterType.HUDSON);
        list.add(MagicFilterType.INKWELL);
        list.add(MagicFilterType.KEVIN);
        list.add(MagicFilterType.N1977);
        list.add(MagicFilterType.NASHVILLE);
        list.add(MagicFilterType.PIXAR);
        list.add(MagicFilterType.RISE);
        list.add(MagicFilterType.SIERRA);
        list.add(MagicFilterType.SUTRO);
        list.add(MagicFilterType.TOASTER2);
        list.add(MagicFilterType.VALENCIA);
        list.add(MagicFilterType.WALDEN);
        list.add(MagicFilterType.XPROII);

        CommonAdapter<MagicFilterType> adapter = new CommonAdapter<MagicFilterType>(this, R.layout.layout_magic_filter_item, list) {
            @Override
            protected void convert(ViewHolder holder, MagicFilterType magicFilterType, int position) {
                holder.setText(R.id.btn_magic_filter_item, magicFilterType.toString());
                if (currentMagicFilterType == magicFilterType) {
                    holder.setTextColor(R.id.btn_magic_filter_item, Color.RED);
                } else {
                    holder.setTextColor(R.id.btn_magic_filter_item, Color.BLACK);
                }
                holder.setOnClickListener(R.id.btn_magic_filter_item, v -> {
                    currentMagicFilterType = magicFilterType;
                    notifyDataSetChanged();
                    publisher.switchCameraFilter(magicFilterType);
                    ll_choose_magic_filter.setVisibility(View.GONE);
                });
            }
        };
        rv_magic_filter_list.setLayoutManager(new LinearLayoutManager(this));
        rv_magic_filter_list.setAdapter(adapter);
    }

    @Override
    public void setListener() {
        super.setListener();
        btn_start.setOnClickListener(this);
        btn_soft_encoder.setOnClickListener(this);
        btn_video_hd_mode.setOnClickListener(this);
        ll_choose_definition.setOnClickListener(this);
        btn_normal_definition.setOnClickListener(this);
        btn_high_definition.setOnClickListener(this);
        btn_full_high_definition.setOnClickListener(this);
        btn_close_choose_definition.setOnClickListener(this);
        btn_magic_filter.setOnClickListener(this);
        ll_choose_magic_filter.setOnClickListener(this);
        btn_close_choose_magic_filter.setOnClickListener(this);
        btn_send_video_only.setOnClickListener(this);
        ll_choose_transform_type.setOnClickListener(this);
        btn_transform_audio.setOnClickListener(this);
        btn_transform_video.setOnClickListener(this);
        btn_transform_audio_video.setOnClickListener(this);
        btn_close_choose_transform_type.setOnClickListener(this);
        btn_start_record_video.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start:
                //开始或结束直播
                if (mCtrlBlock != null) {
                    if (btn_start.getText().toString().equals("结束直播")) {
                        btn_start.setText("开始直播");
                        stopLive();
                    } else {
                        btn_start.setText("结束直播");
                        startLive(mCtrlBlock);
                    }
                }
                break;
            case R.id.btn_soft_encoder:
                //切换软件编码或者硬件编码
                if (mCtrlBlock != null) {
                    if (btn_start.getText().toString().equals("结束直播")) {
                        stopLive();
                        if (btn_soft_encoder.getText().toString().equals("硬件编码")) {
                            btn_soft_encoder.setText("软件编码");
                            currentEncodeType = 0;
                        } else {
                            btn_soft_encoder.setText("硬件编码");
                            currentEncodeType = 1;
                        }
                        startLive(mCtrlBlock);
                    }
                }
                break;
            case R.id.btn_video_hd_mode:
                //打开选择分辨率
                if (btn_start.getText().toString().equals("结束直播")) {
                    ll_choose_definition.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.btn_normal_definition:
                //标清
                if (mCtrlBlock != null) {
                    stopLive();
                    currentPreviewWidth = SrsLiveConfig.STANDARD_DEFINITION_WIDTH;
                    currentPreviewHeight = SrsLiveConfig.STANDARD_DEFINITION_HEIGHT;
                    currentVideoMode = 1;
                    btn_video_hd_mode.setText("标清");
                    ll_choose_definition.setVisibility(View.GONE);
                    startLive(mCtrlBlock);
                }
                break;
            case R.id.btn_high_definition:
                //高清
                if (mCtrlBlock != null) {
                    stopLive();
                    currentPreviewWidth = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
                    currentPreviewHeight = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
                    currentVideoMode = 2;
                    btn_video_hd_mode.setText("高清");
                    ll_choose_definition.setVisibility(View.GONE);
                    startLive(mCtrlBlock);
                }
                break;
            case R.id.btn_full_high_definition:
                //全高清
                if (mCtrlBlock != null) {
                    stopLive();
                    currentPreviewWidth = SrsLiveConfig.FULL_HIGH_DEFINITION_WIDTH;
                    currentPreviewHeight = SrsLiveConfig.FULL_HIGH_DEFINITION_HEIGHT;
                    currentVideoMode = 3;
                    btn_video_hd_mode.setText("全高清");
                    ll_choose_definition.setVisibility(View.GONE);
                    startLive(mCtrlBlock);
                }
                break;
            case R.id.btn_close_choose_definition:
                //关闭选择分辨率
                ll_choose_definition.setVisibility(View.GONE);
                break;
            case R.id.btn_magic_filter:
                //打开选择滤镜
                if (btn_start.getText().toString().equals("结束直播")) {
                    ll_choose_magic_filter.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.btn_close_choose_magic_filter:
                //关闭选择滤镜
                ll_choose_magic_filter.setVisibility(View.GONE);
                break;
            case R.id.btn_send_video_only:
                //打开选择传输类型
                if (btn_start.getText().toString().equals("结束直播")) {
                    ll_choose_transform_type.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.btn_transform_audio:
                //只传音频
                if (!btn_send_video_only.getText().toString().equals("音频")) {
                    btn_send_video_only.setText("音频");
                    currentTransformType = 1;
                    publisher.setSendVideoOnly(false);
                    publisher.setSendAudioOnly(true);
                }
                ll_choose_transform_type.setVisibility(View.GONE);
                break;
            case R.id.btn_transform_video:
                //只传视频
                if (!btn_send_video_only.getText().toString().equals("视频")) {
                    btn_send_video_only.setText("视频");
                    currentTransformType = 2;
                    publisher.setSendVideoOnly(true);
                    publisher.setSendAudioOnly(false);
                }
                ll_choose_transform_type.setVisibility(View.GONE);
                break;
            case R.id.btn_transform_audio_video:
                //音频+视频
                if (!btn_send_video_only.getText().toString().equals("音频+视频")) {
                    btn_send_video_only.setText("音频+视频");
                    currentTransformType = 3;
                    publisher.setSendVideoOnly(false);
                    publisher.setSendAudioOnly(false);
                }
                ll_choose_transform_type.setVisibility(View.GONE);
                break;
            case R.id.btn_close_choose_transform_type:
                //关闭选择传输类型
                ll_choose_transform_type.setVisibility(View.GONE);
                break;
            case R.id.btn_start_record_video:
                //录制视频
                if (btn_start.getText().toString().equals("结束直播")) {
                    if (btn_start_record_video.getText().toString().equals("结束录制")) {
                        btn_start_record_video.setText("开始录制");
                        publisher.stopRecord();
                    } else {
                        btn_start_record_video.setText("结束录制");
                        publisher.startRecord(Environment.getExternalStorageDirectory().getPath() + "/test.mp4");
                    }
                }
                break;
        }
    }

    /**
     * 开始直播
     */
    private void startLive(USBMonitor.UsbControlBlock ctrlBlock) {
        publisher.openCamera(ctrlBlock);
        publisher.setPreviewResolution(currentPreviewWidth, currentPreviewHeight);
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            publisher.setOutputResolution(currentPreviewHeight, currentPreviewWidth);
        } else {
            publisher.setOutputResolution(currentPreviewWidth, currentPreviewHeight);
        }
        publisher.setScreenOrientation(currentOrientation);
        publisher.switchCameraFilter(currentMagicFilterType);
        if (currentVideoMode == 1) {
            publisher.setVideoSmoothMode();
        } else if (currentVideoMode == 2) {
            publisher.setVideoHDMode();
        } else if (currentVideoMode == 3) {
            publisher.setVideoFullHDMode();
        }
        if (currentEncodeType == 0) {
            publisher.switchToHardEncoder();
        } else if (currentEncodeType == 1) {
            publisher.switchToSoftEncoder();
        }
        if (currentTransformType == 1) {
            publisher.setSendAudioOnly(true);
            publisher.setSendVideoOnly(false);
        } else if (currentTransformType == 2) {
            publisher.setSendAudioOnly(false);
            publisher.setSendVideoOnly(true);
        } else if (currentTransformType == 3) {
            publisher.setSendAudioOnly(false);
            publisher.setSendVideoOnly(false);
        }

        publisher.startPublish(TestConfig.TEST_URL);
        handler.post(runnable);
    }

    /**
     * 结束直播
     */
    private void stopLive() {
        if (btn_start_record_video.getText().toString().equals("结束录制")) {
            btn_start_record_video.setText("开始录制");
            publisher.stopRecord();
        }
        publisher.stopPublish();
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * 处理异常情况
     */
    private void handleException() {
        btn_start.setText("开始直播");
        stopLive();
    }

    private SrsEncodeHandler.SrsEncodeListener srsEncodeListener = new SrsEncodeHandler.SrsEncodeListener() {
        @Override
        public void onNetworkWeak() {
            Logger.e("onNetworkWeak");
            ToastUtils.toast(ExternalCameraLiveActivity.this, "onNetworkWeak");
        }

        @Override
        public void onNetworkResume() {
            Logger.e("onNetworkResume");
            ToastUtils.toast(ExternalCameraLiveActivity.this, "onNetworkResume");
        }

        @Override
        public void onEncodeIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onEncodeIllegalArgumentException");
            ToastUtils.toast(ExternalCameraLiveActivity.this, "onEncodeIllegalArgumentException");
            handleException();
        }
    };

    private RtmpHandler.RtmpListener rtmpListener = new RtmpHandler.RtmpListener() {
        @Override
        public void onRtmpConnecting(String msg) {
            Logger.e("onRtmpConnecting:" + msg);
        }

        @Override
        public void onRtmpConnected(String msg) {
            Logger.e("onRtmpConnected:" + msg);
        }

        @Override
        public void onRtmpVideoStreaming() {
//            Logger.e("onRtmpVideoStreaming");
        }

        @Override
        public void onRtmpAudioStreaming() {
//            Logger.e("onRtmpAudioStreaming");
        }

        @Override
        public void onRtmpStopped() {
            Logger.e("onRtmpStopped");
        }

        @Override
        public void onRtmpDisconnected() {
            Logger.e("onRtmpDisconnected");
        }

        @Override
        public void onRtmpVideoFpsChanged(double fps) {
//            Logger.e("onRtmpVideoFpsChanged:" + fps + "fps");
        }

        @Override
        public void onRtmpVideoBitrateChanged(double bitrate) {
//            int rate = (int) bitrate / 1000;
//            if (rate > 0) {
//                Logger.e("onRtmpVideoBitrateChanged:" + rate + "kbps");
//            } else {
//                Logger.e("onRtmpVideoBitrateChanged:" + bitrate + "bps");
//            }
        }

        @Override
        public void onRtmpAudioBitrateChanged(double bitrate) {
//            int rate = (int) bitrate / 1000;
//            if (rate > 0) {
//                Logger.e("onRtmpAudioBitrateChanged:" + rate + "kbps");
//            } else {
//                Logger.e("onRtmpAudioBitrateChanged:" + bitrate + "bps");
//            }
        }

        @Override
        public void onRtmpSocketException(SocketException e) {
            Logger.e(e, "onRtmpSocketException");
            handleException();
        }

        @Override
        public void onRtmpIOException(IOException e) {
            Logger.e(e, "onRtmpIOException");
            handleException();
        }

        @Override
        public void onRtmpIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onRtmpIllegalArgumentException");
            handleException();
        }

        @Override
        public void onRtmpIllegalStateException(IllegalStateException e) {
            Logger.e(e, "onRtmpIllegalStateException");
            handleException();
        }
    };

    private SrsRecordHandler.SrsRecordListener srsRecordListener = new SrsRecordHandler.SrsRecordListener() {
        @Override
        public void onRecordPause() {
            Logger.e("onRecordPause");
        }

        @Override
        public void onRecordResume() {
            Logger.e("onRecordResume");
        }

        @Override
        public void onRecordStarted(String msg) {
            Logger.e("onRecordStarted:" + msg);
        }

        @Override
        public void onRecordFinished(String msg) {
            Logger.e("onRecordFinished:" + msg);
        }

        @Override
        public void onRecordIllegalArgumentException(IllegalArgumentException e) {
            Logger.e(e, "onRecordIllegalArgumentException");
            handleException();
        }

        @Override
        public void onRecordIOException(IOException e) {
            Logger.e(e, "onRecordIOException");
            handleException();
        }
    };

    private UVCCameraGLSurfaceView.ErrorCallback errorCallback = error -> handleException();


    @Override
    protected void onResume() {
        super.onResume();
        usbMonitor.register();
        uvcCameraGLSurfaceView.onResume();
        if (btn_start.getText().toString().equals("结束直播")) {
            publisher.resumePublish();
        }
        if (btn_start_record_video.getText().toString().equals("结束录制")) {
            publisher.resumeRecord();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        usbMonitor.unregister();
        uvcCameraGLSurfaceView.onPause();
        if (btn_start_record_video.getText().toString().equals("结束录制")) {
            publisher.pauseRecord();
        }
        if (btn_start.getText().toString().equals("结束直播")) {
            publisher.pausePublish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        publisher.closeCamera();
        usbMonitor.destroy();
    }
}
