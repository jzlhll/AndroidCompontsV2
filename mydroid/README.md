# mydroid

## 启动入口与 Splash 图标

桌面、下载通知和外部分享使用以下入口。

| 入口 | 启动路径 |
| --- | --- |
| 点击桌面图标 | `MyDroidActivity` |
| 点击下载通知 | `MyDroidActivity` |
| Gallery 等应用通过 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` 分享文件 | `ShareImportActivity` → `MyDroidActivity` → `SendListSelectorFragment` |

`MyDroidActivity` 和 `ShareImportActivity` 均使用 `SplashTheme`，在 `super.onCreate()` 前调用 `installSplashScreen()`，再由 `postSplashScreenTheme` 切换到 `CustomModuleTheme`。

### Android 13+ 的图标显示偏好

系统会根据启动来源、调用方传入的 Splash 样式和主题配置决定启动窗口的显示方式。同样是冷启动，桌面启动可以显示居中的图标，外部分享启动却可能选择纯色 Splash，只显示背景。这不代表入口 Activity 没有执行，也不能仅据此判断为应用布局白屏。

Android 13（API 33）起，使用 `android:windowSplashScreenBehavior` 声明图标显示偏好。mydroid 在 `values-v33` 的 [themes.xml](src/main/res/values-v33/themes.xml) 中配置：

```xml
<style name="SplashTheme" parent="AbsStyleSplashTheme">
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_playstore</item>
    <item name="android:windowSplashScreenBehavior">icon_preferred</item>
</style>
```

`icon_preferred` 用于优先显示 Splash 图标，避免默认样式选择导致的纯色启动屏；调用方显式指定 `SPLASH_SCREEN_STYLE_EMPTY` 时仍可隐藏图标。版本限定目录中的 `SplashTheme` 保留相同的父主题与图标配置；Android 12L 及以下继续使用 `values` 的 [themes.xml](src/main/res/values/themes.xml)，不使用 API 33 的属性。

外部分享直接启动 `ShareImportActivity`，导入期间由 `activity_import.xml` 显示进度。

### 排查与设备验收

Android 的 `StartingWindowInfo.TYPE_PARAMETER_USE_SOLID_COLOR_SPLASH_SCREEN` 对应 `0x20`。系统日志中的 `typeParameter` 包含该位（例如 `0xab & 0x20 != 0`）时，表示带有纯色 Splash 标记；最终样式还需结合图标偏好等参数判断。排查时应检查实际入口 Activity 的 Manifest 主题及对应版本资源。

设备验收应分别覆盖桌面冷启动、Gallery 单图与多图分享冷启动，以及应用已启动时再次分享；同时检查浅色与深色模式。Android 13+ 冷启动应显示居中的 Splash 图标，分享导入后仍应进入 `SendListSelectorFragment`，重复分享继续由 `onNewIntent()` 处理。

参考：[Android Splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen#theme)、[AOSP StartingWindowInfo.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/window/StartingWindowInfo.java)。
