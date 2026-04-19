# Splash 启动页

架构位置、目录分层和 `Route/Screen` 约定先看 [架构骨架](architecture.md)。

## 直接按这套做
- `Splash` 是普通 `Composable` 页面。
- `NavHost` 的 `startDestination` 指向 `Splash`。
- `Splash` 只负责展示和触发启动流程；初始化、登录校验放到 `Route` / `ViewModel`。
- 跳转后必须清掉 `Splash` 自身返回栈。
- 冷启动背景用原生主题处理。

## 路由
```kotlin
object AppRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LOGIN = "login"
}
```

## NavHost
```kotlin
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH,
    ) {
        composable(AppRoutes.SPLASH) {
            SplashRoute(
                onOpenHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                },
                onOpenLogin = {
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.HOME) { HomeRoute() }
        composable(AppRoutes.LOGIN) { LoginRoute() }
    }
}
```

## 页面骨架
```kotlin
@Composable
fun SplashRoute(
    onOpenHome: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SplashViewModel,
) {
    LaunchedEffect(Unit) {
        val isLogin = viewModel.checkLoginStatus()
        delay(1500)
        if (isLogin) onOpenHome() else onOpenLogin()
    }

    SplashScreen()
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Splash")
    }
}
```

## 主题必做
```xml
<style name="Theme.MyApp">
    <item name="android:windowBackground">@drawable/splash_bg</item>
</style>
```

## 不要这样做
- 不要给 `Splash` 单独再开一个 `Activity`。
- 不要跳转后保留 `Splash` 在返回栈里。
- 不要在 `Splash` 里做阻塞式耗时操作。
