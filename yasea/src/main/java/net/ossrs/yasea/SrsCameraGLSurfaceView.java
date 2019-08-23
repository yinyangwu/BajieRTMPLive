package net.ossrs.yasea;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.Surface;

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
 * Created by Leo Ma on 2016/2/25.
 */
public class SrsCameraGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public static final int ERROR_CODE_CAMERA_OPEN_FAILED = 1;
    public static final int ERROR_CODE_CAMERA_EVICTED = 2;
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

    private Camera mCamera;
    private ByteBuffer mGLPreviewBuffer;
    private int mCamId = -1;
    private int mPreviewRotation = 90;
    private int mPreviewOrientation = Configuration.ORIENTATION_PORTRAIT;

    private Thread worker;
    private final Object writeLock = new Object();
    private ConcurrentLinkedQueue<IntBuffer> mGLIntBufferCache = new ConcurrentLinkedQueue<>();
    private PreviewCallback mPrevCb;
    private ErrorCallback errorCallback;

    public SrsCameraGLSurfaceView(Context context) {
        this(context, null);
    }

    public SrsCameraGLSurfaceView(Context context, AttributeSet attrs) {
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
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
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

    public void setPreviewCallback(Camera.PreviewCallback previewCallback) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(previewCallback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setPreviewResolution(int width, int height) {
        if (mCamera == null) {
            return;
        }
        try {
            mPreviewWidth = width;
            mPreviewHeight = height;

            Camera.Parameters params = mCamera.getParameters();
            Camera.Size rs = adaptPreviewResolution(mCamera.new Size(width, height), params.getSupportedPreviewSizes());
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

    public void setCameraId(int id) {
        stopTorch();
        mCamId = id;
        setPreviewOrientation(mPreviewOrientation);
    }

    protected int getRotateDeg() {
        try {
            int rotate = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
            switch (rotate) {
                case Surface.ROTATION_0:
                    return 0;
                case Surface.ROTATION_90:
                    return 90;
                case Surface.ROTATION_180:
                    return 180;
                case Surface.ROTATION_270:
                    return 270;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void setPreviewOrientation(int orientation) {
        if (mCamera == null) {
            return;
        }
        try {
            mPreviewOrientation = orientation;
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(mCamId, info);

            int rotateDeg = getRotateDeg();

            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mPreviewRotation = info.orientation % 360;
                    mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
                } else {
                    mPreviewRotation = (info.orientation + 360) % 360;
                }
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mPreviewRotation = (info.orientation - 90) % 360;
                    mPreviewRotation = (360 - mPreviewRotation) % 360;  // compensate the mirror
                } else {
                    mPreviewRotation = (info.orientation + 90) % 360;
                }
            }

            if (rotateDeg > 0) {
                mPreviewRotation = mPreviewRotation % rotateDeg;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (mCamera == null) {
            return;
        }
        try {
            Camera.Parameters params = mCamera.getParameters();
            int[] range = adaptFpsRange(SrsEncoder.vFPS, params.getSupportedPreviewFpsRange());
            params.setPreviewFpsRange(range[0], range[1]);
            params.setPreviewFormat(ImageFormat.NV21);
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            params.setRecordingHint(true);

            List<String> supportedFocusModes = params.getSupportedFocusModes();
            if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) {
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    mCamera.autoFocus(null);
                } else {
                    params.setFocusMode(supportedFocusModes.get(0));
                }
            }

            getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
            params.setPreviewSize(mPreviewWidth, mPreviewHeight);

            mCamera.setParameters(params);

            mCamera.setDisplayOrientation(mPreviewRotation);

            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            stopPreview();
            closeCamera();
            callOnError(ERROR_CODE_CAMERA_PREVIEW_FAILED);
        }
    }

    public void stopPreview() {
        disableEncoding();
        stopTorch();
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
                closeCamera();
            }
        }
    }

    public void openCamera() {
        if (mCamera != null) {
            return;
        }
        if (mCamId < 0) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            int frontCamId = -1;
            int backCamId = -1;
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    backCamId = i;
                } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCamId = i;
                    break;
                }
            }
            if (frontCamId != -1) {
                mCamId = frontCamId;
            } else if (backCamId != -1) {
                mCamId = backCamId;
            } else {
                mCamId = 0;
            }
        }

        try {
            mCamera = Camera.open(mCamId);
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {
                    //may be Camera.CAMERA_ERROR_EVICTED - Camera was disconnected due to use by higher priority user
                    stopPreview();
                    closeCamera();
                    callOnError(ERROR_CODE_CAMERA_EVICTED);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            stopPreview();
            closeCamera();
            callOnError(ERROR_CODE_CAMERA_OPEN_FAILED);
        }
    }

    public void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera.Size adaptPreviewResolution(Camera.Size resolution, List<Camera.Size> sizeList) {
        float diff = 100f;
        float xdy = (float) resolution.width / (float) resolution.height;
        Camera.Size best = null;
        for (Camera.Size size : sizeList) {
            if (size.equals(resolution)) {
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

    private int[] adaptFpsRange(int fps, List<int[]> fpsRanges) {
        int expectedFps = fps * 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    public void startTorch() {
        if (mCamera != null) {
            try {
                Camera.Parameters params = mCamera.getParameters();
                List<String> supportedFlashModes = params.getSupportedFlashModes();
                if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) {
                    if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    } else {
                        params.setFlashMode(supportedFlashModes.get(0));
                    }
                    mCamera.setParameters(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopTorch() {
        if (mCamera != null) {
            try {
                Camera.Parameters params = mCamera.getParameters();
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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