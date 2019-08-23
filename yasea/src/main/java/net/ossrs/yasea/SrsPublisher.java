package net.ossrs.yasea;

import android.media.AudioRecord;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.util.Log;

import com.github.faucamp.simplertmp.RtmpHandler;
import com.seu.magicfilter.utils.MagicFilterType;

import java.io.File;

/**
 * Created by Leo Ma on 2016/7/25.
 */
public class SrsPublisher {
    private static final String TAG = "SrsPublisher";

    private static AudioRecord mic;
    private static AcousticEchoCanceler aec;
    private static AutomaticGainControl agc;
    private byte[] mPcmBuffer = new byte[4096];
    private Thread aWorker;
    private SrsCameraGLSurfaceView mCameraView;
    private boolean sendVideoOnly = false;
    private boolean sendAudioOnly = false;
    private int videoFrameCount;
    private long lastTimeMillis;
    private double mSamplingFps;

    private SrsFlvMuxer mFlvMuxer;
    private SrsMp4Muxer mMp4Muxer;
    private SrsEncoder mEncoder;

    public SrsPublisher(SrsCameraGLSurfaceView view) {
        mCameraView = view;
        mCameraView.setPreviewCallback(new SrsCameraGLSurfaceView.PreviewCallback() {
            @Override
            public void onGetRgbaFrame(byte[] data, int width, int height) {
                calcSamplingFps();
                if (!sendAudioOnly) {
                    mEncoder.onGetRgbaFrame(data, width, height);
                }
            }
        });
    }

    /**
     * Calculate sampling FPS
     */
    private void calcSamplingFps() {
        if (videoFrameCount == 0) {
            lastTimeMillis = System.nanoTime() / 1000000;
            videoFrameCount++;
        } else {
            if (++videoFrameCount >= SrsEncoder.vGOP) {
                long diffTimeMillis = System.nanoTime() / 1000000 - lastTimeMillis;
                mSamplingFps = (double) videoFrameCount * 1000 / diffTimeMillis;
                videoFrameCount = 0;
            }
        }
    }

    public void openCamera() {
        mCameraView.openCamera();
    }

    public void closeCamera() {
        mCameraView.closeCamera();
    }

    public void startPreview() {
        mCameraView.startPreview();
    }

    public void stopPreview() {
        mCameraView.stopPreview();
    }

    public void startTorch() {
        mCameraView.startTorch();
    }

    public void stopTorch() {
        mCameraView.stopTorch();
    }

