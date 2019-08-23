package com.youngwu.live;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.seu.magicfilter.base.gpuimage.GPUImageFilter;
import com.seu.magicfilter.utils.MagicFilterFactory;
import com.seu.magicfilter.utils.MagicFilterType;
import com.seu.magicfilter.utils.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by YoungWu on 2019/7/29.
 */
public class UVCCameraGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public static final int ERROR_CODE_CAMERA_OPEN_FAILED = 1;
    public static final int ERROR_CODE_CAMERA_PREVIEW_FAILED = 3;

    private GPUImageFilter magicFilter;
    private SurfaceTexture surfaceTexture;
    private int mOESTextureId = OpenGLUtils.NO_TEXTURE;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private volatile boolean mIsEncoding;
    private float mInputAspectRatio;
    private float[] mProjectionMatrix = new float[16];
    private float[] mSurfaceMatrix = new float[16];
    private float[] mTransformMatrix = new float[16];

    private UVCCamera uvcCamera;
    private ByteBuffer mGLPreviewBuffer;

    private Thread worker;
    private final Object writeLock = new Object();
    private ConcurrentLinkedQueue<IntBuffer> mGLIntBufferCache = new ConcurrentLinkedQueue<>();
    private PreviewCallback mPrevCb;
    private ErrorCallback errorCallback;

    public UVCCameraGLSurfaceView(Context context) {
        this(context, null);
    }

    public UVCCameraGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);

        magicFilter = new GPUImageFilter(MagicFilterType.NONE);
        magicFilter.init(getContext().getApplicationContext());
        magicFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);

        mOESTextureId = OpenGLUtils.getExternalOESTextureID();
        surfaceTexture = new SurfaceTexture(mOESTextureId);
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                requestRender();
            }
        });

        // For camera preview on activity create
        if (uvcCamera != null) {
            try {
                uvcCamera.setPreviewTexture(surfaceTexture);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        if (magicFilter != null) {
            magicFilter.onDisplaySizeChanged(width, height);
            magicFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);
        }

        float mOutputAspectRatio = width > height ? (float) width / height : (float) height / width;
        float aspectRatio = mOutputAspectRatio / mInputAspectRatio;
        if (width > height) {
            Matrix.orthoM(mProjectionMatrix, 0, -1.0f, 1.0f, -aspectRatio, aspectRatio, -1.0f, 1.0f);
        } else {
            Matrix.orthoM(mProjectionMatrix, 0, -aspectRatio, aspectRatio, -1.0f, 1.0f, -1.0f, 1.0f);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(mSurfaceMatrix);

        Matrix.multiplyMM(mTransformMatrix, 0, mSurfaceMatrix, 0, mProjectionMatrix, 0);
        if (magicFilter != null) {
            magicFilter.setTextureTransformMatrix(mTransformMatrix);
            magicFilter.onDrawFrame(mOESTextureId);

            if (mIsEncoding) {
                mGLIntBufferCache.add(magicFilter.getGLFboBuffer());
                synchronized (writeLock) {
                    writeLock.notifyAll();
                }
            }
        }
    }

    public void setPreviewCallback(PreviewCallback cb) {
        mPrevCb = cb;
    }

    public void setErrorCallback(ErrorCallback callback) {
        errorCallback = callback;
    }

    public void setFrameCallback(IFrameCallback frameCallback, int pixelFormat) {
        if (uvcCamera != null) {
            try {
                uvcCamera.setFrameCallback(frameCallback, pixelFormat);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setPreviewResolution(int width, int height) {
        if (uvcCamera == null) {
            return;
        }
        try {
            mPreviewWidth = width;
            mPreviewHeight = height;

            List<Size> sizeList = uvcCamera.getSupportedSizeList();
            Size rs = adaptPreviewResolution(width, height, sizeList);
            if (rs != null) {
                mPreviewWidth = rs.width;
                mPreviewHeight = rs.height;
            }

            mGLPreviewBuffer = ByteBuffer.allocate(mPreviewWidth * mPreviewHeight * 4);
            mInputAspectRatio = mPreviewWidth > mPreviewHeight ?
                    (float) mPreviewWidth / mPreviewHeight : (float) mPreviewHeight / mPreviewWidth;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setFilter(final MagicFilterType type) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                if (magicFilter != null) {
                    magicFilter.destroy();
                }
                magicFilter = MagicFilterFactory.initFilters(type);
                if (magicFilter != null) {
                    magicFilter.init(getContext().getApplicationContext());
                    magicFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);
                    magicFilter.onDisplaySizeChanged(mSurfaceWidth, mSurfaceHeight);
                }
            }
        });
        requestRender();
    }

    public void enableEncoding() {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted()) {
                    while (!mGLIntBufferCache.isEmpty()) {
                        IntBuffer picture = mGLIntBufferCache.poll();
                        if (picture != null && mGLPreviewBuffer != null && mPrevCb != null) {
                            mGLPreviewBuffer.asIntBuffer().put(picture.array());
                            mPrevCb.onGetRgbaFrame(mGLPreviewBuffer.array(), mPreviewWidth, mPreviewHeight);
                        }
                    }
                    // Waiting for next frame
                    synchronized (writeLock) {
                        try {
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            writeLock.wait(500);
                        } catch (InterruptedException ie) {
                            worker.interrupt();
                        }
                    }
                }
            }
        });
        worker.start();
        mIsEncoding = true;
    }

    public void disableEncoding() {
        mIsEncoding = false;
        mGLIntBufferCache.clear();
        if (mGLPreviewBuffer != null) {
            mGLPreviewBuffer.clear();
        }

        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                worker.interrupt();
            }
            worker = null;
        }
    }

    public void startPreview() {
        if (uvcCamera == null) {
            return;
        }
        try {
            uvcCamera.setAutoFocus(false);
            uvcCamera.setAutoWhiteBlance(false);
//            uvcCamera.setBrightness(80);//亮度
//            uvcCamera.setBacklightComp(1);//逆光补偿
//            uvcCamera.setContrast(50);//对比度
//            uvcCamera.setExposure(156);//曝光
//            uvcCamera.setFocus(1);
//            uvcCamera.setGain(0);//增益
//            uvcCamera.setGamma(12);//gama
//            uvcCamera.setHue(50);//色调
//            uvcCamera.setPowerlineFrequency(50);//电力线频率
//            uvcCamera.setSaturation(56);//饱和度
//            uvcCamera.setSharpness(16);//清晰度
//            uvcCamera.setWhiteBlance(1);
//            uvcCamera.setZoom(1);

            getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
            try {
                uvcCamera.setPreviewSize(mPreviewWidth, mPreviewHeight, UVCCamera.FRAME_FORMAT_MJPEG);
            } catch (Exception e) {
                uvcCamera.setPreviewSize(mPreviewWidth, mPreviewHeight, UVCCamera.FRAME_FORMAT_YUYV);
            }

            uvcCamera.setPreviewTexture(surfaceTexture);
            uvcCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            stopPreview();
            closeCamera();
            callOnError(ERROR_CODE_CAMERA_PREVIEW_FAILED);
        }
    }

    public void stopPreview() {
        disableEncoding();
        if (uvcCamera != null) {
            try {
                uvcCamera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
                closeCamera();
            }
        }
    }

    public void openCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        if (uvcCamera != null) {
            return;
        }
        if (ctrlBlock == null) {
            return;
        }
        uvcCamera = new UVCCamera();
        try {
            uvcCamera.open(ctrlBlock);
        } catch (Exception e) {
            e.printStackTrace();
            stopPreview();
            closeCamera();
            callOnError(ERROR_CODE_CAMERA_OPEN_FAILED);
        }
    }

    public void closeCamera() {
        if (uvcCamera != null) {
            uvcCamera.close();
            uvcCamera = null;
        }
    }

    private Size adaptPreviewResolution(int width, int height, List<Size> sizeList) {
        float diff = 100f;
        float xdy = (float) width / (float) height;
        Size best = null;
        for (Size size : sizeList) {
            if (size.width == width && size.height == height) {
                return size;
            }
            float tmp = Math.abs(((float) size.width / (float) size.height) - xdy);
            if (tmp < diff) {
                diff = tmp;
                best = size;
            }
        }
        return best;
    }

    public interface PreviewCallback {

        void onGetRgbaFrame(byte[] data, int width, int height);
    }

    public interface ErrorCallback {
        void onError(int error);
    }

    private void callOnError(int error) {
        if (errorCallback != null) {
            errorCallback.onError(error);
        }
    }

}