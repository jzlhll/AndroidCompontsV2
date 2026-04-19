# 架构骨架

## 最小结构
```text
app/
├── MainActivity.kt
├── navigation/
│   ├── AppNavHost.kt
│   └── Routes.kt
├── ui/
│   ├── screens/
│   │   ├── splash/
│   │   │   ├── SplashRoute.kt
│   │   │   └── SplashScreen.kt
│   │   ├── home/
│   │   │   ├── HomeRoute.kt
│   │   │   └── HomeScreen.kt
│   │   └── detail/
│   │       ├── DetailRoute.kt
│   │       └── DetailScreen.kt
│   ├── components/
│   └── theme/
```

## 直接套用的职责
- `MainActivity`：只做 `setContent`、系统入口、权限桥接。
- `AppNavHost`：集中声明导航图。
- `Route`：负责取参数、连接状态、处理导航和事件。
- `Screen`：纯渲染，只接收 `UiState` 与回调。
- 业务层目录按现有项目决定；本 skill 不强制新建 `domain/`、`data/`。

## 启动链路
- 默认入口是 `MainActivity -> AppNavHost -> SplashRoute -> Home/Login`。
- `Splash` 只是普通页面，放在 `NavHost` 的起始路由。
- `Splash` 的具体写法、跳转清栈和冷启动主题，直接看 [Splash 启动页](splash-screen.md)。

## 最小页面分层
```kotlin
@Composable
fun DetailRoute(
    onBack: () -> Unit,
) {
    val uiState = /* 按项目现有方式收集状态 */

    DetailScreen(
        uiState = uiState,
        onBack = onBack,
    )
}
```

## 落地约束
- 单 `Activity`，不新增业务 `Fragment`。
- 页面参数只传最小必要值。
- 传统 `View/XML` 只作兼容层，不扩散成新默认方案。
