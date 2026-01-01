### Module-AuSimplePermission

<img src="project_logo.png" alt="logo" width="300"/>

**Android权限管理模块，提供简化的权限申请和系统交互API。**

- minSdk: 24   compileSdk: 36
- 基于AndroidX Activity Result API
- 支持Activity、Fragment、View作为上下文

#### 功能特性

- 单权限/多权限申请
- 媒体权限申请（兼容Android 14+）
- 系统相机拍照/录像
- 文档选择与内容获取
- 系统目录选择
- Activity跳转结果处理
- 辅助功能权限、悬浮窗权限、存储管理权限

#### 使用方式

##### 权限申请

```kotlin
 
```

##### 系统功能

```kotlin
 
```

##### Activity跳转

```kotlin

```

##### 特殊权限

```kotlin
// 辅助功能权限
gotoAccessibilityPermission()

// 悬浮窗权限
gotoFloatWindowPermission()

// 存储管理权限（Android 11+）
gotoMgrAll(context)
```
