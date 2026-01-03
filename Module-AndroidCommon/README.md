# Module-AndroidCommon
<img src="project_logo.png" alt="logo" width="300"/>

Android 通用基础模块，提供应用开发所需的基础功能和工具类。

## 功能模块

### 核心功能
- **应用初始化**：`CommonInitApplication` 提供基础应用初始化，支持崩溃处理、暗色模式、屏幕适配
- **全局管理**：`Globals` 提供全局协程作用域、Handler、Activity栈管理
- **日志系统**：支持控制台日志和文件日志，支持大文本日志输出

### 架构组件
- **LiveData**：`NoStickLiveData` 去除粘性事件，支持安全更新值
- **Flow架构**：基于 Action-Dispatcher 的 MVVM 架构实现
- **责任链模式**：提供可扩展的责任链模板

### 工具类
- **通用工具**：文件处理、网络、UI、反射、集合、字符串等
- **媒体工具**：媒体文件选择、Uri解析、视频时长获取、文件分享
- **点击防抖**：防止快速重复点击
- **屏幕适配**：今日头条屏幕适配方案
- **Glide封装**：图片加载工具类

### 缓存系统
- **DataStore缓存**：支持 Boolean、Int、Long、Float、Double、String、ByteArray、自定义类型
- **SharedPreferences缓存**：支持 Boolean、Int、Long、Float、String、StringSet

### 其他功能
- **崩溃处理**：全局异常捕获和崩溃页面
- **跨Activity通知**：Activity间通信机制
- **生命周期监听**：全局Activity生命周期回调
- **后台任务**：协程任务执行器
- **ViewModel共享**：跨Fragment/Activity的ViewModel共享管理

## 依赖库
- AndroidX Core、AppCompat、RecyclerView
- Lifecycle (ViewModel、LiveData、Process)
- Material Design
- OkHttp
- Glide
- DataStore Preferences
- AndroidX Startup

## 使用方式
在 Application 中继承 `CommonInitApplication` 并调用 `init()` 方法即可完成基础初始化。
