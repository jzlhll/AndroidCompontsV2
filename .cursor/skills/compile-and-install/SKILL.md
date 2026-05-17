---
name: build-install
description: 编译与安装流程：区分首次编译和后续编译；同一会话首次安装前执行 adb devices 并记住目标 serial，后续安装复用该 serial；永远使用 android run --device 指定设备安装。用户明确要求编译或安装到手机时使用。
---

# 编译与安装流程

## 1) 编译命令规范

编译始终请求非沙盒权限执行，复用用户本机 `~/.gradle`、Gradle Daemon 与项目增量编译缓存。统一在项目根目录执行：

```bash
bash ./gradlew :XXX:assembleDebug
```


## 2) 安装前设备检查与会话设备记录
同一会话的第一次安装前，先检查已连接设备：
```bash
adb devices
```
- 0 台设备：提示用户先连接手机并开启 USB 调试。
- 1 台设备：记录这台设备的 serial，作为本会话后续安装目标。
- 多台设备：必须先让用户确认目标设备 serial，记录后再执行安装。
- 本会话后续安装：不再重复让用户选择设备，直接复用已记录的 serial；只有安装失败或用户明确要求切换设备时，才重新执行 `adb devices` 并更新 serial。

## 3) 安装命令规范

永远使用 `--device` 指定设备安装，即使当前只连接了一台设备：
```bash
android run --device="<serial>" --apks="/绝对路径/XXX/build/outputs/apk/debug/XXX-debug.apk"
```

说明：
- APK 路径优先从 `XXX/build/outputs/apk/debug/output-metadata.json` 确认。
