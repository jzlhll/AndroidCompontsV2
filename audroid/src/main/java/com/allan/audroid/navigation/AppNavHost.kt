package com.allan.audroid.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.allan.audroid.navigation.route.MyDroidAllRoute

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = AppRouteConstants.MY_DROID_ALL
    ) {
        composable(AppRouteConstants.MY_DROID_ALL) {
            MyDroidAllRoute()
        }
    }
}
