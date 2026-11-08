# Pulse Wear

Pulse Wear 是一个 Kotlin Android 应用，用模拟穿戴设备演示扫描、连接、查看设备信息、同步心率与步数以及本地历史。界面沿用 Restore 的浅灰背景、圆角设备卡、大数字读数、趋势曲线和底部导航。

## 网页演示

**[在线体验 Pulse Wear · 手机视图](https://jack01930.github.io/PulseWear/?view=app)**

电脑浏览器会以手机竖屏比例显示演示。可以扫描并连接模拟手环、同步心率与步数、重试同步超时，并查看保存在当前浏览器的历史。

网页源码在 `docs/`。本地预览可在项目根目录运行 `python -m http.server 8080 --directory docs`，再打开 `http://localhost:8080/?view=app`。

## 运行

- 用 Android Studio 打开项目根目录，使用 IDE 自带 JDK 或 JDK 17 及以上，安装 Android SDK 35。[AGP 8.11 工具链要求](https://developer.android.com/build/releases/agp-8-11-0-release-notes)
- 在 Device Manager 选用 Pixel 4。Android 10（API 29）x86_64 镜像可运行此项目。设备内存可设为 1536 MB，并关闭两路虚拟摄像头。
- 运行 `app`。也可在已配置 `JAVA_HOME` 和 `ANDROID_HOME` 的 PowerShell 中运行 `.\gradlew.bat :app:assembleDebug`，APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 演示路径

1. 在「设备」点击「扫描设备」，找到 Pulse Band S1 后点击「连接手环」。连接后可查看型号、固件、电量、信号和最近同步时间。
2. 点击「查看健康概览」，再点击「同步健康数据」。同步成功后可查看心率、心率趋势和步数。
3. 在「历史」查看本地记录及详情。重启 App 后仍可查看已保存记录。
4. 回到「设备」，开启「模拟下次同步超时」。在「健康」发起同步，等到超时提示，再点击「重试同步」。重试成功后，历史中仍只有同一设备当日的一条记录。

## 实现

- `WearableClient` 定义扫描、连接、通道准备和健康数据请求。`MockWearableClient` 在本地模拟发现设备、服务准备和订阅回包，超时演示会丢弃一次模拟回包。
- `HealthHistory` 用 Android 私有 SharedPreferences 保存每日快照，以设备 ID 和日期为键。同日成功同步会更新该记录，超时保留既有记录。
- 所有设备、信号和健康读数均为演示数据，运行无需蓝牙、网络权限或账号。

## 交付打包

先在项目根目录完成真实 Git 提交，然后在 PowerShell 运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\package-submission.ps1`。输出 `delivery/PulseWear-source.zip`，包含 `.git`、源码、Gradle Wrapper、README 和 AI 使用说明。另行提交演示录屏及 AI 对话记录。
