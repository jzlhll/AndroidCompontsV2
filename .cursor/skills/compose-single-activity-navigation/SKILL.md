---
name: compose-single-activity-navigation
description: 实现 Android 单Activity Compose 架构，使用 Navigation Compose 管理页面且不新增 Fragment。用于新建单Activity Compose 页面、给现有 Compose 项目加导航、或把 Fragment 页面迁到 Compose 时。
---

# 单Activity Compose 落地

## 适用场景
- 新建单 `Activity` 的 Compose 页面架构。
- 给已有 Compose 项目补 `Navigation Compose`。
- 把 `Fragment` 页面迁到 Compose。

## 直接按这套做
1. 只保留一个宿主 `ComponentActivity`。
2. 所有页面都实现成 `@Composable`，不要新增业务 `Fragment`。
3. 用一个 `AppNavHost` 管理全部路由。
4. 路由集中定义，不在页面里散落硬编码字符串。
5. 页面拆成 `Route` + `Screen`：
   - `Route`：取参数、连接状态、处理导航。
   - `Screen`：纯渲染，只收状态和回调。
6. 路由参数只传最小必要值，如 `id`；不要传对象、JSON、大串文本。
7. 如果必须复用旧 `View/XML`，遵循 `compose-view-interop`。

## 代码约束
- `Activity` 只做 `setContent`、挂主题、挂 `NavHost`。
- `NavController` 只放在导航层或 `Route`，不要一路下传到纯 UI 组件。
- 带参页面同时提供路由模板和构建函数。
- 启动页、登录页这类一次性页面跳走后，要清返回栈。
- 页面状态遵循项目现有状态管理方案；本 skill 不额外强绑状态框架。

## Splash 这样做
- `Splash` 也是普通 Compose 页面，不新增 `Activity`，不新增 `Fragment`。
- 把 `Splash` 放到 `NavHost` 的 `startDestination`。
- 启动校验、初始化、登录判断放在 `Route` / `ViewModel`，不要堆进 `Activity`。
- `Splash` 跳转后必须 `popUpTo(splash) { inclusive = true }`，确保返回键不能退回启动页。
- 需要最小展示时长时，用页面内副作用处理，不要写阻塞逻辑。
- 冷启动白屏必须靠原生 `Theme.SplashScreen` + `installSplashScreen()` + `postSplashScreenTheme` 处理。
- 追求与系统 Splash **完全一致**时，优先在 `installSplashScreen()` 后使用 `setKeepOnScreenCondition` 延长系统 Splash，不要再手写 Compose 仿制页。
- 如果有业务等待阶段，必须补一个 Compose `SplashScreen`，并与原生 Splash **视觉完全一致**：
  - 背景色与 `windowSplashScreenBackground` 用同一资源（需支持 `values-night`），禁止硬编码 `Color.White`。
  - 图标与 `windowSplashScreenAnimatedIcon` 用同一资源，居中显示，尺寸按真机对齐微调。
  - 顶层使用 `fillMaxSize()`，保证全屏过渡无跳变。

## DeepLink 这样做
- 外部拉起、分享跳转、通知跳转统一走 `Navigation Compose` 的 `deepLinks`。
- `MainActivity` 仍然是唯一入口，不为分享或 Deeplink 单独加 `Activity`。
- 可被外部拉起的页面，优先放进以 `Home` 为起点的子图里，这样返回键先回主页，不直接退出 App。
- 如果当前导航还是平铺结构，又要求“外部拉起后返回主页”，就不要只依赖默认返回栈，要显式补主页再进目标页。
- DeepLink 只负责把最小参数带进目标页；页面数据仍在进入页面后加载。

## 实现顺序
1. 先确认是否已有 `MainActivity`、主题和 `NavHost`。
2. 如果没有，先补宿主 `Activity`、`AppNavHost`、统一路由定义。
3. 如果是新增页面，先建 `Route` / `Screen`，再注册到 `NavHost`。
4. 如果有路由参数，先定义模板和构建函数，再写页面。
5. 如果必须复用旧页面控件，再按 `compose-view-interop` 接入。

## 不要这样做
- 不要新增第二个业务 `Activity` 或 `Fragment`。
- 不要把业务逻辑堆进 `Activity`。
- 不要把 `NavController` 塞进所有子组件。
- 不要把整个对象直接放进导航参数。
- 不要把整页 XML 当默认方案继续开发。

## 参考
- [架构与职责](reference/architecture.md)
- [导航模式](reference/navigation-patterns.md)
- [Splash 启动页](reference/splash-screen.md)
- [DeepLink 外部拉起](reference/deep-link.md)
- [Demo：MainActivity 与 NavHost](reference/demo-mainactivity-navhost.md)
- [Demo：路由与参数](reference/demo-screen-route-args.md)
