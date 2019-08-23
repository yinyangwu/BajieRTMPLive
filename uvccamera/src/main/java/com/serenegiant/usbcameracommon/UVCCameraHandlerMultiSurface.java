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
import android.view.Surface;

import com.serenegiant.glutils.RendererHolder;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.CameraViewInterface;

public class UVCCameraHandlerMultiSurface extends AbstractUVCCameraHandler {

    /**
     * create UVCCameraHandlerMultiSurface, use MediaVideoEncoder, try MJPEG, default bandwidth
     *
     * @param parent
     * @param cameraView
     * @param width
     * @param height
     * @return
     */
    public static UVCCameraHandlerMultiSurface createHandler(Activity parent, CameraViewInterface cameraView, int width, int height) {
        return createHandler(parent, cameraView, 1, width, height, UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.DEFAULT_BANDWIDTH);
    }

    /**
     * create UVCCameraHandlerMultiSurface, use MediaVideoEncoder, try MJPEG
     *
     * @param parent
     * @param cameraView
     * @param width
     * @param height
     * @param bandwidthFactor
     * @return
     */
    public static UVCCameraHandlerMultiSurface createHandler(Activity parent, CameraViewInterface cameraView,
                                                             int width, int height, float bandwidthFactor) {
        return createHandler(parent, cameraView, 1, width, height, UVCCamera.FRAME_FORMAT_MJPEG, bandwidthFactor);
    }

    /**
     * create UVCCameraHandlerMultiSurface, try MJPEG, default bandwidth
     *
     * @param parent
     * @param cameraView
     * @param encoderType
     * @param width
     * @param height
     * @return
     */
    public static UVCCameraHandlerMultiSurface createHandler(Activity parent, CameraViewInterface cameraView,
                                                             int encoderType, int width, int height) {
        return createHandler(parent, cameraView, encoderType, width, height, UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.DEFAULT_BANDWIDTH);
    }

    /**
     * create UVCCameraHandlerMultiSurface, default bandwidth
     *
     * @param parent
     * @param cameraView
     * @param encoderType
     * @param width
     * @param height
     * @param format
     * @return
     */
    public static UVCCameraHandlerMultiSurface createHandler(Activity parent, CameraViewInterface cameraView,
                                                             int encoderType, int width, int height, int format) {
        return createHandler(parent, cameraView, encoderType, width, height, format, UVCCamera.DEFAULT_BANDWIDTH);
    }

    /**
     * create UVCCameraHandlerMultiSurface
     *
     * @param parent
     * @param cameraView
     * @param encoderType     0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
     * @param width
     * @param height
     * @param format          either UVCCamera.FRAME_FORMAT_YUYV(0) or UVCCamera.FRAME_FORMAT_MJPEG(1)
     * @param bandwidthFactor
     * @return
     */
    public static UVCCameraHandlerMultiSurface createHandler(Activity parent, CameraViewInterface cameraView,
                                                             int encoderType, int width, int height, int format, float bandwidthFactor) {
        CameraThread thread = new CameraThread(UVCCameraHandlerMultiSurface.class, parent, cameraView, encoderType, width, height, format, bandwidthFactor);
        thread.start();
        return (UVCCameraHandlerMultiSurface) thread.getHandler();
    }

    private RendererHolder mRendererHolder;

    protected UVCCameraHandlerMultiSurface(CameraThread thread) {
        super(thread);
        mRendererHolder = new RendererHolder(thread.getWidth(), thread.getHeight(), null);
    }

    @Override
    public void release() {
        super.release();
        if (mRendererHolder != null) {
            mRendererHolder.release();
            mRendererHolder = null;
        }
    }

    @Override
    public void resize(int width, int height) {
        if (mRendererHolder != null) {
            mRendererHolder.resize(width, height);
        }
    }

    @Override
    public void startPreview(Object surface) {
        if (mRendererHolder != null) {
            super.startPreview(mRendererHolder.getSurface());
        } else {
            throw new IllegalStateException();
        }
    }

    public synchronized void addSurface(int surfaceId, Surface surface, boolean isRecordable) {
        checkReleased();
        mRendererHolder.addSurface(surfaceId, surface, isRecordable);
    }

    public synchronized void removeSurface(int surfaceId) {
        if (mRendererHolder != null) {
            mRendererHolder.removeSurface(surfaceId);
        }
    }

    @Override
    public void captureStill() {
        super.captureStill();
    }

    @Override
    public void captureStill(final String path) {
        checkReleased();
        post(new Runnable() {
            @Override
            public void run() {
                synchronized (UVCCameraHandlerMultiSurface.this) {
                    if (mRendererHolder != null) {
                        mRendererHolder.captureStill(path);
                        updateMedia(path);
                    }
                }
            }
        });
    }
}
