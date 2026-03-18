package com.au.appcompose

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType.Companion.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.au.appcompose.composeutils.getScreenDpValue
import com.au.appcompose.composeutils.getNavigationBarHeight
import com.au.appcompose.composeutils.getStatusBarHeight
import com.au.appcompose.ui.theme.AndroidCompontsTheme
import com.au.appcompose.ui.theme.uiPaddingEdge
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d(TAG, "onCreate: ")

        setContent {
            val systemUiDp = getStatusBarHeight()
            val navUiDp = getNavigationBarHeight()
            val pair = getScreenDpValue()
            Log.d(TAG, "onCreate: pair ${pair.first} ${pair.second} systemUIHeightDp ${systemUiDp.value} navUiDp $navUiDp")

            AndroidCompontsTheme {
                Scaffold( modifier = Modifier.fillMaxSize()) { innerPaddingValues ->
                    MainUi(
                        name = "Android",
                        modifier = Modifier.padding(innerPaddingValues)
                    )
                }
            }
        }
    }

    @Composable
    fun RememberUpdatedStateUsage(onTimeout: () -> Unit) {
        val latestOnTimeout by rememberUpdatedState(newValue = onTimeout)

        val lastOnTimeout2 by remember { mutableStateOf(onTimeout) }.apply {
            value = onTimeout
        }

        LaunchedEffect(key1 = true) {
            delay(3000)
            lastOnTimeout2()
        }

        Text("3秒后执行回调（回调更新不重启协程）", Modifier.padding(16.dp))
    }

    @Composable
    fun RememberUpdatedStateCaller() {
        var message by remember { mutableStateOf("初始回调") }

        RememberUpdatedStateUsage(
            onTimeout = { message = "最新回调执行！" }
        )

        Text(message, Modifier.padding(16.dp))
    }

    @Composable
    fun MainUi(name: String, modifier: Modifier = Modifier) {
        var clicks by remember { mutableIntStateOf(0) }

        val mutableState = remember { mutableStateOf("dddx".toUri()) }
        var value2 by remember { mutableStateOf("dddx".toUri()) }
        val (value4, setValue) = remember { mutableStateOf("dddx".toUri()) }

        Column(
            modifier = Modifier
                .padding(horizontal = uiPaddingEdge) //先写padding就是margin效果。
                .statusBarsPadding() //直接写这个就能padding了，再也不用以前的transparent的办法了。
                .background(
                    color = Color(android.graphics.Color.GREEN),
                    shape = RoundedCornerShape(Dp(8f)) // 需要导入androidx.compose.foundation.shape包
                )
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {


            Text(
                text = "Hello1 $name!",
                modifier = modifier
            )
            Text("Hello2 $name!")
            HorizontalDivider()

            Button(onClick = {
                clicks++
            }, modifier = Modifier.width(Dp(250f))) {
                Text("I've been clicked $clicks times")
            }
        }
    }
}