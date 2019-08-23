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

package com.serenegiant.encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaAudioEncoder extends MediaEncoder {
    private static final boolean DEBUG = false;
    private static final String TAG = "MediaAudioEncoder";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;// 44.1[KHz] is only setting guaranteed to be available on all devices.
    private static final int BIT_RATE = 64000;
    private static final int SAMPLES_PER_FRAME = 1024;// AAC, bytes/frame/channel
    private static final int FRAMES_PER_BUFFER = 25;// AAC, frame/buffer/sec

    private AudioThread mAudioThread = null;

    public MediaAudioEncoder(MediaMuxerWrapper muxer, MediaEncoderListener listener) {
        super(muxer, listener);
    }

    @Override
    protected void prepare() throws IOException {
        if (DEBUG) Log.v(TAG, "prepare:");
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;
        // prepare MediaCodec for AAC encoding of audio data from inernal mic.
        MediaCodecInfo audioCodecInfo = selectAudioCodec();
        if (audioCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        if (DEBUG) Log.i(TAG, "selected codec: " + audioCodecInfo.getName());

        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        if (DEBUG) Log.i(TAG, "format: " + audioFormat);
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        if (DEBUG) Log.i(TAG, "prepare finishing");
        if (mListener != null) {
            mListener.onPrepared(this);
        }
    }

    @Override
    protected void startRecording() {
        super.startRecording();
        // create and execute audio capturing thread using internal mic
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    @Override
    protected void release() {
        mAudioThread = null;
        super.release();
    }

    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.CAMCORDER,
    };

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     */
    private class AudioThread extends Thread {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO); // THREAD_PRIORITY_URGENT_AUDIO
            int cnt = 0;
            int min_buffer_size = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
            if (buffer_size < min_buffer_size)
                buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
            ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME).order(ByteOrder.nativeOrder());
            AudioRecord audioRecord = null;
            for (int src : AUDIO_SOURCES) {
                try {
                    audioRecord = new AudioRecord(src,
                            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        audioRecord.release();
                        audioRecord = null;
                    }
                } catch (Exception e) {
                    audioRecord = null;
                }
                if (audioRecord != null) {
                    break;
                }
            }
            if (audioRecord != null) {
                try {
                    if (mIsCapturing) {
                        if (DEBUG) Log.v(TAG, "AudioThread:start audio recording");
                        int readBytes;
                        audioRecord.startRecording();
                        try {
                            for (; mIsCapturing && !mRequestStop && !mIsEOS; ) {
                                // read audio data from internal mic
                                buf.clear();
                                try {
                                    readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                                } catch (Exception e) {
                                    break;
                                }
                                if (readBytes > 0) {
                                    // set audio data to encoder
                                    buf.position(readBytes);
                                    buf.flip();
                                    encode(buf, readBytes, getPTSUs());
                                    frameAvailableSoon();
                                    cnt++;
                                }
                            }
                            if (cnt > 0) {
                                frameAvailableSoon();
                            }
                        } finally {
                            audioRecord.stop();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "AudioThread#run", e);
                } finally {
                    audioRecord.release();
                }
            }
            if (cnt == 0) {
                for (int i = 0; mIsCapturing && (i < 5); i++) {
                    buf.position(SAMPLES_PER_FRAME);
                    buf.flip();
                    try {
                        encode(buf, SAMPLES_PER_FRAME, getPTSUs());
                        frameAvailableSoon();
                    } catch (Exception e) {
                        break;
                    }
                    synchronized (this) {
                        try {
                            wait(50);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
            if (DEBUG) Log.v(TAG, "AudioThread:finished");
        }
    }

    /**
     * select the first codec that match a specific MIME type
     */
    private static MediaCodecInfo selectAudioCodec() {
        if (DEBUG) Log.v(TAG, "selectAudioCodec:");
        MediaCodecInfo result = null;
        // get the list of available codecs
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                //skipp decoder
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (DEBUG) Log.i(TAG, "supportedType:" + codecInfo.getName() + ",MIME=" + type);
                if (type.equalsIgnoreCase(MediaAudioEncoder.MIME_TYPE)) {
                    result = codecInfo;
                    break;
                }
            }
        }
        return result;
    }

}
