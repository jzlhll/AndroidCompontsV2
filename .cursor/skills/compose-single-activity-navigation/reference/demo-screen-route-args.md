# Demo：路由与参数

```kotlin
object AppRoutes {
    const val HOME = "home"
    const val DETAIL = "detail/{id}"

    fun detail(id: String): String = "detail/$id"
}

@Composable
fun HomeRoute(
    onOpenDetail: (String) -> Unit,
) {
    HomeScreen(
        onOpenDetail = { onOpenDetail("123") },
    )
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppRoutes.HOME) {
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
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            DetailRoute(
                id = id,
                onBack = navController::popBackStack,
            )
        }
    }
}
```

要点：
- 路由模板和构建函数放一起。
- 详情页只收 `id`，数据在页面内再加载。
