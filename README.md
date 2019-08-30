# BajieRTMPLive--机身摄像头&&UVC摄像头实现直播方案

## 1.magicfilter：滤镜库，一共有37种效果。分别有

SUNRISE,SUNSET,WHITECAT,BLACKCAT,SKINWHITEN,BEAUTY,HEALTHY,ROMANCE,SAKURA,WARM,ANTIQUE,NOSTALGIA,CALM,LATTE,TENDER,COOL,EMERALD,EVERGREEN,SKETCH,AMARO,BRANNAN,BROOKLYN,EARLYBIRD,FREUD,HUDSON,INKWELL,KEVIN,N1977,NASHVILLE,PIXAR,RISE,SIERRA,SUTRO,TOASTER2,VALENCIA,WALDEN,XPROII。

## 2.mp4parser：MP4文件解析器，提供视频录制处理方法。

## 3.rtmppusher：RTMP协议封装库，提供传输音视频数据给服务器。

## 4.uvccamera：UVC摄像头封装库，通过UVC协议连接外接摄像头并获取摄像头帧数据。

## 5.yasea：处理摄像头预览画面，依赖xh264实现软件编码，处理RGBA帧数据转YUV，合成音视频数据

## 6.文件.bash_profile是我的环境变量，Macbook Pro 64位，使用NDK版本是android-ndk-r17c

## 下面是预览画面的展示：

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/0.png" width="360" alt="主页"/>

### a.机身摄像头直播，使用手机OPPO Find X，Android9.0系统

### 1.未开启直播之前画面

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a1.png" width="360" alt="未开启直播之前画面"/>

### 2.使用前置摄像头，默认高清模式1280*720

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a2.png" width="360" alt="使用前置摄像头"/>

### 3.使用后置摄像头

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a3.png" width="360" alt="使用后置摄像头"/>

### 4.开启闪光灯

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a4.png" width="360" alt="开启闪光灯"/>

### 5.选择分辨率，分为标清模式640*480，高清模式1280*720，全高清模式1920*1080，默认高清模式1280*720

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a5.png" width="360" alt="选择分辨率，分为标清模式640*480，高清模式1280*720，全高清模式1920*1080，默认高清模式1280*720"/>

### 6.标清模式，640*480

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a6.png" width="360" alt="标清模式，640*480"/>

### 7.全高清模式，1920*1080

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a7.png" width="360" alt="全高清模式，1920*1080"/>

### 8.选择滤镜

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a8.png" width="360" alt="选择滤镜"/>

### 9.选择warm，暖和效果滤镜

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a9.png" width="360" alt="选择warmer，暖和效果滤镜"/>

### 10.warm，暖和效果滤镜展示

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a10.png" width="360" alt="warmer，暖和效果滤镜展示"/>

### 11.选择传输数据类型，可支持视频+音频、仅视频、仅音频，默认是视频+音频

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a11.png" width="360" alt="选择传输数据类型，可支持视频+音频、仅视频、仅音频，默认是视频+音频"/>

### 12.仅视频

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a12.png" width="360" alt="仅视频"/>

### 13.仅音频

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a13.png" width="360" alt="仅音频"/>

### 14.开启软件编码模式，默认是使用硬件编码

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/a14.png" width="360" alt="开启软件编码模式，默认是使用硬件编码"/>

### 15.warm滤镜和没有使用滤镜录制的两段视频

BajieRTMPLive/screenshoot/InternalCamera/with_magicfilter.mp4
BajieRTMPLive/screenshoot/InternalCamera/without_magicfilter.mp4

### 16.srs流媒体服务器以及播放平台，预览画面展示

竖屏高清模式

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/as1.png" width="720" alt="竖屏高清模式"/>

横屏高清模式

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/as2.png" width="720" alt="横屏高清模式"/>

横屏全高清模式

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/as3.png" width="720" alt="横屏全高清模式"/>

横屏标清模式

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/as4.png" width="720" alt="横屏标清模式"/>

横屏高清模式warm滤镜效果

<img src="https://raw.githubusercontent.com/yinyangwu/BajieRTMPLive/master/screenshoot/InternalCamera/as4.png" width="720" alt="横屏高清模式warm滤镜效果"/>

### b.UVC摄像头直播，使用Android6.0系统，定制硬件与定制系统，外接摄像头
