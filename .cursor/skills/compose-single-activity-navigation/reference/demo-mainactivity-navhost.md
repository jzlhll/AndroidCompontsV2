# Demo：MainActivity 与 NavHost

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
    ) {
        composable(AppRoutes.HOME) {
            HomeRoute(
                onOpenDetail = { id -> navController.navigate(AppRoutes.detail(id)) },
            )
        }
        composable(AppRoutes.DETAIL) {
            DetailRoute(
                onBack = navController::popBackStack,
            )
        }
    }
}
```

要点：
- `Activity` 只挂根主题和 `NavHost`。
- 导航控制器只在宿主导航层创建。