    public void startAudio() {
        mic = mEncoder.chooseAudioRecord();
        if (mic == null) {
            return;
        }

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(mic.getAudioSessionId());
            if (aec != null) {
                try {
                    aec.setEnabled(true);
                } catch (Exception e) {
                    Log.e(TAG, "AcousticEchoCanceler can't setEnabled(true)");
                }
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(mic.getAudioSessionId());
            if (agc != null) {
                try {
                    agc.setEnabled(true);
                } catch (Exception e) {
                    Log.e(TAG, "AutomaticGainControl can't setEnabled(true)");
                }
            }
        }

        aWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                try {
                    mic.startRecording();
                } catch (Exception e) {
                    Log.e(TAG, "AudioRecord startRecording failed.");
                }
                while (!Thread.interrupted()) {
                    if (sendVideoOnly) {
                        mEncoder.onGetPcmFrame(mPcmBuffer, mPcmBuffer.length);
                        try {
                            // This is trivial...
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            break;
                        }
                    } else {
                        if (mic != null) {
                            int size = mic.read(mPcmBuffer, 0, mPcmBuffer.length);
                            if (size > 0) {
                                mEncoder.onGetPcmFrame(mPcmBuffer, size);
                            } else {
                                Log.e(TAG, "AudioRecord reading failed , code = " + size);
                            }
                        }
                    }
                }
            }
        });
        aWorker.start();
    }

    public void stopAudio() {
        if (aWorker != null) {
            aWorker.interrupt();
            try {
                aWorker.join();
            } catch (InterruptedException e) {
                aWorker.interrupt();
            }
            aWorker = null;
        }

        if (mic != null) {
            mic.setRecordPositionUpdateListener(null);
            try {
                mic.stop();
            } finally {
                mic.release();
                mic = null;
            }
        }

        if (aec != null) {
            try {
                aec.setEnabled(false);
            } finally {
                aec.release();
                aec = null;
            }
        }

        if (agc != null) {
            try {
                agc.setEnabled(false);
            } finally {
                agc.release();
                agc = null;
            }
        }
    }

    private void startEncode() {
        if (!mEncoder.start()) {
            return;
        }
        startPreview();
        startAudio();
        if (mEncoder != null && mEncoder.isEnabled()) {
            mCameraView.enableEncoding();
        }
    }

    private void resumeEncode() {
        startAudio();
        if (mEncoder != null && mEncoder.isEnabled()) {
            mCameraView.enableEncoding();
        }
    }

    private void pauseEncode() {
        stopAudio();
        mCameraView.disableEncoding();
        mCameraView.stopTorch();
    }

    private void stopEncode() {
        stopPreview();
        stopAudio();
        mEncoder.stop();
    }

    public void startPublish(String rtmpUrl) {
        if (mFlvMuxer != null) {
            mFlvMuxer.start(rtmpUrl);
            mFlvMuxer.setVideoResolution(mEncoder.getOutputWidth(), mEncoder.getOutputHeight());
            startEncode();
        }
    }

    public void resumePublish() {
        if (mFlvMuxer != null) {
            mEncoder.resume();
            resumeEncode();
        }
    }

    public void pausePublish() {
        if (mFlvMuxer != null) {
            mEncoder.pause();
            pauseEncode();
        }
    }

    public void stopPublish() {
        if (mFlvMuxer != null) {
            stopEncode();
            mFlvMuxer.stop();
        }
    }

    public boolean startRecord(String recPath) {
        return mMp4Muxer != null && mMp4Muxer.record(new File(recPath));
    }

    public void resumeRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.resume();
        }
    }

    public void pauseRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.pause();
        }
    }

    public void stopRecord() {
        if (mMp4Muxer != null) {
            mMp4Muxer.stop();
        }
    }

    public boolean isAllFramesUploaded() {
        return mFlvMuxer.getVideoFrameCacheNumber().get() == 0;
    }

    public int getVideoFrameCacheCount() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getVideoFrameCacheNumber().get();
        }
        return 0;
    }

    public void switchToSoftEncoder() {
        mEncoder.switchToSoftEncoder();
    }

    public void switchToHardEncoder() {
        mEncoder.switchToHardEncoder();
    }

    public double getSamplingFps() {
        return mSamplingFps;
    }

    public void setPreviewResolution(int width, int height) {
        mCameraView.setPreviewResolution(width, height);
    }

    public void setOutputResolution(int width, int height) {
        if (width <= height) {
            mEncoder.setPortraitResolution(width, height);
        } else {
            mEncoder.setLandscapeResolution(width, height);
        }
    }

    public void setScreenOrientation(int orientation) {
        mCameraView.setPreviewOrientation(orientation);
        mEncoder.setScreenOrientation(orientation);
    }

    public void setVideoFullHDMode() {
        mEncoder.setVideoFullHDMode();
    }

    public void setVideoHDMode() {
        mEncoder.setVideoHDMode();
    }

    public void setVideoSmoothMode() {
        mEncoder.setVideoSmoothMode();
    }

    public void setSendVideoOnly(boolean flag) {
        if (mic != null) {
            if (flag) {
                mic.stop();
                mPcmBuffer = new byte[4096];
            } else {
                mic.startRecording();
            }
        }
        sendVideoOnly = flag;
    }

    public void setSendAudioOnly(boolean flag) {
        sendAudioOnly = flag;
    }

    public void switchCameraFilter(MagicFilterType type) {
        mCameraView.setFilter(type);
    }

    public void switchCameraFace(int id) {
        mCameraView.stopPreview();
        mCameraView.closeCamera();
        mCameraView.setCameraId(id);
        mCameraView.openCamera();
        mCameraView.startPreview();
        if (mEncoder != null && mEncoder.isEnabled()) {
            mCameraView.enableEncoding();
        }
    }

    public void setRtmpHandler(RtmpHandler handler) {
        mFlvMuxer = new SrsFlvMuxer(handler);
        if (mEncoder != null) {
            mEncoder.setFlvMuxer(mFlvMuxer);
        }
    }

    public void setRecordHandler(SrsRecordHandler handler) {
        mMp4Muxer = new SrsMp4Muxer(handler);
        if (mEncoder != null) {
            mEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public void setEncodeHandler(SrsEncodeHandler handler) {
        mEncoder = new SrsEncoder(handler);
        if (mFlvMuxer != null) {
            mEncoder.setFlvMuxer(mFlvMuxer);
        }
        if (mMp4Muxer != null) {
            mEncoder.setMp4Muxer(mMp4Muxer);
        }
    }

    public void setErrorCallback(SrsCameraGLSurfaceView.ErrorCallback errorCallback) {
        mCameraView.setErrorCallback(errorCallback);
    }
}