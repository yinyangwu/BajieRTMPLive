/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameracommon;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaSurfaceEncoder;
import com.serenegiant.encoder.MediaVideoBufferEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.uvccamera.R;
import com.serenegiant.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

abstract class AbstractUVCCameraHandler extends Handler {
    private static final boolean DEBUG = false;
    private static final String TAG = "AbsUVCCameraHandler";

    public interface CameraCallback {
        void onOpen();

        void onClose();

        void onStartPreview();

        void onStopPreview();

        void onStartRecording();

        void onStopRecording();

        void onError(Exception e);
    }

    private static final int MSG_OPEN = 0;
    private static final int MSG_CLOSE = 1;
    private static final int MSG_PREVIEW_START = 2;
    private static final int MSG_PREVIEW_STOP = 3;
    private static final int MSG_CAPTURE_STILL = 4;
    private static final int MSG_CAPTURE_START = 5;
    private static final int MSG_CAPTURE_STOP = 6;
    private static final int MSG_MEDIA_UPDATE = 7;
    private static final int MSG_RELEASE = 9;

    private WeakReference<CameraThread> mWeakThread;
    private volatile boolean mReleased;

    protected AbstractUVCCameraHandler(CameraThread thread) {
        mWeakThread = new WeakReference<>(thread);
    }

    public int getWidth() {
        CameraThread thread = mWeakThread.get();
        return thread != null ? thread.getWidth() : 0;
    }

    public int getHeight() {
        CameraThread thread = mWeakThread.get();
        return thread != null ? thread.getHeight() : 0;
    }

    public boolean isOpened() {
        CameraThread thread = mWeakThread.get();
        return thread != null && thread.isCameraOpened();
    }

    public boolean isPreviewing() {
        CameraThread thread = mWeakThread.get();
        return thread != null && thread.isPreviewing();
    }

    public boolean isRecording() {
        CameraThread thread = mWeakThread.get();
        return thread != null && thread.isRecording();
    }

    public boolean isEqual(UsbDevice device) {
        CameraThread thread = mWeakThread.get();
        return (thread != null) && thread.isEqual(device);
    }

    public boolean isCameraThread() {
        CameraThread thread = mWeakThread.get();
        return thread != null && (thread.getId() == Thread.currentThread().getId());
    }

    public boolean isReleased() {
        CameraThread thread = mWeakThread.get();
        return mReleased || (thread == null);
    }

    public void checkReleased() {
        if (isReleased()) {
            throw new IllegalStateException("already released");
        }
    }

    public void open(USBMonitor.UsbControlBlock ctrlBlock) {
        checkReleased();
        sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
    }

    public void close() {
        if (DEBUG) Log.v(TAG, "close:");
        if (isOpened()) {
            stopPreview();
            sendEmptyMessage(MSG_CLOSE);
        }
        if (DEBUG) Log.v(TAG, "close:finished");
    }

    public void resize(int width, int height) {
        checkReleased();
        throw new UnsupportedOperationException("does not support now");
    }

