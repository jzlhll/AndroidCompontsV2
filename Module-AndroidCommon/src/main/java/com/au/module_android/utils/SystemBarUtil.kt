package com.au.module_android.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.window.layout.WindowMetricsCalculator
import com.au.module_android.DarkModeAndLocalesConst

//参考资料
//https://developer.android.google.cn/develop/ui/views/layout/edge-to-edge?hl=zh-cn
//https://developer.android.google.cn/develop/ui/views/layout/immersive?hl=zh-cn
//https://developer.android.google.cn/develop/ui/views/layout/insets/rounded-corners?hl=zh-cn
//https://developer.android.google.cn/develop/ui/views/layout/edge-to-edge-manually?hl=zh-cn

fun ComponentActivity.enableEdgeToEdgeFix(
    statusBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
) {
    enableEdgeToEdge(statusBarStyle, navigationBarStyle)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        //与edgeToEdge不同，它的源码是判断 nightMode == UiModeManager.MODE_NIGHT_AUTO
        window.isNavigationBarContrastEnforced = false
    }
}

/**
 * 检查当前界面是不是处于深色模式。返回true表示是深色模式。
 *
 */
fun isAppearanceLightForBars(context: Context) : Boolean{
    val detectIsDark = DarkModeAndLocalesConst.detectDarkMode(context)
    return !detectIsDark
}

/**
 * 修改状态栏文字颜色
 * isAppearanceLightXXX true就表示文字就是黑色的。false就表示文字就是白色的。所以要传入正确的值。
 */
fun Activity.changeBarsColor(statusBarTextDark: Boolean? = null,
                                 navBarTextDark: Boolean? = null,
                                 statusColor:Int?= null,
                                 navColor:Int?=null) {
    val light = isAppearanceLightForBars(this)
    window.changeBarsTextColor(statusBarTextDark ?: light, navBarTextDark ?: light, statusColor, navColor)
}

fun Window.changeBarsTextColor(statusBarTextDark: Boolean,
                                 navBarTextDark: Boolean,
                               statusColor:Int?= null,
                               navColor:Int?=null) {
    val controller = WindowInsetsControllerCompat(this, this.decorView)

    controller.isAppearanceLightStatusBars = statusBarTextDark
    controller.isAppearanceLightNavigationBars = navBarTextDark

    statusBarColor = statusColor ?: Color.TRANSPARENT  //android15一直是透明。所以你需要自己做padding，然后绘制。
    navigationBarColor = navColor ?: Color.TRANSPARENT //android15一直是透明。所以你需要自己做padding，然后绘制。
}

/**
 * 监听某个 View 的变化，获取到 WindowInsetsCompat
 */
fun View.applyWindowInsets(once:Boolean= false, insetsBlock: (
    insets: WindowInsetsCompat,
    statusBarsHeight: Int,
    navigationBarHeight: Int
) -> WindowInsetsCompat = {insets, _, _ -> insets}
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val ret = insetsBlock.invoke(
            insets,
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        )
        if (once) {
            ViewCompat.setOnApplyWindowInsetsListener(this, null)
        }
        ret
    }
}

/**
 * 透明状态栏
 *
 * 谨慎使用：activity和fragment已经通过基础框架默认限定实现
 *
 * 现在只需要在Dialog或者特殊临时切换调用
 * 在特殊地方使用[changeBarsTextColor]改变bars文字颜色即可。
 */
@Deprecated("框架已经使用了enableEdgeToEdge()实现了往下兼容，你不需要调用该函数。在特殊地方使用[changeBarsTextColor]改变bars文字颜色即可。")
fun Activity.immersive(
        statusBarTextDark: Boolean? = null,
        navBarTextDark: Boolean? = null,
        statusColor:Int?= null,
        navColor:Int?=null) {
    val light = isAppearanceLightForBars(this)
    window.immersive(statusBarTextDark ?: light, navBarTextDark ?: light, statusColor, navColor)
}

@Deprecated("框架已经使用了enableEdgeToEdge()实现了往下兼容，你不需要调用该函数。在特殊地方使用[changeBarsTextColor]改变bars文字颜色即可。")
fun Window.immersive(
        statusBarTextDark: Boolean,
        navBarTextDark: Boolean,
        statusColor:Int?= null,
        navColor:Int?=null
) {
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        //与edgeToEdge不同，它的源码是判断 nightMode == UiModeManager.MODE_NIGHT_AUTO
        this.isNavigationBarContrastEnforced = false
        this.isStatusBarContrastEnforced = false
    }
    WindowCompat.setDecorFitsSystemWindows(this, false)
    this.changeBarsTextColor(statusBarTextDark, navBarTextDark, statusColor, navColor)
}

@Deprecated("框架已经使用了enableEdgeToEdge()实现了往下兼容，你不需要调用该函数。在特殊地方使用[changeBarsTextColor]改变bars文字颜色即可。")
fun DialogFragment.immersive(statusBarTextDark: Boolean,
                                    navBarTextDark: Boolean,
                             statusColor:Int?= null,
                             navColor:Int?=null) {
    dialog?.window?.run {
        immersive(statusBarTextDark, navBarTextDark, statusColor, navColor)
    }
}

fun Activity.myHideSystemUI() {
    //设置布局延伸到刘海屏内，没此处设置会导致小米手机顶部导航栏显示黑色。
    val window = this.window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ignoreError {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            getWindow().setAttributes(lp)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        @Suppress
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }
}

//todo
fun Activity.myShowSystemUI() {
    //设置布局延伸到刘海屏内，没此处设置会导致小米手机顶部导航栏显示黑色。
    val window = this.window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ignoreError {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            getWindow().setAttributes(lp)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.show(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    } else {
        @Suppress("DEPRECATION")
        // 清除全屏标志
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE // 或直接设为 0
        // 可选：清除 Window 的全屏标志（如果之前通过 Window 设置过）
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

/**
 * 无需等待界面渲染成功，即在onCreate就可以调用，而且里面已经做了低版本兼容，感谢jetpack window库
 * 获取的就是整个屏幕的高度。包含了statusBar，navigationBar的高度一起。与wm size一致。
 * 这个方法100%可靠。虽然我们看api上描述说低版本是近似值，但是也是最接近最合理的值，不会是0的。
 */
fun Activity.getScreenFullSize() : Pair<Int, Int> {
    val m = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    //computeMaximumWindowMetrics(this) 区别就是多屏，类似华为推上去的效果。不分屏就是一样的。
    return m.bounds.width() to m.bounds.height()
}

/**
 * 必须在activity已经完全渲染之后，可以通过
 * 第一种办法：
 * ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
 *         val navHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
 *         val statusHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
 * 第二种办法：
 * View.post中调用本函数。
 *
 * 第三种办法：
 * 在onWindowFocusChanged中调用本函数。
 */
fun Activity.currentStatusBarAndNavBarHeight() : Pair<Int, Int>? {
    val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return null
    val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    val sta = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    return sta to nav
}

/**
 * 需要再onAttachToWindow调用
 */
fun View.currentStatusBarAndNavBarHeight() : Pair<Int, Int>? {
    return (this.context as? Activity)?.currentStatusBarAndNavBarHeight()
}