package net.ossrs.yasea;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Leo Ma on 4/1/2016.
 * jni指定了该类的类名以及包的路径不允许更改。
 */
public class SrsEncoder {
    private static final String TAG = "SrsEncoder";
    private static final boolean DEBUG = false;

    public static final String vCodec = SrsLiveConfig.VIDEO_CODEC;
    public static final String aCodec = SrsLiveConfig.AUDIO_CODEC;
    public static String x264Preset = SrsLiveConfig.XH264_VERY_FAST_PRESET;

    public static int vPortraitWidth = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
    public static int vPortraitHeight = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
    public static int vLandscapeWidth = SrsLiveConfig.HIGH_DEFINITION_WIDTH;
    public static int vLandscapeHeight = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;
    public static int vOutWidth = SrsLiveConfig.HIGH_DEFINITION_HEIGHT;// Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
    public static int vOutHeight = SrsLiveConfig.HIGH_DEFINITION_WIDTH;// Since Y component is quadruple size as U and V component, the stride must be set as 32x

    public static int vBitrate = SrsLiveConfig.HIGH_DEFINITION_BITRATE;//视频比特率
    public static int vFPS = SrsLiveConfig.NORMAL_FPS;//视频帧率
    public static final int vGOP = SrsLiveConfig.GOP;//I帧间隔周期

    public static int aBitrate = SrsLiveConfig.HIGH_QUALITY_BITRATE;//音频比特率
    public static final int aSampleRate = SrsLiveConfig.AUDIO_SAMPLE_RATE;//音频采样率
    public static int aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;//立体声通道

    private SrsEncodeHandler mHandler;

    private SrsFlvMuxer flvMuxer;
    private SrsMp4Muxer mp4Muxer;

    private MediaCodecInfo vMci;
    private MediaCodec vEncoder;
    private MediaCodec aEncoder;

    private boolean networkWeakTriggered = false;
    private boolean useSoftEncoder = false;
    private boolean canSoftEncode = false;

    private long mPresentTime;
    private long mPauseTime;

    private int mVideoColorFormat;

    private int videoFlvTrack;
    private int videoMp4Track;
    private int audioFlvTrack;
    private int audioMp4Track;

    static {
        System.loadLibrary("yuv");
        System.loadLibrary("enc");
    }

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv

    public SrsEncoder(SrsEncodeHandler handler) {
        mHandler = handler;
        vMci = chooseVideoEncoder();
    }

    public void setFlvMuxer(SrsFlvMuxer flvMuxer) {
        this.flvMuxer = flvMuxer;
    }

    public void setMp4Muxer(SrsMp4Muxer mp4Muxer) {
        this.mp4Muxer = mp4Muxer;
    }

    public boolean start() {
        if (flvMuxer == null || mp4Muxer == null) {
            return false;
        }

        // the referent PTS for video and audio encoder.
        mPresentTime = System.nanoTime() / 1000;

        // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
        // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        if (!useSoftEncoder && (vOutWidth % 32 != 0 || vOutHeight % 32 != 0)) {
            if (vMci != null && vMci.getName().contains("MTK")) {
                Log.e(TAG, "MTK encoding revolution stride must be 32x");
            }
        }

        setEncoderResolution(vOutWidth, vOutHeight);
        setEncoderFps(vFPS);
        setEncoderGop(vGOP);
        // Unfortunately for some android phone, the output fps is less than 10 limited by the
        // capacity of poor cheap chips even with x264. So for the sake of quick appearance of
        // the first picture on the player, a spare lower GOP value is suggested. But note that
        // lower GOP will produce more I frames and therefore more streaming data flow.
        // setEncoderGop(15);
        setEncoderBitrate(vBitrate);
        setEncoderPreset(x264Preset);

        if (useSoftEncoder) {
            canSoftEncode = openSoftEncoder();
            if (!canSoftEncode) {
                return false;
            }
        }

