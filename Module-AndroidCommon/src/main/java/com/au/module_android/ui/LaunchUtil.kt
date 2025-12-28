package com.au.module_android.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.core.app.ActivityOptionsCompat
import com.au.module_android.permissions.activity.ActivityForResult
import com.au.module_android.ui.FragmentShellActivity.Companion.startRoot

/**
 *
 * 使用koin做Fragment名
 * 把一个Fragment放到本Activity当做唯一的界面。
 *
 * @param context Context
 * @param arguments 用来透传给Fragment
 * @param optionsCompat 是startActivity的参数
 * @param enterAnim 与android标准不同的是，这里给出的anim都是限定即将打开的activity进入时候的动画
 * @param exitAnim  与android标准不同的是，这里给出的anim都是限定即将打开的activity退出时候的动画
 */
fun  String.namedLaunch(context: Context,
                        arguments: Bundle? = null,
                        optionsCompat: ActivityOptionsCompat? = null,
                        enterAnim:Int? = null,
                        exitAnim:Int? = null,
                        activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
    startRoot(context, FragmentShellActivity::class.java, this,
        null, arguments, optionsCompat, enterAnim, exitAnim, activityResultCallback)
}

/**
 * 使用koin做Fragment名
 * 把一个Fragment放到本Activity当做唯一的界面。
 *
 * @param context Context
 * @param activityResult 如果传入了非空对象，则会通过它启动，会携带返回；否则就是默认启动。
 * @param arguments 用来透传给Fragment
 * @param optionsCompat 是startActivity的参数
 * @param enterAnim 与android标准不同的是，这里给出的anim都是限定即将打开的activity进入时候的动画
 * @param exitAnim  与android标准不同的是，这里给出的anim都是限定即将打开的activity退出时候的动画
 */
fun String.namedLaunchResult(context: Context,
                   activityResult:ActivityForResult,
                   arguments: Bundle? = null,
                   optionsCompat: ActivityOptionsCompat? = null,
                   enterAnim:Int? = null,
                   exitAnim:Int? = null,
                   activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
    startRoot(context, FragmentShellActivity::class.java, this, activityResult, arguments, optionsCompat, enterAnim, exitAnim, activityResultCallback)
}