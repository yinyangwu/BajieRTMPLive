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

package com.serenegiant.usb;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UVCCamera {
    private static final String TAG = "UVCCamera";
    private static final boolean DEBUG = false;

    private static final String DEFAULT_USBFS = "/dev/bus/usb";

    public static final int DEFAULT_PREVIEW_WIDTH = 1280;
    public static final int DEFAULT_PREVIEW_HEIGHT = 720;
    public static final int DEFAULT_PREVIEW_MODE = 0;
    public static final int DEFAULT_PREVIEW_MIN_FPS = 1;
    public static final int DEFAULT_PREVIEW_MAX_FPS = 30;
    public static final float DEFAULT_BANDWIDTH = 1.0f;

    public static final int FRAME_FORMAT_YUYV = 0;
    public static final int FRAME_FORMAT_MJPEG = 1;

    public static final int PIXEL_FORMAT_RAW = 0;
    public static final int PIXEL_FORMAT_YUV = 1;
    public static final int PIXEL_FORMAT_RGB565 = 2;
    public static final int PIXEL_FORMAT_RGBX = 3;
    public static final int PIXEL_FORMAT_YUV420SP = 4;
    public static final int PIXEL_FORMAT_NV21 = 5;        // = YVU420SemiPlanar

    //--------------------------------------------------------------------------------
    public static final int CTRL_SCANNING = 0x00000001;    // D0:  Scanning Mode
    public static final int CTRL_AE = 0x00000002;    // D1:  Auto-Exposure Mode
    public static final int CTRL_AE_PRIORITY = 0x00000004;    // D2:  Auto-Exposure Priority
    public static final int CTRL_AE_ABS = 0x00000008;    // D3:  Exposure Time (Absolute)
    public static final int CTRL_AR_REL = 0x00000010;    // D4:  Exposure Time (Relative)
    public static final int CTRL_FOCUS_ABS = 0x00000020;    // D5:  Focus (Absolute)
    public static final int CTRL_FOCUS_REL = 0x00000040;    // D6:  Focus (Relative)
    public static final int CTRL_IRIS_ABS = 0x00000080;    // D7:  Iris (Absolute)
    public static final int CTRL_IRIS_REL = 0x00000100;    // D8:  Iris (Relative)
    public static final int CTRL_ZOOM_ABS = 0x00000200;    // D9:  Zoom (Absolute)
    public static final int CTRL_ZOOM_REL = 0x00000400;    // D10: Zoom (Relative)
    public static final int CTRL_PANTILT_ABS = 0x00000800;    // D11: PanTilt (Absolute)
    public static final int CTRL_PANTILT_REL = 0x00001000;    // D12: PanTilt (Relative)
    public static final int CTRL_ROLL_ABS = 0x00002000;    // D13: Roll (Absolute)
    public static final int CTRL_ROLL_REL = 0x00004000;    // D14: Roll (Relative)
    public static final int CTRL_FOCUS_AUTO = 0x00020000;    // D17: Focus, Auto
    public static final int CTRL_PRIVACY = 0x00040000;    // D18: Privacy
    public static final int CTRL_FOCUS_SIMPLE = 0x00080000;    // D19: Focus, Simple
    public static final int CTRL_WINDOW = 0x00100000;    // D20: Window

    public static final int PU_BRIGHTNESS = 0x80000001;    // D0: Brightness
    public static final int PU_CONTRAST = 0x80000002;    // D1: Contrast
    public static final int PU_HUE = 0x80000004;    // D2: Hue
    public static final int PU_SATURATION = 0x80000008;    // D3: Saturation
    public static final int PU_SHARPNESS = 0x80000010;    // D4: Sharpness
    public static final int PU_GAMMA = 0x80000020;    // D5: Gamma
    public static final int PU_WB_TEMP = 0x80000040;    // D6: White Balance Temperature
    public static final int PU_WB_COMPO = 0x80000080;    // D7: White Balance Component
    public static final int PU_BACKLIGHT = 0x80000100;    // D8: Backlight Compensation
    public static final int PU_GAIN = 0x80000200;    // D9: Gain
    public static final int PU_POWER_LF = 0x80000400;    // D10: Power Line Frequency
    public static final int PU_HUE_AUTO = 0x80000800;    // D11: Hue, Auto
    public static final int PU_WB_TEMP_AUTO = 0x80001000;    // D12: White Balance Temperature, Auto
    public static final int PU_WB_COMPO_AUTO = 0x80002000;    // D13: White Balance Component, Auto
    public static final int PU_DIGITAL_MULT = 0x80004000;    // D14: Digital Multiplier
    public static final int PU_DIGITAL_LIMIT = 0x80008000;    // D15: Digital Multiplier Limit
    public static final int PU_AVIDEO_STD = 0x80010000;    // D16: Analog Video Standard
    public static final int PU_AVIDEO_LOCK = 0x80020000;    // D17: Analog Video Lock Status
    public static final int PU_CONTRAST_AUTO = 0x80040000;    // D18: Contrast, Auto
    public static final int PU_EXPOSURE = 0x80040001;    // 曝光

    // uvc_status_class from libuvc.h
    public static final int STATUS_CLASS_CONTROL = 0x10;
    public static final int STATUS_CLASS_CONTROL_CAMERA = 0x11;
    public static final int STATUS_CLASS_CONTROL_PROCESSING = 0x12;

    // uvc_status_attribute from libuvc.h
    public static final int STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00;
    public static final int STATUS_ATTRIBUTE_INFO_CHANGE = 0x01;
    public static final int STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02;
    public static final int STATUS_ATTRIBUTE_UNKNOWN = 0xff;

    static {
        System.loadLibrary("jpeg-turbo1500");
        System.loadLibrary("usb100");
        System.loadLibrary("uvc");
        System.loadLibrary("UVCCamera");
    }

    private UsbControlBlock mCtrlBlock;
    private long mControlSupports;// カメラコントロールでサポートしている機能フラグ
    private long mProcSupports;// プロセッシングユニットでサポートしている機能フラグ
    private int mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
    private int mCurrentWidth = DEFAULT_PREVIEW_WIDTH;
    private int mCurrentHeight = DEFAULT_PREVIEW_HEIGHT;
    private float mCurrentBandwidthFactor = DEFAULT_BANDWIDTH;
    private String mSupportedSize;
    // these fields from here are accessed from native code and do not change name and remove
    protected long mNativePtr;
    protected int mScanningModeMin, mScanningModeMax, mScanningModeDef;
    protected int mExposureModeMin, mExposureModeMax, mExposureModeDef;
    protected int mExposurePriorityMin, mExposurePriorityMax, mExposurePriorityDef;
    protected int mExposureMin, mExposureMax, mExposureDef;
    protected int mAutoFocusMin, mAutoFocusMax, mAutoFocusDef;
    private int mFocusMin, mFocusMax, mFocusDef;
    protected int mFocusRelMin, mFocusRelMax, mFocusRelDef;
    protected int mFocusSimpleMin, mFocusSimpleMax, mFocusSimpleDef;
    protected int mIrisMin, mIrisMax, mIrisDef;
    protected int mIrisRelMin, mIrisRelMax, mIrisRelDef;
    protected int mPanMin, mPanMax, mPanDef;
    protected int mTiltMin, mTiltMax, mTiltDef;
    protected int mRollMin, mRollMax, mRollDef;
    protected int mPanRelMin, mPanRelMax, mPanRelDef;
    protected int mTiltRelMin, mTiltRelMax, mTiltRelDef;
    protected int mRollRelMin, mRollRelMax, mRollRelDef;
    protected int mPrivacyMin, mPrivacyMax, mPrivacyDef;
    protected int mAutoWhiteBlanceMin, mAutoWhiteBlanceMax, mAutoWhiteBlanceDef;
    protected int mAutoWhiteBlanceCompoMin, mAutoWhiteBlanceCompoMax, mAutoWhiteBlanceCompoDef;
    private int mWhiteBlanceMin, mWhiteBlanceMax, mWhiteBlanceDef;
    protected int mWhiteBlanceCompoMin, mWhiteBlanceCompoMax, mWhiteBlanceCompoDef;
    protected int mWhiteBlanceRelMin, mWhiteBlanceRelMax, mWhiteBlanceRelDef;
    protected int mBacklightCompMin, mBacklightCompMax, mBacklightCompDef;
    private int mBrightnessMin, mBrightnessMax, mBrightnessDef;
    private int mContrastMin, mContrastMax, mContrastDef;
    private int mSharpnessMin, mSharpnessMax, mSharpnessDef;
    private int mGainMin, mGainMax, mGainDef;
    private int mGammaMin, mGammaMax, mGammaDef;
    private int mSaturationMin, mSaturationMax, mSaturationDef;
    private int mHueMin, mHueMax, mHueDef;
    private int mZoomMin, mZoomMax, mZoomDef;
    protected int mZoomRelMin, mZoomRelMax, mZoomRelDef;
    protected int mPowerlineFrequencyMin, mPowerlineFrequencyMax, mPowerlineFrequencyDef;
    protected int mMultiplierMin, mMultiplierMax, mMultiplierDef;
    protected int mMultiplierLimitMin, mMultiplierLimitMax, mMultiplierLimitDef;
    protected int mAnalogVideoStandardMin, mAnalogVideoStandardMax, mAnalogVideoStandardDef;
    protected int mAnalogVideoLockStateMin, mAnalogVideoLockStateMax, mAnalogVideoLockStateDef;
    // until here

    /**
     * the constructor of this class should be call within the thread that has a looper
     * (UI thread or a thread that called Looper.prepare)
     */
    public UVCCamera() {
        mNativePtr = nativeCreate();
        mSupportedSize = null;
    }

    /**
     * connect to a UVC camera
     * USB permission is necessary before this method is called
     */
    public synchronized void open(UsbControlBlock ctrlBlock) {
        int result;
        try {
            mCtrlBlock = ctrlBlock.clone();
            result = nativeConnect(mNativePtr,
                    mCtrlBlock.getVendorId(), mCtrlBlock.getProductId(),
                    mCtrlBlock.getFileDescriptor(),
                    mCtrlBlock.getBusNum(),
                    mCtrlBlock.getDevNum(),
                    getUSBFSName(mCtrlBlock));
        } catch (Exception e) {
            Log.w(TAG, e);
            result = -1;
        }
        if (result != 0) {
            throw new UnsupportedOperationException("open failed:result=" + result);
        }
        if (mNativePtr != 0 && TextUtils.isEmpty(mSupportedSize)) {
            mSupportedSize = nativeGetSupportedSize(mNativePtr);
        }
        nativeSetPreviewSize(mNativePtr, DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT,
                DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, DEFAULT_PREVIEW_MODE, DEFAULT_BANDWIDTH);
    }

    /**
     * set status callback
     */
    public void setStatusCallback(IStatusCallback callback) {
        if (mNativePtr != 0) {
            nativeSetStatusCallback(mNativePtr, callback);
        }
    }

    /**
     * set button callback
     */
    public void setButtonCallback(IButtonCallback callback) {
        if (mNativePtr != 0) {
            nativeSetButtonCallback(mNativePtr, callback);
        }
    }

    /**
     * close and release UVC camera
     */
    public synchronized void close() {
        stopPreview();
        if (mNativePtr != 0) {
            nativeRelease(mNativePtr);
        }
        if (mCtrlBlock != null) {
            mCtrlBlock.close();
            mCtrlBlock = null;
        }
        mControlSupports = mProcSupports = 0;
        mCurrentFrameFormat = -1;
        mCurrentBandwidthFactor = 0;
        mSupportedSize = null;
        if (DEBUG) Log.v(TAG, "close:finished");
    }

    public UsbDevice getDevice() {
        return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
    }

    public String getDeviceName() {
        return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
    }

    public UsbControlBlock getUsbControlBlock() {
        return mCtrlBlock;
    }

    public synchronized String getSupportedSize() {
        return !TextUtils.isEmpty(mSupportedSize) ? mSupportedSize : (mSupportedSize = nativeGetSupportedSize(mNativePtr));
    }

    public Size getPreviewSize() {
        Size result = null;
        List<Size> list = getSupportedSizeList();
        for (Size sz : list) {
            if ((sz.width == mCurrentWidth)
                    || (sz.height == mCurrentHeight)) {
                result = sz;
                break;
            }
        }
        return result;
    }

    /**
     * Set preview size and preview mode
     */
    public void setPreviewSize(int width, int height) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, mCurrentFrameFormat, mCurrentBandwidthFactor);
    }

    /**
     * Set preview size and preview mode
     *
     * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     */
    public void setPreviewSize(int width, int height, int frameFormat) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, frameFormat, mCurrentBandwidthFactor);
    }

    /**
     * Set preview size and preview mode
     *
     * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     * @param bandwidth   [0.0f,1.0f]
     */
    public void setPreviewSize(int width, int height, int frameFormat, float bandwidth) {
        setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, frameFormat, bandwidth);
    }

    /**
     * Set preview size and preview mode
     *
     * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
     */
    public void setPreviewSize(int width, int height, int min_fps, int max_fps, int frameFormat, float bandwidthFactor) {
        if ((width == 0) || (height == 0))
            throw new IllegalArgumentException("invalid preview size");
        if (mNativePtr != 0) {
            int result = nativeSetPreviewSize(mNativePtr, width, height, min_fps, max_fps, frameFormat, bandwidthFactor);
            if (result != 0)
                throw new IllegalArgumentException("Failed to set preview size");
            mCurrentFrameFormat = frameFormat;
            mCurrentWidth = width;
            mCurrentHeight = height;
            mCurrentBandwidthFactor = bandwidthFactor;
        }
    }

    public List<Size> getSupportedSizeList() {
        int type = (mCurrentFrameFormat > 0) ? 6 : 4;
        return getSupportedSize(type, mSupportedSize);
    }

    public static List<Size> getSupportedSize(int type, String supportedSize) {
        List<Size> result = new ArrayList<>();
        if (!TextUtils.isEmpty(supportedSize))
            try {
                JSONObject json = new JSONObject(supportedSize);
                JSONArray formats = json.getJSONArray("formats");
                int format_nums = formats.length();
                for (int i = 0; i < format_nums; i++) {
                    JSONObject format = formats.getJSONObject(i);
                    if (format.has("type") && format.has("size")) {
                        int format_type = format.getInt("type");
                        if ((format_type == type) || (type == -1)) {
                            addSize(format, format_type, 0, result);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        return result;
    }

    private static void addSize(JSONObject format, int formatType, int frameType, List<Size> size_list) throws JSONException {
        JSONArray array = format.getJSONArray("size");
        int length = array.length();
        for (int j = 0; j < length; j++) {
            String[] sz = array.getString(j).split("x");
            try {
                size_list.add(new Size(formatType, frameType, j, Integer.parseInt(sz[0]), Integer.parseInt(sz[1])));
            } catch (Exception e) {
                break;
            }
        }
    }

    /**
     * set preview surface with SurfaceHolder</br>
     * you can use SurfaceHolder came from SurfaceView/GLSurfaceView
     */
    public synchronized void setPreviewDisplay(SurfaceHolder holder) {
        nativeSetPreviewDisplay(mNativePtr, holder.getSurface());
    }

    /**
     * set preview surface with SurfaceTexture.
     * this method require API >= 14
     */
    public synchronized void setPreviewTexture(SurfaceTexture texture) {
        Surface surface = new Surface(texture);    // XXX API >= 14
        nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set preview surface with Surface
     */
    public synchronized void setPreviewDisplay(Surface surface) {
        nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set frame callback
     */
    public void setFrameCallback(IFrameCallback callback, int pixelFormat) {
        if (mNativePtr != 0) {
            nativeSetFrameCallback(mNativePtr, callback, pixelFormat);
        }
    }

    /**
     * start preview
     */
    public synchronized void startPreview() {
        if (mCtrlBlock != null) {
            nativeStartPreview(mNativePtr);
        }
    }

    /**
     * stop preview
     */
    public synchronized void stopPreview() {
        setFrameCallback(null, 0);
        if (mCtrlBlock != null) {
            nativeStopPreview(mNativePtr);
        }
    }

    /**
     * destroy UVCCamera object
     */
    public synchronized void destroy() {
        close();
        if (mNativePtr != 0) {
            nativeDestroy(mNativePtr);
            mNativePtr = 0;
        }
    }

    // wrong result may return when you call this just after camera open.
    // it is better to wait several hundred millSeconds.
    public boolean checkSupportFlag(long flag) {
        updateCameraParams();
        if ((flag & 0x80000000) == 0x80000000)
            return ((mProcSupports & flag) == (flag & 0x7ffffffF));
        else
            return (mControlSupports & flag) == flag;
    }

    //================================================================================
    public synchronized void setAutoFocus(boolean autoFocus) {
        if (mNativePtr != 0) {
            nativeSetAutoFocus(mNativePtr, autoFocus);
        }
    }

    public synchronized boolean getAutoFocus() {
        boolean result = true;
        if (mNativePtr != 0) {
            result = nativeGetAutoFocus(mNativePtr) > 0;
        }
        return result;
    }
//================================================================================

    /**
     * @param focus [%]
     */
    public synchronized void setFocus(int focus) {
        if (mNativePtr != 0) {
            float range = Math.abs(mFocusMax - mFocusMin);
            if (range > 0)
                nativeSetFocus(mNativePtr, (int) (focus / 100.f * range) + mFocusMin);
        }
    }

    /**
     * @return focus[%]
     */
    public synchronized int getFocus(int focus_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateFocusLimit(mNativePtr);
            float range = Math.abs(mFocusMax - mFocusMin);
            if (range > 0) {
                result = (int) ((focus_abs - mFocusMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return focus[%]
     */
    public synchronized int getFocus() {
        return getFocus(nativeGetFocus(mNativePtr));
    }

    public synchronized void resetFocus() {
        if (mNativePtr != 0) {
            nativeSetFocus(mNativePtr, mFocusDef);
        }
    }

    //================================================================================
    public synchronized void setAutoWhiteBlance(boolean autoWhiteBlance) {
        if (mNativePtr != 0) {
            nativeSetAutoWhiteBlance(mNativePtr, autoWhiteBlance);
        }
    }

    public synchronized boolean getAutoWhiteBlance() {
        boolean result = true;
        if (mNativePtr != 0) {
            result = nativeGetAutoWhiteBlance(mNativePtr) > 0;
        }
        return result;
    }

//================================================================================

    /**
     * @param whiteBlance [%]
     */
    public synchronized void setWhiteBlance(int whiteBlance) {
        if (mNativePtr != 0) {
            float range = Math.abs(mWhiteBlanceMax - mWhiteBlanceMin);
            if (range > 0)
                nativeSetWhiteBlance(mNativePtr, (int) (whiteBlance / 100.f * range) + mWhiteBlanceMin);
        }
    }

    /**
     * @return whiteBlance[%]
     */
    public synchronized int getWhiteBlance(int whiteBlance_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateWhiteBlanceLimit(mNativePtr);
            float range = Math.abs(mWhiteBlanceMax - mWhiteBlanceMin);
            if (range > 0) {
                result = (int) ((whiteBlance_abs - mWhiteBlanceMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return white blance[%]
     */
    public synchronized int getWhiteBlance() {
        return getFocus(nativeGetWhiteBlance(mNativePtr));
    }

    public synchronized void resetWhiteBlance() {
        if (mNativePtr != 0) {
            nativeSetWhiteBlance(mNativePtr, mWhiteBlanceDef);
        }
    }
//================================================================================

    /**
     * @param brightness [%]
     */
    public synchronized void setBrightness(int brightness) {
        if (mNativePtr != 0) {
            float range = Math.abs(mBrightnessMax - mBrightnessMin);
            if (range > 0)
                nativeSetBrightness(mNativePtr, (int) (brightness / 100.f * range) + mBrightnessMin);
        }
    }

    /**
     * @return brightness[%]
     */
    public synchronized int getBrightness(int brightness_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateBrightnessLimit(mNativePtr);
            float range = Math.abs(mBrightnessMax - mBrightnessMin);
            if (range > 0) {
                result = (int) ((brightness_abs - mBrightnessMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return brightness[%]
     */
    public synchronized int getBrightness() {
        return getBrightness(nativeGetBrightness(mNativePtr));
    }

    public synchronized void resetBrightness() {
        if (mNativePtr != 0) {
            nativeSetBrightness(mNativePtr, mBrightnessDef);
        }
    }

//================================================================================

    /**
     * @param contrast [%]
     */
    public synchronized void setContrast(int contrast) {
        if (mNativePtr != 0) {
            nativeUpdateContrastLimit(mNativePtr);
            float range = Math.abs(mContrastMax - mContrastMin);
            if (range > 0)
                nativeSetContrast(mNativePtr, (int) (contrast / 100.f * range) + mContrastMin);
        }
    }

    /**
     * @return contrast[%]
     */
    public synchronized int getContrast(int contrast_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            float range = Math.abs(mContrastMax - mContrastMin);
            if (range > 0) {
                result = (int) ((contrast_abs - mContrastMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return contrast[%]
     */
    public synchronized int getContrast() {
        return getContrast(nativeGetContrast(mNativePtr));
    }

    public synchronized void resetContrast() {
        if (mNativePtr != 0) {
            nativeSetContrast(mNativePtr, mContrastDef);
        }
    }

//================================================================================

    /**
     * @param sharpness [%]
     */
    public synchronized void setSharpness(int sharpness) {
        if (mNativePtr != 0) {
            float range = Math.abs(mSharpnessMax - mSharpnessMin);
            if (range > 0)
                nativeSetSharpness(mNativePtr, (int) (sharpness / 100.f * range) + mSharpnessMin);
        }
    }

    /**
     * @return sharpness[%]
     */
    public synchronized int getSharpness(int sharpness_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateSharpnessLimit(mNativePtr);
            float range = Math.abs(mSharpnessMax - mSharpnessMin);
            if (range > 0) {
                result = (int) ((sharpness_abs - mSharpnessMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return sharpness[%]
     */
    public synchronized int getSharpness() {
        return getSharpness(nativeGetSharpness(mNativePtr));
    }

    public synchronized void resetSharpness() {
        if (mNativePtr != 0) {
            nativeSetSharpness(mNativePtr, mSharpnessDef);
        }
    }
//================================================================================

    /**
     * @param gain [%]
     */
    public synchronized void setGain(int gain) {
        if (mNativePtr != 0) {
            float range = Math.abs(mGainMax - mGainMin);
            if (range > 0)
                nativeSetGain(mNativePtr, (int) (gain / 100.f * range) + mGainMin);
        }
    }

    /**
     * @return gain[%]
     */
    public synchronized int getGain(int gain_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateGainLimit(mNativePtr);
            float range = Math.abs(mGainMax - mGainMin);
            if (range > 0) {
                result = (int) ((gain_abs - mGainMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return gain[%]
     */
    public synchronized int getGain() {
        return getGain(nativeGetGain(mNativePtr));
    }

    public synchronized void resetGain() {
        if (mNativePtr != 0) {
            nativeSetGain(mNativePtr, mGainDef);
        }
    }

//================================================================================

    /**
     * @param gamma [%]
     */
    public synchronized void setGamma(int gamma) {
        if (mNativePtr != 0) {
            float range = Math.abs(mGammaMax - mGammaMin);
            if (range > 0)
                nativeSetGamma(mNativePtr, (int) (gamma / 100.f * range) + mGammaMin);
        }
    }

    /**
     * @return gamma[%]
     */
    public synchronized int getGamma(int gamma_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateGammaLimit(mNativePtr);
            float range = Math.abs(mGammaMax - mGammaMin);
            if (range > 0) {
                result = (int) ((gamma_abs - mGammaMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return gamma[%]
     */
    public synchronized int getGamma() {
        return getGamma(nativeGetGamma(mNativePtr));
    }

    public synchronized void resetGamma() {
        if (mNativePtr != 0) {
            nativeSetGamma(mNativePtr, mGammaDef);
        }
    }

//================================================================================

    /**
     * @param saturation [%]
     */
    public synchronized void setSaturation(int saturation) {
        if (mNativePtr != 0) {
            float range = Math.abs(mSaturationMax - mSaturationMin);
            if (range > 0)
                nativeSetSaturation(mNativePtr, (int) (saturation / 100.f * range) + mSaturationMin);
        }
    }

    /**
     * @return saturation[%]
     */
    public synchronized int getSaturation(int saturation_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateSaturationLimit(mNativePtr);
            float range = Math.abs(mSaturationMax - mSaturationMin);
            if (range > 0) {
                result = (int) ((saturation_abs - mSaturationMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return saturation[%]
     */
    public synchronized int getSaturation() {
        return getSaturation(nativeGetSaturation(mNativePtr));
    }

    public synchronized void resetSaturation() {
        if (mNativePtr != 0) {
            nativeSetSaturation(mNativePtr, mSaturationDef);
        }
    }
//================================================================================

    /**
     * @param hue [%]
     */
    public synchronized void setHue(int hue) {
        if (mNativePtr != 0) {
            float range = Math.abs(mHueMax - mHueMin);
            if (range > 0)
                nativeSetHue(mNativePtr, (int) (hue / 100.f * range) + mHueMin);
        }
    }

    /**
     * @return hue[%]
     */
    public synchronized int getHue(int hue_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateHueLimit(mNativePtr);
            float range = Math.abs(mHueMax - mHueMin);
            if (range > 0) {
                result = (int) ((hue_abs - mHueMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return hue[%]
     */
    public synchronized int getHue() {
        return getHue(nativeGetHue(mNativePtr));
    }

    public synchronized void resetHue() {
        if (mNativePtr != 0) {
            nativeSetHue(mNativePtr, mSaturationDef);
        }
    }

//================================================================================
    public void setPowerlineFrequency(int frequency) {
        if (mNativePtr != 0)
            nativeSetPowerlineFrequency(mNativePtr, frequency);
    }

    public int getPowerlineFrequency() {
        return nativeGetPowerlineFrequency(mNativePtr);
    }

//================================================================================

    /**
     * this may not work well with some combination of camera and device
     *
     * @param zoom [%]
     */
    public synchronized void setZoom(int zoom) {
        if (mNativePtr != 0) {
            float range = Math.abs(mZoomMax - mZoomMin);
            if (range > 0) {
                int z = (int) (zoom / 100.f * range) + mZoomMin;
                nativeSetZoom(mNativePtr, z);
            }
        }
    }

    /**
     * @return zoom[%]
     */
    public synchronized int getZoom(int zoom_abs) {
        int result = 0;
        if (mNativePtr != 0) {
            nativeUpdateZoomLimit(mNativePtr);
            float range = Math.abs(mZoomMax - mZoomMin);
            if (range > 0) {
                result = (int) ((zoom_abs - mZoomMin) * 100.f / range);
            }
        }
        return result;
    }

    /**
     * @return zoom[%]
     */
    public synchronized int getZoom() {
        return getZoom(nativeGetZoom(mNativePtr));
    }

    public synchronized void resetZoom() {
        if (mNativePtr != 0) {
            nativeSetZoom(mNativePtr, mZoomDef);
        }
    }

//================================================================================

    /**
     * 设置逆光补偿
     */
    public synchronized void setBacklightComp(int value) {
        if (mNativePtr != 0) {
            nativeSetBacklightComp(mNativePtr, value);
        }
    }

    /**
     * 设置逆光补偿
     */
    public synchronized int getBacklightComp() {
        int result = 0;
        if (mNativePtr != 0) {
            result = nativeGetBacklightComp(mNativePtr);
        }
        return result;
    }

    /**
     * 重置逆光补偿
     */
    public synchronized void resetBacklightComp() {
        if (mNativePtr != 0) {
            nativeSetBacklightComp(mNativePtr, 30);
        }
    }

//================================================================================

    /**
     * 曝光
     */
    public synchronized void setExposure(int value) {
        if (mNativePtr != 0) {
            nativeSetExposure(mNativePtr, value);
        }
    }

    /**
     * 曝光
     */
    public synchronized int getExposure() {
        if (mNativePtr != 0) {
            return nativeGetExposure(mNativePtr);
        }
        return -1;
    }

    /**
     * 曝光
     */
    public synchronized void resetExposure() {
        if (mNativePtr != 0) {
            nativeSetExposure(mNativePtr, 0);
        }
    }

//================================================================================
    public synchronized void updateCameraParams() {
        if (mNativePtr != 0) {
            if ((mControlSupports == 0) || (mProcSupports == 0)) {
                // サポートしている機能フラグを取得
                if (mControlSupports == 0)
                    mControlSupports = nativeGetCtrlSupports(mNativePtr);
                if (mProcSupports == 0)
                    mProcSupports = nativeGetProcSupports(mNativePtr);
                // 設定値を取得
                if ((mControlSupports != 0) && (mProcSupports != 0)) {
                    nativeUpdateBrightnessLimit(mNativePtr);
                    nativeUpdateContrastLimit(mNativePtr);
                    nativeUpdateSharpnessLimit(mNativePtr);
                    nativeUpdateGainLimit(mNativePtr);
                    nativeUpdateGammaLimit(mNativePtr);
                    nativeUpdateSaturationLimit(mNativePtr);
                    nativeUpdateHueLimit(mNativePtr);
                    nativeUpdateZoomLimit(mNativePtr);
                    nativeUpdateWhiteBlanceLimit(mNativePtr);
                    nativeUpdateFocusLimit(mNativePtr);
                }
                if (DEBUG) {
                    dumpControls(mControlSupports);
                    dumpProc(mProcSupports);
                    Log.v(TAG, String.format("Brightness:min=%d,max=%d,def=%d", mBrightnessMin, mBrightnessMax, mBrightnessDef));
                    Log.v(TAG, String.format("Contrast:min=%d,max=%d,def=%d", mContrastMin, mContrastMax, mContrastDef));
                    Log.v(TAG, String.format("Sharpness:min=%d,max=%d,def=%d", mSharpnessMin, mSharpnessMax, mSharpnessDef));
                    Log.v(TAG, String.format("Gain:min=%d,max=%d,def=%d", mGainMin, mGainMax, mGainDef));
                    Log.v(TAG, String.format("Gamma:min=%d,max=%d,def=%d", mGammaMin, mGammaMax, mGammaDef));
                    Log.v(TAG, String.format("Saturation:min=%d,max=%d,def=%d", mSaturationMin, mSaturationMax, mSaturationDef));
                    Log.v(TAG, String.format("Hue:min=%d,max=%d,def=%d", mHueMin, mHueMax, mHueDef));
                    Log.v(TAG, String.format("Zoom:min=%d,max=%d,def=%d", mZoomMin, mZoomMax, mZoomDef));
                    Log.v(TAG, String.format("WhiteBlance:min=%d,max=%d,def=%d", mWhiteBlanceMin, mWhiteBlanceMax, mWhiteBlanceDef));
                    Log.v(TAG, String.format("Focus:min=%d,max=%d,def=%d", mFocusMin, mFocusMax, mFocusDef));
                }
            }
        } else {
            mControlSupports = mProcSupports = 0;
        }
    }

    private static final String[] SUPPORTS_CTRL = {
            "D0:  Scanning Mode",
            "D1:  Auto-Exposure Mode",
            "D2:  Auto-Exposure Priority",
            "D3:  Exposure Time (Absolute)",
            "D4:  Exposure Time (Relative)",
            "D5:  Focus (Absolute)",
            "D6:  Focus (Relative)",
            "D7:  Iris (Absolute)",
            "D8:  Iris (Relative)",
            "D9:  Zoom (Absolute)",
            "D10: Zoom (Relative)",
            "D11: PanTilt (Absolute)",
            "D12: PanTilt (Relative)",
            "D13: Roll (Absolute)",
            "D14: Roll (Relative)",
            "D15: Reserved",
            "D16: Reserved",
            "D17: Focus, Auto",
            "D18: Privacy",
            "D19: Focus, Simple",
            "D20: Window",
            "D21: Region of Interest",
            "D22: Reserved, set to zero",
            "D23: Reserved, set to zero",
    };

    private static final String[] SUPPORTS_PROC = {
            "D0: Brightness",
            "D1: Contrast",
            "D2: Hue",
            "D3: Saturation",
            "D4: Sharpness",
            "D5: Gamma",
            "D6: White Balance Temperature",
            "D7: White Balance Component",
            "D8: Backlight Compensation",
            "D9: Gain",
            "D10: Power Line Frequency",
            "D11: Hue, Auto",
            "D12: White Balance Temperature, Auto",
            "D13: White Balance Component, Auto",
            "D14: Digital Multiplier",
            "D15: Digital Multiplier Limit",
            "D16: Analog Video Standard",
            "D17: Analog Video Lock Status",
            "D18: Contrast, Auto",
            "D19: Reserved. Set to zero",
            "D20: Reserved. Set to zero",
            "D21: Reserved. Set to zero",
            "D22: Reserved. Set to zero",
            "D23: Reserved. Set to zero",
    };

    private static void dumpControls(long controlSupports) {
        Log.i(TAG, String.format("controlSupports=%x", controlSupports));
        for (int i = 0; i < SUPPORTS_CTRL.length; i++) {
            Log.i(TAG, SUPPORTS_CTRL[i] + ((controlSupports & (0x1 << i)) != 0 ? "=enabled" : "=disabled"));
        }
    }

    private static void dumpProc(long procSupports) {
        Log.i(TAG, String.format("procSupports=%x", procSupports));
        for (int i = 0; i < SUPPORTS_PROC.length; i++) {
            Log.i(TAG, SUPPORTS_PROC[i] + ((procSupports & (0x1 << i)) != 0 ? "=enabled" : "=disabled"));
        }
    }

    private String getUSBFSName(UsbControlBlock ctrlBlock) {
        String result = null;
        String name = ctrlBlock.getDeviceName();
        String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
        if ((v != null) && (v.length > 2)) {
            StringBuilder sb = new StringBuilder(v[0]);
            for (int i = 1; i < v.length - 2; i++)
                sb.append("/").append(v[i]);
            result = sb.toString();
        }
        if (TextUtils.isEmpty(result)) {
            Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
            result = DEFAULT_USBFS;
        }
        return result;
    }

    // #nativeCreate and #nativeDestroy are not static methods.
    private native long nativeCreate();

    private native void nativeDestroy(long id_camera);

    private native int nativeConnect(long id_camera, int venderId, int productId, int fileDescriptor, int busNum, int devAddr, String usbfs);

    private static native int nativeRelease(long id_camera);

    private static native int nativeSetStatusCallback(long mNativePtr, IStatusCallback callback);

    private static native int nativeSetButtonCallback(long mNativePtr, IButtonCallback callback);

    private static native int nativeSetPreviewSize(long id_camera, int width, int height, int min_fps, int max_fps, int mode, float bandwidth);

    private static native String nativeGetSupportedSize(long id_camera);

    private static native int nativeStartPreview(long id_camera);

    private static native int nativeStopPreview(long id_camera);

    private static native int nativeSetPreviewDisplay(long id_camera, Surface surface);

    private static native int nativeSetFrameCallback(long mNativePtr, IFrameCallback callback, int pixelFormat);

//**********************************************************************

    /**
     * start movie capturing(this should call while previewing)
     */
    public void startCapture(Surface surface) {
        if (mCtrlBlock != null && surface != null) {
            nativeSetCaptureDisplay(mNativePtr, surface);
        } else
            throw new NullPointerException("startCapture");
    }

    /**
     * stop movie capturing
     */
    public void stopCapture() {
        if (mCtrlBlock != null) {
            nativeSetCaptureDisplay(mNativePtr, null);
        }
    }

    private static native int nativeSetCaptureDisplay(long id_camera, Surface surface);

    private static native long nativeGetCtrlSupports(long id_camera);

    private static native long nativeGetProcSupports(long id_camera);

    private native int nativeUpdateScanningModeLimit(long id_camera);

    private static native int nativeSetScanningMode(long id_camera, int scanning_mode);

    private static native int nativeGetScanningMode(long id_camera);

    private native int nativeUpdateExposureModeLimit(long id_camera);

    private static native int nativeSetExposureMode(long id_camera, int exposureMode);

    private static native int nativeGetExposureMode(long id_camera);

    private native int nativeUpdateExposurePriorityLimit(long id_camera);

    private static native int nativeSetExposurePriority(long id_camera, int priority);

    private static native int nativeGetExposurePriority(long id_camera);

    private native int nativeUpdateExposureLimit(long id_camera);

    private static native int nativeSetExposure(long id_camera, int exposure);

    private static native int nativeGetExposure(long id_camera);

    private native int nativeUpdateExposureRelLimit(long id_camera);

    private static native int nativeSetExposureRel(long id_camera, int exposure_rel);

    private static native int nativeGetExposureRel(long id_camera);

    private native int nativeUpdateAutoFocusLimit(long id_camera);

    private static native int nativeSetAutoFocus(long id_camera, boolean autofocus);

    private static native int nativeGetAutoFocus(long id_camera);

    private native int nativeUpdateFocusLimit(long id_camera);

    private static native int nativeSetFocus(long id_camera, int focus);

    private static native int nativeGetFocus(long id_camera);

    private native int nativeUpdateFocusRelLimit(long id_camera);

    private static native int nativeSetFocusRel(long id_camera, int focus_rel);

    private static native int nativeGetFocusRel(long id_camera);

    private native int nativeUpdateIrisLimit(long id_camera);

    private static native int nativeSetIris(long id_camera, int iris);

    private static native int nativeGetIris(long id_camera);

    private native int nativeUpdateIrisRelLimit(long id_camera);

    private static native int nativeSetIrisRel(long id_camera, int iris_rel);

    private static native int nativeGetIrisRel(long id_camera);

    private native int nativeUpdatePanLimit(long id_camera);

    private static native int nativeSetPan(long id_camera, int pan);

    private static native int nativeGetPan(long id_camera);

    private native int nativeUpdatePanRelLimit(long id_camera);

    private static native int nativeSetPanRel(long id_camera, int pan_rel);

    private static native int nativeGetPanRel(long id_camera);

    private native int nativeUpdateTiltLimit(long id_camera);

    private static native int nativeSetTilt(long id_camera, int tilt);

    private static native int nativeGetTilt(long id_camera);

    private native int nativeUpdateTiltRelLimit(long id_camera);

    private static native int nativeSetTiltRel(long id_camera, int tilt_rel);

    private static native int nativeGetTiltRel(long id_camera);

    private native int nativeUpdateRollLimit(long id_camera);

    private static native int nativeSetRoll(long id_camera, int roll);

    private static native int nativeGetRoll(long id_camera);

    private native int nativeUpdateRollRelLimit(long id_camera);

    private static native int nativeSetRollRel(long id_camera, int roll_rel);

    private static native int nativeGetRollRel(long id_camera);

    private native int nativeUpdateAutoWhiteBlanceLimit(long id_camera);

    private static native int nativeSetAutoWhiteBlance(long id_camera, boolean autoWhiteBlance);

    private static native int nativeGetAutoWhiteBlance(long id_camera);

    private native int nativeUpdateAutoWhiteBlanceCompoLimit(long id_camera);

    private static native int nativeSetAutoWhiteBlanceCompo(long id_camera, boolean autoWhiteBlanceCompo);

    private static native int nativeGetAutoWhiteBlanceCompo(long id_camera);

    private native int nativeUpdateWhiteBlanceLimit(long id_camera);

    private static native int nativeSetWhiteBlance(long id_camera, int whiteBlance);

    private static native int nativeGetWhiteBlance(long id_camera);

    private native int nativeUpdateWhiteBlanceCompoLimit(long id_camera);

    private static native int nativeSetWhiteBlanceCompo(long id_camera, int whiteBlance_compo);

    private static native int nativeGetWhiteBlanceCompo(long id_camera);

    private native int nativeUpdateBacklightCompLimit(long id_camera);

    private static native int nativeSetBacklightComp(long id_camera, int backlight_comp);

    private static native int nativeGetBacklightComp(long id_camera);

    private native int nativeUpdateBrightnessLimit(long id_camera);

    private static native int nativeSetBrightness(long id_camera, int brightness);

    private static native int nativeGetBrightness(long id_camera);

    private native int nativeUpdateContrastLimit(long id_camera);

    private static native int nativeSetContrast(long id_camera, int contrast);

    private static native int nativeGetContrast(long id_camera);

    private native int nativeUpdateAutoContrastLimit(long id_camera);

    private static native int nativeSetAutoContrast(long id_camera, boolean autocontrast);

    private static native int nativeGetAutoContrast(long id_camera);

    private native int nativeUpdateSharpnessLimit(long id_camera);

    private static native int nativeSetSharpness(long id_camera, int sharpness);

    private static native int nativeGetSharpness(long id_camera);

    private native int nativeUpdateGainLimit(long id_camera);

    private static native int nativeSetGain(long id_camera, int gain);

    private static native int nativeGetGain(long id_camera);

    private native int nativeUpdateGammaLimit(long id_camera);

    private static native int nativeSetGamma(long id_camera, int gamma);

    private static native int nativeGetGamma(long id_camera);

    private native int nativeUpdateSaturationLimit(long id_camera);

    private static native int nativeSetSaturation(long id_camera, int saturation);

    private static native int nativeGetSaturation(long id_camera);

    private native int nativeUpdateHueLimit(long id_camera);

    private static native int nativeSetHue(long id_camera, int hue);

    private static native int nativeGetHue(long id_camera);

    private native int nativeUpdateAutoHueLimit(long id_camera);

    private static native int nativeSetAutoHue(long id_camera, boolean autohue);

    private static native int nativeGetAutoHue(long id_camera);

    private native int nativeUpdatePowerlineFrequencyLimit(long id_camera);

    private static native int nativeSetPowerlineFrequency(long id_camera, int frequency);

    private static native int nativeGetPowerlineFrequency(long id_camera);

    private native int nativeUpdateZoomLimit(long id_camera);

    private static native int nativeSetZoom(long id_camera, int zoom);

    private static native int nativeGetZoom(long id_camera);

    private native int nativeUpdateZoomRelLimit(long id_camera);

    private static native int nativeSetZoomRel(long id_camera, int zoom_rel);

    private static native int nativeGetZoomRel(long id_camera);

    private native int nativeUpdateDigitalMultiplierLimit(long id_camera);

    private static native int nativeSetDigitalMultiplier(long id_camera, int multiplier);

    private static native int nativeGetDigitalMultiplier(long id_camera);

    private native int nativeUpdateDigitalMultiplierLimitLimit(long id_camera);

    private static native int nativeSetDigitalMultiplierLimit(long id_camera, int multiplier_limit);

    private static native int nativeGetDigitalMultiplierLimit(long id_camera);

    private native int nativeUpdateAnalogVideoStandardLimit(long id_camera);

    private static native int nativeSetAnalogVideoStandard(long id_camera, int standard);

    private static native int nativeGetAnalogVideoStandard(long id_camera);

    private native int nativeUpdateAnalogVideoLockStateLimit(long id_camera);

    private static native int nativeSetAnalogVideoLoackState(long id_camera, int state);

    private static native int nativeGetAnalogVideoLoackState(long id_camera);

    private native int nativeUpdatePrivacyLimit(long id_camera);

    private static native int nativeSetPrivacy(long id_camera, boolean privacy);

    private static native int nativeGetPrivacy(long id_camera);
}
