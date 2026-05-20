# DeepLink 外部拉起

## 直接按这套做
- 外部分享、通知、浏览器拉起统一走 `Navigation Compose` 的 `deepLinks`。
- `MainActivity` 仍然是唯一入口，不新增跳板 `Activity`。
- 目标页只接最小参数，如 `id`；页面内容进入后再加载。
- 如果要求“外部拉起目标页后，返回先回主页”，优先把目标页放进以 `Home` 为起点的子图。

## 清单
```xml
<activity
    android:name=".MainActivity"
    android:exported="true">

    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:scheme="https"
            android:host="share.yourapp.com" />
        <data
            android:scheme="yourapp"
            android:host="share" />
    </intent-filter>
</activity>
```

## 返回先回主页的推荐结构
```kotlin
object AppRoutes {
    const val SPLASH = "splash"
    const val MAIN = "main"
    const val HOME = "home"
    const val DETAIL = "detail/{id}"

    fun detail(id: String): String = "detail/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH,
    ) {
        composable(AppRoutes.SPLASH) {
            SplashRoute(
                onOpenMain = {
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        navigation(
            startDestination = AppRoutes.HOME,
            route = AppRoutes.MAIN,
        ) {
            composable(AppRoutes.HOME) {
                HomeRoute(
                    onOpenDetail = { id ->
                        navController.navigate(AppRoutes.detail(id))
                    },
                )
            }

            composable(
                route = AppRoutes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "yourapp://share/detail/{id}" },
                    navDeepLink { uriPattern = "https://share.yourapp.com/detail/{id}" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                DetailRoute(
                    id = id,
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}
```

## 为什么这样设计
- DeepLink 命中 `Detail` 时，返回栈会先回到所在子图的起点，也就是 `Home`。
- 正常冷启动仍然走 `Splash -> Main -> Home`。
- 外部拉起不会额外开页面，也不会破坏单 `Activity` 架构。

## 平铺图的兜底规则
- 如果当前图是平铺的（所有页面都直接并列挂在同一个 `NavHost` 下），没有 `Home` 子图，又要求外部拉起后返回主页，就不要只依赖默认返回栈。
- 需要在接管外部跳转时，显式补主页再进入目标页。

## 不要这样做
- 不要为 Deeplink 或分享跳转单独再开一个 `Activity`。
- 不要把整个对象直接塞进 Deeplink 参数。
- 不要让 Deeplink 直接把用户带回 `Splash`。
