# 导航落地

## 路由定义
```kotlin
object AppRoutes {
    const val HOME = "home"
    const val DETAIL = "detail/{id}"

    fun detail(id: String): String = "detail/$id"
}
```

## NavHost 骨架
```kotlin
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
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
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            DetailRoute(
                id = id,
                onBack = navController::popBackStack,
            )
        }
    }
}
```

## 规则
- 只传 `id`、筛选项、来源标记等轻量参数。
- 页面进入后再通过 `ViewModel` 拉详情，不在路由里塞对象或 JSON。
- 如果有启动页，`Splash` 放在 `startDestination`，跳走后清掉自身返回栈。
- 启动页、登录页跳转到主页面后，使用 `popUpTo(...){ inclusive = true }` 清理历史页。
- 同一路由重复点击会造成重复入栈时，增加 `launchSingleTop = true`。
- 如果有外部拉起或分享跳转，再看 [DeepLink 外部拉起](deep-link.md)。