        // aEncoder pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aEncoder = MediaCodec.createEncoderByType(aCodec);
        } catch (Exception e) {
            Log.e(TAG, "create aEncoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the aEncoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        int ach = aChannelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(aCodec, aSampleRate, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, aBitrate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        aEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the audio tracker to muxer.
        audioFlvTrack = flvMuxer.addTrack(audioFormat);
        audioMp4Track = mp4Muxer.addTrack(audioFormat);

        // vEncoder yuv to 264 es stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            vEncoder = MediaCodec.createByCodecName(vMci.getName());
        } catch (Exception e) {
            Log.e(TAG, "create vEncoder failed.");
            e.printStackTrace();
            return false;
        }

        // setup the vEncoder.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration
        MediaFormat videoFormat = MediaFormat.createVideoFormat(vCodec, vOutWidth, vOutHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, vBitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, vFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, vGOP);
        vEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the video tracker to muxer.
        videoFlvTrack = flvMuxer.addTrack(videoFormat);
        videoMp4Track = mp4Muxer.addTrack(videoFormat);

        // start device and encoder.
        vEncoder.start();
        aEncoder.start();
        return true;
    }

    public void pause() {
        mPauseTime = System.nanoTime() / 1000;
    }

    public void resume() {
        long resumeTime = (System.nanoTime() / 1000) - mPauseTime;
        mPresentTime = mPresentTime + resumeTime;
        mPauseTime = 0;
    }

    public void stop() {
        if (useSoftEncoder) {
            closeSoftEncoder();
            canSoftEncode = false;
        }

        if (aEncoder != null) {
            if (DEBUG) Log.i(TAG, "stop aEncoder");
            try {
                aEncoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            aEncoder.release();
            aEncoder = null;
        }

        if (vEncoder != null) {
            if (DEBUG) Log.i(TAG, "stop vEncoder");
            try {
                vEncoder.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            vEncoder.release();
            vEncoder = null;
        }
    }

    public void switchToSoftEncoder() {
        useSoftEncoder = true;
    }

    public void switchToHardEncoder() {
        useSoftEncoder = false;
    }

    public boolean canHardEncode() {
        return vEncoder != null;
    }

    public boolean canSoftEncode() {
        return canSoftEncode;
    }

    public boolean isEnabled() {
        return canHardEncode() || canSoftEncode();
    }

    public void setPortraitResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vPortraitWidth = width;
        vPortraitHeight = height;
        vLandscapeWidth = height;
        vLandscapeHeight = width;
    }

    public void setLandscapeResolution(int width, int height) {
        vOutWidth = width;
        vOutHeight = height;
        vLandscapeWidth = width;
        vLandscapeHeight = height;
        vPortraitWidth = height;
        vPortraitHeight = width;
    }

    public void setVideoFullHDMode() {
        vBitrate = SrsLiveConfig.FULL_HIGH_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.APE_FLAC_BITRATE;
        vFPS = SrsLiveConfig.HIGH_FPS;
        x264Preset = SrsLiveConfig.XH264_ULTRA_FAST_PRESET;
    }

    public void setVideoHDMode() {
        vBitrate = SrsLiveConfig.HIGH_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.HIGH_QUALITY_BITRATE;
        vFPS = SrsLiveConfig.NORMAL_FPS;
        x264Preset = SrsLiveConfig.XH264_VERY_FAST_PRESET;
    }

    public void setVideoSmoothMode() {
        vBitrate = SrsLiveConfig.STANDARD_DEFINITION_BITRATE;
        aBitrate = SrsLiveConfig.NORMAL_QUALITY_BITRATE;
        vFPS = SrsLiveConfig.POOR_FPS;
        x264Preset = SrsLiveConfig.XH264_SUPER_FAST_PRESET;
    }

    public int getOutputWidth() {
        return vOutWidth;
    }

    public int getOutputHeight() {
        return vOutHeight;
    }

    public void setScreenOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            vOutWidth = vPortraitWidth;
            vOutHeight = vPortraitHeight;
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vOutWidth = vLandscapeWidth;
            vOutHeight = vLandscapeHeight;
        }

        // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
        // Since Y component is quadruple size as U and V component, the stride must be set as 32x
        if (!useSoftEncoder && (vOutWidth % 32 != 0 || vOutHeight % 32 != 0)) {
            if (vMci != null && vMci.getName().contains("MTK")) {
                Log.e(TAG, "MTK encoding revolution stride must be 32x");
            }
        }

        setEncoderResolution(vOutWidth, vOutHeight);
    }

    private void onProcessedYuvFrame(byte[] yuvFrame, long pts) {
        if (vEncoder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            ByteBuffer[] inBuffers = vEncoder.getInputBuffers();
            ByteBuffer[] outBuffers = vEncoder.getOutputBuffers();

            int inBufferIndex = vEncoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(yuvFrame, 0, yuvFrame.length);
                vEncoder.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
            }

            for (; ; ) {
                MediaCodec.BufferInfo vEbi = new MediaCodec.BufferInfo();
                int outBufferIndex = vEncoder.dequeueOutputBuffer(vEbi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = outBuffers[outBufferIndex];
                    onEncodedAnnexBFrame(bb, vEbi);
                    vEncoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        } else {
            int inBufferIndex = vEncoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = vEncoder.getInputBuffer(inBufferIndex);
                if (bb != null) {
                    bb.put(yuvFrame, 0, yuvFrame.length);
                }
                vEncoder.queueInputBuffer(inBufferIndex, 0, yuvFrame.length, pts, 0);
            }

            for (; ; ) {
                MediaCodec.BufferInfo vEbi = new MediaCodec.BufferInfo();
                int outBufferIndex = vEncoder.dequeueOutputBuffer(vEbi, 0);
                if (outBufferIndex >= 0) {
                    ByteBuffer bb = vEncoder.getOutputBuffer(outBufferIndex);
                    if (bb != null) {
                        onEncodedAnnexBFrame(bb, vEbi);
                    }
                    vEncoder.releaseOutputBuffer(outBufferIndex, false);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * libenc.cc里面的（libenc_RGBASoftEncode函数）会调用到此方法，用于软件编码
     */
    private void onSoftEncodedData(byte[] es, long pts, boolean isKeyFrame) {
        ByteBuffer bb = ByteBuffer.wrap(es);
        MediaCodec.BufferInfo vEbi = new MediaCodec.BufferInfo();
        vEbi.offset = 0;
        vEbi.size = es.length;
        vEbi.presentationTimeUs = pts;
        vEbi.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
        onEncodedAnnexBFrame(bb, vEbi);
    }

    /**
     * when got encoded h264 es stream.
     */
    private void onEncodedAnnexBFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        if (mp4Muxer == null || flvMuxer == null) {
            return;
        }
        mp4Muxer.writeSampleData(videoMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(videoFlvTrack, es, bi);
    }

    /**
     * Check audio frame cache number to judge the networking situation.
     * Just cache GOP / FPS seconds data according to latency.
     */
    public void onGetPcmFrame(byte[] data, int size) {
        if (flvMuxer == null || aEncoder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
            if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
                ByteBuffer[] inBuffers = aEncoder.getInputBuffers();
                ByteBuffer[] outBuffers = aEncoder.getOutputBuffers();

                int inBufferIndex = aEncoder.dequeueInputBuffer(-1);
                if (inBufferIndex >= 0) {
                    ByteBuffer bb = inBuffers[inBufferIndex];
                    bb.clear();
                    bb.put(data, 0, size);
                    long pts = System.nanoTime() / 1000 - mPresentTime;
                    aEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
                }

                for (; ; ) {
                    MediaCodec.BufferInfo aEbi = new MediaCodec.BufferInfo();
                    int outBufferIndex = aEncoder.dequeueOutputBuffer(aEbi, 0);
                    if (outBufferIndex >= 0) {
                        ByteBuffer bb = outBuffers[outBufferIndex];
                        onEncodedAacFrame(bb, aEbi);
                        aEncoder.releaseOutputBuffer(outBufferIndex, false);
                    } else {
                        break;
                    }
                }
            }
        } else {
            AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
            if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
                int inBufferIndex = aEncoder.dequeueInputBuffer(-1);
                if (inBufferIndex >= 0) {
                    ByteBuffer bb = aEncoder.getInputBuffer(inBufferIndex);
                    if (bb != null) {
                        bb.put(data, 0, size);
                    }
                    long pts = System.nanoTime() / 1000 - mPresentTime;
                    aEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
                }

                for (; ; ) {
                    MediaCodec.BufferInfo aEbi = new MediaCodec.BufferInfo();
                    int outBufferIndex = aEncoder.dequeueOutputBuffer(aEbi, 0);
                    if (outBufferIndex >= 0) {
                        ByteBuffer bb = aEncoder.getOutputBuffer(outBufferIndex);
                        if (bb != null) {
                            onEncodedAacFrame(bb, aEbi);
                        }
                        aEncoder.releaseOutputBuffer(outBufferIndex, false);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    /**
     * when got encoded aac raw stream.
     */
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        if (mp4Muxer == null || flvMuxer == null) {
            return;
        }
        mp4Muxer.writeSampleData(audioMp4Track, es.duplicate(), bi);
        flvMuxer.writeSampleData(audioFlvTrack, es, bi);
    }

    /**
     * Check video frame cache number to judge the networking situation.
     * Just cache GOP / FPS seconds data according to latency.
     */
    public void onGetRgbaFrame(byte[] data, int width, int height) {
        if (flvMuxer == null) {
            return;
        }
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTime;
            if (useSoftEncoder) {
                swRgbaFrame(data, width, height, pts);
            } else {
                byte[] processedData = hwRgbaFrame(data, width, height);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    /**
     * Check video frame cache number to judge the networking situation.
     * Just cache GOP / FPS seconds data according to latency.
     */
    public void onGetYuvNV21Frame(byte[] data, int width, int height, Rect boundingBox) {
        if (flvMuxer == null) {
            return;
        }
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTime;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
            } else {
                byte[] processedData = hwYUVNV21FrameScaled(data, width, height, boundingBox);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    /**
     * Check video frame cache number to judge the networking situation.
     * Just cache GOP / FPS seconds data according to latency.
     */
    public void onGetArgbFrame(int[] data, int width, int height, Rect boundingBox) {
        if (flvMuxer == null) {
            return;
        }
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTime;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
            } else {
                byte[] processedData = hwArgbFrameScaled(data, width, height, boundingBox);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    /**
     * Check video frame cache number to judge the networking situation.
     * Just cache GOP / FPS seconds data according to latency.
     */
    public void onGetArgbFrame(int[] data, int width, int height) {
        if (flvMuxer == null) {
            return;
        }
        AtomicInteger videoFrameCacheNumber = flvMuxer.getVideoFrameCacheNumber();
        if (videoFrameCacheNumber != null && videoFrameCacheNumber.get() < vGOP) {
            long pts = System.nanoTime() / 1000 - mPresentTime;
            if (useSoftEncoder) {
                throw new UnsupportedOperationException("Not implemented");
            } else {
                byte[] processedData = hwArgbFrame(data, width, height);
                if (processedData != null) {
                    onProcessedYuvFrame(processedData, pts);
                } else {
                    mHandler.notifyEncodeIllegalArgumentException(new IllegalArgumentException("libyuv failure"));
                }
            }

            if (networkWeakTriggered) {
                networkWeakTriggered = false;
                mHandler.notifyNetworkResume();
            }
        } else {
            mHandler.notifyNetworkWeak();
            networkWeakTriggered = true;
        }
    }

    private byte[] hwRgbaFrame(byte[] data, int width, int height) {
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            return RGBAToI420(data, width, height, true, 180);
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            return RGBAToNV12(data, width, height, true, 180);
        } else {
            throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwYUVNV21FrameScaled(byte[] data, int width, int height, Rect boundingBox) {
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            return NV21ToI420Scaled(data, width, height, true, 180, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            return NV21ToNV12Scaled(data, width, height, true, 180, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
        } else {
            throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwArgbFrameScaled(int[] data, int width, int height, Rect boundingBox) {
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            return ARGBToI420Scaled(data, width, height, false, 0, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            return ARGBToNV12Scaled(data, width, height, false, 0, boundingBox.left, boundingBox.top, boundingBox.width(), boundingBox.height());
        } else {
            throw new IllegalStateException("Unsupported color format!");
        }
    }

    private byte[] hwArgbFrame(int[] data, int inputWidth, int inputHeight) {
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            return ARGBToI420(data, inputWidth, inputHeight, false, 0);
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            return ARGBToNV12(data, inputWidth, inputHeight, false, 0);
        } else {
            throw new IllegalStateException("Unsupported color format!");
        }
    }

    private void swRgbaFrame(byte[] data, int width, int height, long pts) {
        RGBASoftEncode(data, width, height, true, 180, pts);
    }

    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
    };

    public AudioRecord chooseAudioRecord() {
        AudioRecord audioRecord = null;
        for (int src : AUDIO_SOURCES) {
            try {
                audioRecord = new AudioRecord(src, SrsEncoder.aSampleRate,
                        AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord = new AudioRecord(src, SrsEncoder.aSampleRate,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, getPcmBufferSize() * 4);
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        SrsEncoder.aChannelConfig = AudioFormat.CHANNEL_IN_MONO;
                    }
                } else {
                    SrsEncoder.aChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
                }
            } catch (Exception e) {
                audioRecord = null;
            }
            if (audioRecord != null) {
                break;
            }
        }
        return audioRecord;
    }

    private int getPcmBufferSize() {
        int pcmBufSize = AudioRecord.getMinBufferSize(aSampleRate, AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) + 8191;
        return pcmBufSize - (pcmBufSize % 8192);
    }

    /**
     * color formats that we can use in this class
     */
    private static int[] recognizedFormats = new int[]{
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
    };

    /**
     * choose the video encoder by name.
     */
    private MediaCodecInfo chooseVideoEncoder() {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                int nbCodecs = MediaCodecList.getCodecCount();
                for (int i = 0; i < nbCodecs; i++) {
                    MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
                    if (!mci.isEncoder()) {
                        continue;
                    }
                    String[] types = mci.getSupportedTypes();
                    for (String type : types) {
                        if (type.equalsIgnoreCase(vCodec)) {
                            if (DEBUG)
                                Log.i(TAG, String.format("vEncoder %s types: %s", mci.getName(), type));
                            mVideoColorFormat = chooseVideoColorFormat(mci);
                            return mci;
                        }
                    }
                }
            } else {
                MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                MediaCodecInfo[] mcis = mediaCodecList.getCodecInfos();
                for (MediaCodecInfo mci : mcis) {
                    if (!mci.isEncoder()) {
                        continue;
                    }
                    String[] types = mci.getSupportedTypes();
                    for (String type : types) {
                        if (type.equalsIgnoreCase(vCodec)) {
                            if (DEBUG)
                                Log.i(TAG, String.format("vEncoder %s types: %s", mci.getName(), type));
                            mVideoColorFormat = chooseVideoColorFormat(mci);
                            return mci;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "choose video encoder failed.");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * choose the right supported color format. @see below:
     * <p>
     * choose the encoder "video/avc":
     * 1. select default one when type matched.
     * 2. google avc is unusable.
     * 3. choose qcom avc.
     */
    private int chooseVideoColorFormat(MediaCodecInfo mci) {
        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc;
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            cc = mci.getCapabilitiesForType(vCodec);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
        for (int cf : cc.colorFormats) {
            if (DEBUG)
                Log.i(TAG, String.format("vEncoder %s supports color format 0x%x(%d)", mci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            for (int recognizedFormat : recognizedFormats) {
                if (cf == recognizedFormat) {
                    matchedColorFormat = cf;
                    break;
                }
            }
            if (matchedColorFormat > 0) {
                break;
            }
        }

        for (MediaCodecInfo.CodecProfileLevel pl : cc.profileLevels) {
            if (DEBUG)
                Log.i(TAG, String.format("vEncoder %s support profile %d, level %d", mci.getName(), pl.profile, pl.level));
        }
        if (matchedColorFormat == 0) {
            Log.e(TAG, "couldn't find a good color format for " + mci.getName() + " / " + vCodec);
        } else {
            if (DEBUG)
                Log.i(TAG, String.format("vEncoder %s choose color format 0x%x(%d)", mci.getName(), matchedColorFormat, matchedColorFormat));
        }
        return matchedColorFormat;
    }

    /**
     * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
     * We convert by putting the corresponding U and V bytes together (interleaved).
     * <p>
     * the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
     */
    public static byte[] YV12toYUV420PackedSemiPlanar(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }

        return output;
    }

    /**
     * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
     * So we just have to reverse U and V.
     */
    public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

    private native void setEncoderResolution(int outWidth, int outHeight);

    private native void setEncoderFps(int fps);

    private native void setEncoderGop(int gop);

    private native void setEncoderBitrate(int bitrate);

    private native void setEncoderPreset(String preset);

    private native byte[] RGBAToI420(byte[] rgbaFrame, int width, int height, boolean flip,
                                     int rotate);

    private native byte[] RGBAToNV12(byte[] rgbaFrame, int width, int height, boolean flip,
                                     int rotate);

    private native byte[] ARGBToI420Scaled(int[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] ARGBToNV12Scaled(int[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] ARGBToI420(int[] frame, int src_width, int src_height,
                                     boolean need_flip, int rotate_degree);

    private native byte[] ARGBToNV12(int[] frame, int src_width, int src_height,
                                     boolean need_flip, int rotate_degree);

    private native byte[] NV21ToNV12Scaled(byte[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native byte[] NV21ToI420Scaled(byte[] frame, int src_width, int src_height,
                                           boolean need_flip, int rotate_degree, int crop_x, int crop_y, int crop_width,
                                           int crop_height);

    private native int RGBASoftEncode(byte[] rgbaFrame, int width, int height, boolean flip,
                                      int rotate, long pts);

    private native boolean openSoftEncoder();

    private native void closeSoftEncoder();

}