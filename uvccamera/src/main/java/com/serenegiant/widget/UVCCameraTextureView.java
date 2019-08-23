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

package com.serenegiant.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import com.serenegiant.encoder.IVideoEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.glutils.EGLBase;
import com.serenegiant.glutils.GLDrawer2D;
import com.serenegiant.glutils.es1.GLHelper;
import com.serenegiant.utils.FpsCounter;

/**
 * change the view size with keeping the specified aspect ratio.
 * if you set this view with in a FrameLayout and set property "android:layout_gravity="center",
 * you can show this view in the center of screen and keep the aspect ratio of content
 * XXX it is better that can set the aspect ratio as xml property
 */
public class UVCCameraTextureView extends AspectRatioTextureView    // API >= 14
        implements TextureView.SurfaceTextureListener, CameraViewInterface {
    private static final boolean DEBUG = false;
    private static final String TAG = "UVCCameraTextureView";

    private boolean mHasSurface;
    private RenderHandler mRenderHandler;
    private final Object mCaptureSync = new Object();
    private Bitmap mTempBitmap;
    private boolean mRequestCaptureStillImage;
    private Callback mCallback;
    /**
     * for calculation of frame rate
     */
    private FpsCounter mFpsCounter = new FpsCounter();

    public UVCCameraTextureView(Context context) {
        this(context, null, 0);
    }

    public UVCCameraTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UVCCameraTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setSurfaceTextureListener(this);
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.v(TAG, "onResume:");
        if (mHasSurface) {
            mRenderHandler = RenderHandler.createHandler(mCallback, mFpsCounter, getRealSurfaceTexture(), getWidth(), getHeight());
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        if (mTempBitmap != null && !mTempBitmap.isRecycled()) {
            mTempBitmap.recycle();
            mTempBitmap = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (DEBUG) {
            Log.v(TAG, "onSurfaceTextureAvailable:" + surface + "," + width + ",height=" + height);
        }
        if (mRenderHandler == null) {
            mRenderHandler = RenderHandler.createHandler(mCallback, mFpsCounter, surface, width, height);
        } else {
            mRenderHandler.resize(width, height);
        }
        mHasSurface = true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (DEBUG) {
            Log.v(TAG, "onSurfaceTextureSizeChanged:" + surface + "," + width + ",height=" + height);
        }
        if (mRenderHandler != null) {
            mRenderHandler.resize(width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureDestroyed:" + surface);
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        mHasSurface = false;
        if (mPreviewSurface != null) {
            mPreviewSurface.release();
            mPreviewSurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        synchronized (mCaptureSync) {
            if (mRequestCaptureStillImage) {
                mRequestCaptureStillImage = false;
                if (mTempBitmap == null) {
                    mTempBitmap = getBitmap();
                } else {
                    getBitmap(mTempBitmap);
                }
                mCaptureSync.notifyAll();
            }
        }
    }

    /**
     * capture preview image as a bitmap
     * this method blocks current thread until bitmap is ready
     * if you call this method at almost same time from different thread,
     * the returned bitmap will be changed while you are processing the bitmap
     * (because we return same instance of bitmap on each call for memory saving)
     * if you need to call this method from multiple thread,
     * you should change this method(copy and return)
     */
    @Override
    public Bitmap captureStillImage() {
        synchronized (mCaptureSync) {
            mRequestCaptureStillImage = true;
            try {
                mCaptureSync.wait();
            } catch (InterruptedException ignored) {
            }
            return mTempBitmap;
        }
    }

    private SurfaceTexture mPreviewSurface;

    public SurfaceTexture getRealSurfaceTexture() {
        if (mPreviewSurface == null) {
            mPreviewSurface = mRenderHandler != null ? mRenderHandler.getPreviewTexture() : null;
        }
        return mPreviewSurface;
    }

    @Override
    public void setVideoEncoder(IVideoEncoder encoder) {
        if (mRenderHandler != null) {
            mRenderHandler.setVideoEncoder(encoder);
        }
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void resetFps() {
        mFpsCounter.reset();
    }

    /**
     * update frame rate of image processing
     */
    public void updateFps() {
        mFpsCounter.update();
    }

    /**
     * get current frame rate of image processing
     */
    public float getFps() {
        return mFpsCounter.getFps();
    }

    /**
     * get total frame rate from start
     */
    public float getTotalFps() {
        return mFpsCounter.getTotalFps();
    }

    /**
     * render camera frames on this view on a private thread
     *
     * @author saki
     */
    private static class RenderHandler extends Handler implements SurfaceTexture.OnFrameAvailableListener {
        private static final int MSG_REQUEST_RENDER = 1;
        private static final int MSG_SET_ENCODER = 2;
        private static final int MSG_CREATE_SURFACE = 3;
        private static final int MSG_RESIZE = 4;
        private static final int MSG_TERMINATE = 9;

        private RenderThread mThread;
        private boolean mIsActive = true;
        private FpsCounter mFpsCounter;

        public static RenderHandler createHandler(CameraViewInterface.Callback callback, FpsCounter counter, SurfaceTexture surface, int width, int height) {
            RenderThread thread = new RenderThread(callback, counter, surface, width, height);
            thread.start();
            return thread.getHandler();
        }

        private RenderHandler(FpsCounter counter, RenderThread thread) {
            mThread = thread;
            mFpsCounter = counter;
        }

        public void setVideoEncoder(IVideoEncoder encoder) {
            if (DEBUG) Log.v(TAG, "setVideoEncoder:");
            if (mIsActive)
                sendMessage(obtainMessage(MSG_SET_ENCODER, encoder));
        }

        public SurfaceTexture getPreviewTexture() {
            if (DEBUG) Log.v(TAG, "getPreviewTexture:");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendEmptyMessage(MSG_CREATE_SURFACE);
                    try {
                        mThread.mSync.wait();
                    } catch (InterruptedException ignored) {
                    }
                    return mThread.mPreviewSurface;
                }
            } else {
                return null;
            }
        }

        public void resize(int width, int height) {
            if (DEBUG) Log.v(TAG, "resize:");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendMessage(obtainMessage(MSG_RESIZE, width, height));
                    try {
                        mThread.mSync.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        public void release() {
            if (DEBUG) Log.v(TAG, "release:");
            if (mIsActive) {
                mIsActive = false;
                removeMessages(MSG_REQUEST_RENDER);
                removeMessages(MSG_SET_ENCODER);
                sendEmptyMessage(MSG_TERMINATE);
            }
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            if (mIsActive) {
                mFpsCounter.count();
                sendEmptyMessage(MSG_REQUEST_RENDER);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if (mThread == null) return;
            if (msg.what == MSG_REQUEST_RENDER) {
                mThread.onDrawFrame();
            } else if (msg.what == MSG_SET_ENCODER) {
                mThread.setEncoder((MediaEncoder) msg.obj);
            } else if (msg.what == MSG_CREATE_SURFACE) {
                mThread.updatePreviewSurface();
            } else if (msg.what == MSG_RESIZE) {
                mThread.resize(msg.arg1, msg.arg2);
            } else if (msg.what == MSG_TERMINATE) {
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
                mThread = null;
            }
        }

        private static class RenderThread extends Thread {
            private final Object mSync = new Object();
            private SurfaceTexture mSurface;
            private RenderHandler mHandler;
            private EGLBase mEgl;
            /**
             * IEglSurface instance related to this TextureView
             */
            private EGLBase.IEglSurface mEglSurface;
            private GLDrawer2D mDrawer;
            private int mTexId = -1;
            /**
             * SurfaceTexture instance to receive video images
             */
            private SurfaceTexture mPreviewSurface;
            private float[] mStMatrix = new float[16];
            private MediaEncoder mEncoder;
            private int mViewWidth, mViewHeight;
            private CameraViewInterface.Callback mCallback;
            private FpsCounter mFpsCounter;

            /**
             * constructor
             *
             * @param surface: drawing surface came from TextureView
             */
            public RenderThread(CameraViewInterface.Callback callback, FpsCounter fpsCounter, SurfaceTexture surface, int width, int height) {
                mCallback = callback;
                mFpsCounter = fpsCounter;
                mSurface = surface;
                mViewWidth = width;
                mViewHeight = height;
                setName("RenderThread");
            }

            public RenderHandler getHandler() {
                if (DEBUG) Log.v(TAG, "RenderThread#getHandler:");
                synchronized (mSync) {
                    // create rendering thread
                    if (mHandler == null)
                        try {
                            mSync.wait();
                        } catch (InterruptedException ignored) {
                        }
                }
                return mHandler;
            }

            public void resize(int width, int height) {
                if (((width > 0) && (width != mViewWidth)) || ((height > 0) && (height != mViewHeight))) {
                    mViewWidth = width;
                    mViewHeight = height;
                    updatePreviewSurface();
                } else {
                    synchronized (mSync) {
                        mSync.notifyAll();
                    }
                }
            }

            public void updatePreviewSurface() {
                if (DEBUG) Log.i(TAG, "RenderThread#updatePreviewSurface:");
                synchronized (mSync) {
                    if (mPreviewSurface != null) {
                        if (DEBUG) Log.d(TAG, "updatePreviewSurface:release mPreviewSurface");
                        mPreviewSurface.setOnFrameAvailableListener(null);
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    mEglSurface.makeCurrent();
                    if (mTexId >= 0) {
                        mDrawer.deleteTex(mTexId);
                    }
                    // create texture and SurfaceTexture for input from camera
                    mTexId = mDrawer.initTex();
                    if (DEBUG) Log.v(TAG, "updatePreviewSurface:tex_id=" + mTexId);
                    mPreviewSurface = new SurfaceTexture(mTexId);
                    mPreviewSurface.setDefaultBufferSize(mViewWidth, mViewHeight);
                    mPreviewSurface.setOnFrameAvailableListener(mHandler);
                    // notify to caller thread that previewSurface is ready
                    mSync.notifyAll();
                    if (mCallback != null) {
                        mCallback.onSurfaceChanged(mPreviewSurface, mViewWidth, mViewHeight);
                    }
                }
            }

            public void setEncoder(MediaEncoder encoder) {
                if (DEBUG) Log.v(TAG, "RenderThread#setEncoder:encoder=" + encoder);
                if ((encoder instanceof MediaVideoEncoder)) {
                    ((MediaVideoEncoder) encoder).setEglContext(mEglSurface.getContext(), mTexId);
                }
                mEncoder = encoder;
            }

            /**
             * draw a frame (and request to draw for video capturing if it is necessary)
             */
            public void onDrawFrame() {
                mEglSurface.makeCurrent();
                if (mPreviewSurface != null) {
                    // update texture(came from camera)
                    mPreviewSurface.updateTexImage();
                    // get texture matrix
                    mPreviewSurface.getTransformMatrix(mStMatrix);
                }
                // notify video encoder if it exist
                if (mEncoder != null) {
                    // notify to capturing thread that the camera frame is available.
                    if (mEncoder instanceof MediaVideoEncoder) {
                        ((MediaVideoEncoder) mEncoder).frameAvailableSoon(mStMatrix);
                    } else {
                        mEncoder.frameAvailableSoon();
                    }
                }
                // draw to preview screen
                mDrawer.draw(mTexId, mStMatrix, 0);
                mEglSurface.swap();
            }

            @Override
            public void run() {
                Log.d(TAG, getName() + " started");
                init();
                Looper.prepare();
                synchronized (mSync) {
                    mHandler = new RenderHandler(mFpsCounter, this);
                    mSync.notify();
                }

                Looper.loop();

                Log.d(TAG, getName() + " finishing");
                release();
                synchronized (mSync) {
                    mHandler = null;
                    mSync.notify();
                }
            }

            private void init() {
                if (DEBUG) Log.v(TAG, "RenderThread#init:");
                // create EGLContext for this thread
                mEgl = EGLBase.createFrom(null, false, false);
                mEglSurface = mEgl.createFromSurface(mSurface);
                mEglSurface.makeCurrent();
                // create drawing object
                mDrawer = new GLDrawer2D(true);
                if (mCallback != null) {
                    mCallback.onSurfaceCreated(mSurface, mViewWidth, mViewHeight);
                }
            }

            private void release() {
                if (DEBUG) Log.v(TAG, "RenderThread#release:");
                if (mDrawer != null) {
                    mDrawer.release();
                    mDrawer = null;
                }
                if (mCallback != null) {
                    mCallback.onSurfaceDestroy(mPreviewSurface);
                }
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                if (mTexId >= 0) {
                    GLHelper.deleteTex(mTexId);
                    mTexId = -1;
                }
                if (mEglSurface != null) {
                    mEglSurface.release();
                    mEglSurface = null;
                }
                if (mEgl != null) {
                    mEgl.release();
                    mEgl = null;
                }
            }
        }
    }
}