    public void startPreview(Object surface) {
        checkReleased();
        if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
            throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture");
        }
        sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
    }

    public void stopPreview() {
        if (DEBUG) Log.v(TAG, "stopPreview:");
        removeMessages(MSG_PREVIEW_START);
        stopRecording();
        if (isPreviewing()) {
            CameraThread thread = mWeakThread.get();
            if (thread == null) return;
            synchronized (thread.mSync) {
                sendEmptyMessage(MSG_PREVIEW_STOP);
                if (!isCameraThread()) {
                    // wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
                    // while preview is still running.
                    // therefore this method will take a time to execute
                    try {
                        thread.mSync.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        if (DEBUG) Log.v(TAG, "stopPreview:finished");
    }

    public void captureStill() {
        checkReleased();
        sendEmptyMessage(MSG_CAPTURE_STILL);
    }

    public void captureStill(String path) {
        checkReleased();
        sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
    }

    public void startRecording() {
        checkReleased();
        sendEmptyMessage(MSG_CAPTURE_START);
    }

    public void stopRecording() {
        sendEmptyMessage(MSG_CAPTURE_STOP);
    }

    public void release() {
        mReleased = true;
        close();
        sendEmptyMessage(MSG_RELEASE);
    }

    public void addCallback(CameraCallback callback) {
        checkReleased();
        if (!mReleased && (callback != null)) {
            CameraThread thread = mWeakThread.get();
            if (thread != null) {
                thread.mCallbacks.add(callback);
            }
        }
    }

    public void removeCallback(CameraCallback callback) {
        if (callback != null) {
            CameraThread thread = mWeakThread.get();
            if (thread != null) {
                thread.mCallbacks.remove(callback);
            }
        }
    }

    public void updateMedia(String path) {
        sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
    }

    public boolean checkSupportFlag(long flag) {
        checkReleased();
        CameraThread thread = mWeakThread.get();
        return thread != null && thread.mUVCCamera != null && thread.mUVCCamera.checkSupportFlag(flag);
    }

    public int getValue(int flag) {
        checkReleased();
        CameraThread thread = mWeakThread.get();
        UVCCamera camera = thread != null ? thread.mUVCCamera : null;
        if (camera != null) {
            if (flag == UVCCamera.PU_BRIGHTNESS) {
                return camera.getBrightness();
            } else if (flag == UVCCamera.PU_CONTRAST) {
                return camera.getContrast();
            }
        }
        throw new IllegalStateException();
    }

    public int setValue(int flag, int value) {
        checkReleased();
        CameraThread thread = mWeakThread.get();
        UVCCamera camera = thread != null ? thread.mUVCCamera : null;
        if (camera != null) {
            if (flag == UVCCamera.PU_BRIGHTNESS) {
                camera.setBrightness(value);
                return camera.getBrightness();
            } else if (flag == UVCCamera.PU_CONTRAST) {
                camera.setContrast(value);
                return camera.getContrast();
            }
        }
        throw new IllegalStateException();
    }

    public int resetValue(int flag) {
        checkReleased();
        CameraThread thread = mWeakThread.get();
        UVCCamera camera = thread != null ? thread.mUVCCamera : null;
        if (camera != null) {
            if (flag == UVCCamera.PU_BRIGHTNESS) {
                camera.resetBrightness();
                return camera.getBrightness();
            } else if (flag == UVCCamera.PU_CONTRAST) {
                camera.resetContrast();
                return camera.getContrast();
            }
        }
        throw new IllegalStateException();
    }

    @Override
    public void handleMessage(Message msg) {
        CameraThread thread = mWeakThread.get();
        if (thread == null) return;
        switch (msg.what) {
            case MSG_OPEN:
                thread.handleOpen((USBMonitor.UsbControlBlock) msg.obj);
                break;
            case MSG_CLOSE:
                thread.handleClose();
                break;
            case MSG_PREVIEW_START:
                thread.handleStartPreview(msg.obj);
                break;
            case MSG_PREVIEW_STOP:
                thread.handleStopPreview();
                break;
            case MSG_CAPTURE_STILL:
                thread.handleCaptureStill((String) msg.obj);
                break;
            case MSG_CAPTURE_START:
                thread.handleStartRecording();
                break;
            case MSG_CAPTURE_STOP:
                thread.handleStopRecording();
                break;
            case MSG_MEDIA_UPDATE:
                thread.handleUpdateMedia((String) msg.obj);
                break;
            case MSG_RELEASE:
                thread.handleRelease();
                break;
            default:
                throw new RuntimeException("unsupported message:what=" + msg.what);
        }
    }

    public static class CameraThread extends Thread {
        private static final String TAG_THREAD = "CameraThread";
        private final Object mSync = new Object();
        private Class<? extends AbstractUVCCameraHandler> mHandlerClass;
        private WeakReference<Activity> mWeakParent;
        private WeakReference<CameraViewInterface> mWeakCameraView;
        private int mEncoderType;
        private Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<>();
        private int mWidth, mHeight, mPreviewMode;
        private float mBandwidthFactor;
        private boolean mIsPreviewing;
        private boolean mIsRecording;
        /**
         * shutter sound
         */
        private SoundPool mSoundPool;
        private int mSoundId;
        private AbstractUVCCameraHandler mHandler;
        /**
         * for accessing UVC camera
         */
        private UVCCamera mUVCCamera;
        /**
         * muxer for audio/video recording
         */
        private MediaMuxerWrapper mMuxer;
        private MediaVideoBufferEncoder mVideoEncoder;

        /**
         * @param clazz           Class extends AbstractUVCCameraHandler
         * @param parent          parent Activity
         * @param cameraView      for still capturing
         * @param encoderType     0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
         * @param width           preview width
         * @param height          preview height
         * @param format          either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
         * @param bandwidthFactor 带宽比例
         */
        CameraThread(Class<? extends AbstractUVCCameraHandler> clazz,
                     Activity parent, CameraViewInterface cameraView,
                     int encoderType, int width, int height, int format,
                     float bandwidthFactor) {
            super("CameraThread");
            mHandlerClass = clazz;
            mEncoderType = encoderType;
            mWidth = width;
            mHeight = height;
            mPreviewMode = format;
            mBandwidthFactor = bandwidthFactor;
            mWeakParent = new WeakReference<>(parent);
            mWeakCameraView = new WeakReference<>(cameraView);
            loadShutterSound(parent);
        }

        @Override
        protected void finalize() throws Throwable {
            Log.i(TAG_THREAD, "CameraThread#finalize");
            super.finalize();
        }

        public AbstractUVCCameraHandler getHandler() {
            if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
            synchronized (mSync) {
                if (mHandler == null)
                    try {
                        mSync.wait();
                    } catch (InterruptedException ignored) {
                    }
            }
            return mHandler;
        }

        public int getWidth() {
            synchronized (mSync) {
                return mWidth;
            }
        }

        public int getHeight() {
            synchronized (mSync) {
                return mHeight;
            }
        }

        public boolean isCameraOpened() {
            synchronized (mSync) {
                return mUVCCamera != null;
            }
        }

        public boolean isPreviewing() {
            synchronized (mSync) {
                return mUVCCamera != null && mIsPreviewing;
            }
        }

        public boolean isRecording() {
            synchronized (mSync) {
                return (mUVCCamera != null) && (mMuxer != null);
            }
        }

        public boolean isEqual(UsbDevice device) {
            return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
        }

        public void handleOpen(USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
            handleClose();
            try {
                UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                camera.setStatusCallback(new IStatusCallback() {
                    @Override
                    public void onStatus(int statusClass, int event, int selector, int statusAttribute, ByteBuffer data) {
                        if (DEBUG)
                            Log.i(TAG_THREAD, "statusClass=" + statusClass + ",event=" + event + ",selector=" + selector + ",statusAttribute=" + statusAttribute + ",size=" + data.slice());
                    }
                });
                synchronized (mSync) {
                    mUVCCamera = camera;
                }
                callOnOpen();
            } catch (Exception e) {
                callOnError(e);
            }
            if (DEBUG)
                Log.i(TAG_THREAD, "supportedSize:" + (mUVCCamera != null ? mUVCCamera.getSupportedSize() : null));
            if (DEBUG) Log.v(TAG_THREAD, "handleOpen:finished");
        }

        public void handleClose() {
            if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
            handleStopRecording();
            UVCCamera camera;
            synchronized (mSync) {
                camera = mUVCCamera;
                mUVCCamera = null;
            }
            if (camera != null) {
                camera.stopPreview();
                camera.destroy();
                callOnClose();
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleClose:finished");
        }

        public void handleStartPreview(Object surface) {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:surface=" + surface);
            if ((mUVCCamera == null) || mIsPreviewing) {
                return;
            }
            try {
                mUVCCamera.setPreviewSize(mWidth, mHeight, UVCCamera.DEFAULT_PREVIEW_MIN_FPS,
                        UVCCamera.DEFAULT_PREVIEW_MAX_FPS, mPreviewMode, mBandwidthFactor);
            } catch (IllegalArgumentException e) {
                try {
                    // fallback to YUV mode
                    mUVCCamera.setPreviewSize(mWidth, mHeight, UVCCamera.DEFAULT_PREVIEW_MIN_FPS,
                            UVCCamera.DEFAULT_PREVIEW_MAX_FPS, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
                } catch (IllegalArgumentException e1) {
                    callOnError(e1);
                    return;
                }
            }
            if (surface instanceof SurfaceHolder) {
                mUVCCamera.setPreviewDisplay((SurfaceHolder) surface);
            } else if (surface instanceof Surface) {
                mUVCCamera.setPreviewDisplay((Surface) surface);
            } else {
                mUVCCamera.setPreviewTexture((SurfaceTexture) surface);
            }
            mUVCCamera.startPreview();
            mUVCCamera.updateCameraParams();
            synchronized (mSync) {
                mIsPreviewing = true;
            }
            callOnStartPreview();
            if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:finished");
        }

        public void handleStopPreview() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
            if (mIsPreviewing) {
                if (mUVCCamera != null) {
                    mUVCCamera.stopPreview();
                }
                synchronized (mSync) {
                    mIsPreviewing = false;
                    mSync.notifyAll();
                }
                callOnStopPreview();
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
        }

        public void handleCaptureStill(String path) {
            if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:path=" + path);
            Activity parent = mWeakParent.get();
            if (parent == null) return;
            mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);    // play shutter sound
            try {
                CameraViewInterface cameraViewInterface = mWeakCameraView.get();
                if (cameraViewInterface == null) {
                    return;
                }
                Bitmap bitmap = cameraViewInterface.captureStillImage();
                if (bitmap == null) {
                    return;
                }
                // get buffered output stream for saving a captured still image as a file on external storage.
                // the file name is came from current time.
                // You should use extension name as same as CompressFormat when calling Bitmap#compress.
                File outputFile = TextUtils.isEmpty(path)
                        ? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
                        : new File(path);
                if (outputFile != null) {
                    try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.flush();
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                callOnError(e);
                Log.e(TAG_THREAD, "handleStartRecording:", e);
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:finished");
        }

        public void handleStartRecording() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:mEncoderType=" + mEncoderType);
            try {
                if ((mUVCCamera == null) || (mMuxer != null)) {
                    return;
                }
                MediaMuxerWrapper muxer = new MediaMuxerWrapper(".mp4");    // if you record audio only, ".m4a" is also OK.
                MediaVideoBufferEncoder videoEncoder = null;
                switch (mEncoderType) {
                    case 1:    // for video capturing using MediaVideoEncoder
                        new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
                        break;
                    case 2:    // for video capturing using MediaVideoBufferEncoder
                        videoEncoder = new MediaVideoBufferEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
                        break;
                    // case 0:	// for video capturing using MediaSurfaceEncoder
                    default:
                        new MediaSurfaceEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
                        break;
                }
                // for audio capturing
                new MediaAudioEncoder(muxer, mMediaEncoderListener);
                muxer.prepare();
                muxer.startRecording();
                if (videoEncoder != null) {
                    mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
                }
                synchronized (mSync) {
                    mMuxer = muxer;
                    mVideoEncoder = videoEncoder;
                }
                callOnStartRecording();
            } catch (IOException e) {
                callOnError(e);
                Log.e(TAG_THREAD, "handleStartRecording:", e);
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:finished");
        }

        public void handleStopRecording() {
            if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
            MediaMuxerWrapper muxer;
            synchronized (mSync) {
                muxer = mMuxer;
                mMuxer = null;
                mVideoEncoder = null;
                if (mUVCCamera != null) {
                    mUVCCamera.stopCapture();
                }
            }
            CameraViewInterface cameraViewInterface = mWeakCameraView.get();
            if (cameraViewInterface != null) {
                cameraViewInterface.setVideoEncoder(null);
            }
            if (muxer != null) {
                muxer.stopRecording();
                if (mUVCCamera != null) {
                    mUVCCamera.setFrameCallback(null, 0);
                }
                // you should not wait here
                callOnStopRecording();
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:finished");
        }

        private IFrameCallback mIFrameCallback = new IFrameCallback() {
            @Override
            public void onFrame(ByteBuffer frame) {
                MediaVideoBufferEncoder videoEncoder;
                synchronized (mSync) {
                    videoEncoder = mVideoEncoder;
                }
                if (videoEncoder != null) {
                    videoEncoder.frameAvailableSoon();
                    videoEncoder.encode(frame);
                }
            }
        };

        public void handleUpdateMedia(String path) {
            if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
            Activity parent = mWeakParent.get();
            boolean released = (mHandler == null) || mHandler.mReleased;
            if (parent != null && parent.getApplicationContext() != null) {
                try {
                    if (DEBUG) Log.i(TAG_THREAD, "MediaScannerConnection#scanFile");
                    MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{path}, null, null);
                } catch (Exception e) {
                    Log.e(TAG_THREAD, "handleUpdateMedia:", e);
                }
                if (released || parent.isDestroyed())
                    handleRelease();
            } else {
                Log.w(TAG_THREAD, "MainActivity already destroyed");
                // give up to add this movie to MediaStore now.
                // Seeing this movie on Gallery app etc. will take a lot of time.
                handleRelease();
            }
        }

        public void handleRelease() {
            if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
            handleClose();
            mCallbacks.clear();
            if (!mIsRecording) {
                mHandler.mReleased = true;
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            }
            if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
        }

        private MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
            @Override
            public void onPrepared(MediaEncoder encoder) {
                if (DEBUG) Log.v(TAG_THREAD, "onPrepared:encoder=" + encoder);
                mIsRecording = true;
                if (encoder instanceof MediaVideoEncoder) {
                    CameraViewInterface cameraViewInterface = mWeakCameraView.get();
                    if (cameraViewInterface != null) {
                        cameraViewInterface.setVideoEncoder((MediaVideoEncoder) encoder);
                    }
                } else if (encoder instanceof MediaSurfaceEncoder) {
                    CameraViewInterface cameraViewInterface = mWeakCameraView.get();
                    if (cameraViewInterface != null) {
                        cameraViewInterface.setVideoEncoder((MediaSurfaceEncoder) encoder);
                    }
                    try {
                        if (mUVCCamera != null) {
                            mUVCCamera.startCapture(((MediaSurfaceEncoder) encoder).getInputSurface());
                        }
                    } catch (Exception e) {
                        Log.e(TAG_THREAD, "onPrepared:", e);
                    }
                }
            }

            @Override
            public void onStopped(MediaEncoder encoder) {
                if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
                if ((encoder instanceof MediaVideoEncoder)
                        || (encoder instanceof MediaSurfaceEncoder)) {
                    try {
                        mIsRecording = false;
                        Activity parent = mWeakParent.get();
                        CameraViewInterface cameraViewInterface = mWeakCameraView.get();
                        if (cameraViewInterface != null) {
                            cameraViewInterface.setVideoEncoder(null);
                        }
                        synchronized (mSync) {
                            if (mUVCCamera != null) {
                                mUVCCamera.stopCapture();
                            }
                        }
                        String path = encoder.getOutputPath();
                        if (!TextUtils.isEmpty(path)) {
                            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
                        } else {
                            boolean released = (mHandler == null) || mHandler.mReleased;
                            if (released || parent == null || parent.isDestroyed()) {
                                handleRelease();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG_THREAD, "onStopped:", e);
                    }
                }
            }
        };

        /**
         * prepare and load shutter sound for still image capturing
         */
        private void loadShutterSound(Context context) {
            // get system stream type using reflection
            int streamType;
            try {
                Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
                Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
                streamType = sseField.getInt(null);
            } catch (Exception e) {
                streamType = AudioManager.STREAM_SYSTEM;    // set appropriate according to your app policy
            }
            if (mSoundPool != null) {
                try {
                    mSoundPool.release();
                } catch (Exception ignored) {
                }
                mSoundPool = null;
            }
            // load shutter sound from resource
            mSoundPool = new SoundPool(2, streamType, 0);
            mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
        }

        @Override
        public void run() {
            Looper.prepare();
            AbstractUVCCameraHandler handler = null;
            try {
                Constructor<? extends AbstractUVCCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
                handler = constructor.newInstance(this);
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                Log.w(TAG_THREAD, e);
            }
            if (handler != null) {
                synchronized (mSync) {
                    mHandler = handler;
                    mSync.notifyAll();
                }
                Looper.loop();
                if (mSoundPool != null) {
                    mSoundPool.release();
                    mSoundPool = null;
                }
                if (mHandler != null) {
                    mHandler.mReleased = true;
                }
            }
            mCallbacks.clear();
            synchronized (mSync) {
                mHandler = null;
                mSync.notifyAll();
            }
        }

        private void callOnOpen() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onOpen();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnClose() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onClose();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnStartPreview() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onStartPreview();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnStopPreview() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onStopPreview();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnStartRecording() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onStartRecording();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnStopRecording() {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onStopRecording();
                } catch (Exception e) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }

        private void callOnError(Exception e) {
            for (CameraCallback callback : mCallbacks) {
                try {
                    callback.onError(e);
                } catch (Exception e1) {
                    mCallbacks.remove(callback);
                    Log.w(TAG_THREAD, e);
                }
            }
        }
    }
}
