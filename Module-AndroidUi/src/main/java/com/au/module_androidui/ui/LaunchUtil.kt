package com.au.module_androidui.ui

import android.content.Context
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import com.au.module_androidui.ui.FragmentShellActivity.Companion.startKoin

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
                        exitAnim:Int? = null) {
    startKoin(context, FragmentShellActivity::class.java, this, arguments, optionsCompat, enterAnim, exitAnim)
}