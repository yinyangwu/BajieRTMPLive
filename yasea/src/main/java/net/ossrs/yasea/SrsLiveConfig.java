package net.ossrs.yasea;

/**
 * Desc:直播相关配置方案
 * <p>
 * 下面是OPPO FIND X摄像头支持的预览尺寸：
 * <p>
 * width:height=2340:1080
 * width:height=2304:1728
 * width:height=2304:1296
 * width:height=2280:1080
 * width:height=2048:1536
 * width:height=1920:1440
 * width:height=1920:1080
 * width:height=1840:1380
 * width:height=1600:1200
 * width:height=1440:1080
 * width:height=1280:400
 * width:height=1280:960
 * width:height=1280:768
 * width:height=1280:720
 * width:height=1024:738
 * width:height=1024:768
 * width:height=960:720
 * width:height=800:600
 * width:height=800:480
 * width:height=720:480
 * width:height=640:480
 * width:height=352:288
 * width:height=320:240
 * width:height=176:144
 * <p>
 * Created by YoungWu on 2019/7/10.
 */
public class SrsLiveConfig {
    /**
     * 视频编码方案
     */
    public static final String VIDEO_CODEC = "video/avc";
    /**
     * 音频编码方案
     */
    public static final String AUDIO_CODEC = "audio/mp4a-latm";

    /**
     * xh264编码方案，快速
     */
    public static final String XH264_VERY_FAST_PRESET = "veryfast";
    /**
     * xh264编码方案，超级快
     */
    public static final String XH264_SUPER_FAST_PRESET = "superfast";
    /**
     * xh264编码方案，极快
     */
    public static final String XH264_ULTRA_FAST_PRESET = "ultrafast";

    /**
     * 标清分辨率宽度
     */
    public static final int STANDARD_DEFINITION_WIDTH = 640;
    /**
     * 标清分辨率高度
     */
    public static final int STANDARD_DEFINITION_HEIGHT = 480;
    /**
     * 高清分辨率宽度
     */
    public static final int HIGH_DEFINITION_WIDTH = 1280;
    /**
     * 高清分辨率高度
     */
    public static final int HIGH_DEFINITION_HEIGHT = 720;
    /**
     * 全高清分辨率宽度
     */
    public static final int FULL_HIGH_DEFINITION_WIDTH = 1920;
    /**
     * 全高清分辨率高度
     */
    public static final int FULL_HIGH_DEFINITION_HEIGHT = 1080;

    /**
     * 标清视频比特率2Mbps
     */
    public static final int STANDARD_DEFINITION_BITRATE = 2 * 1024 * 1024;
    /**
     * 高清视频采比特率4Mbps
     */
    public static final int HIGH_DEFINITION_BITRATE = 4 * 1024 * 1024;
    /**
     * 全高清视频比特率6Mbps
     */
    public static final int FULL_HIGH_DEFINITION_BITRATE = 6 * 1024 * 1024;

    /**
     * 普通音频比特率128kbps
     */
    public static final int NORMAL_QUALITY_BITRATE = 128 * 1024;
    /**
     * 高质量音频比特率192Kbps
     */
    public static final int HIGH_QUALITY_BITRATE = 192 * 1024;
    /**
     * 无损音频比特率320Kbps
     */
    public static final int APE_FLAC_BITRATE = 320 * 1024;

    /**
     * 较差帧率，每秒15帧
     */
    public static final int POOR_FPS = 15;
    /**
     * 普通帧率，每秒24帧
     */
    public static final int NORMAL_FPS = 24;
    /**
     * 高帧率，每秒30帧
     */
    public static final int HIGH_FPS = 30;

    /**
     * I帧间隔周期，最长每10秒出来一个I帧，否则就判断为当前网络状况较差
     */
    public static final int GOP = 10;

    /**
     * 音频采样率，44.1kHz
     */
    public static final int AUDIO_SAMPLE_RATE = 44100;
}
