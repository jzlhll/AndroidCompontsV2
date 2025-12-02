package com.au.module_android.ui.navigation

import android.app.Activity
import android.os.Bundle
import com.au.module_android.ui.FragmentNavigationActivity
import com.au.module_android.ui.base.AbsFragment

/**
 * 在同一个场景下，切换不同的页面
 */
fun AbsFragment.navigateTo(pageId:String, params: Bundle? = null) {
    val activity = requireActivity() as FragmentNavigationActivity
    val scene = activity.fragmentNavigationScene
    scene.list.find { it.pageId == pageId }?.let { page->
        activity.navigateTo(page)
    }
}

fun AbsFragment.navigateBack() {

}

/**
 * 跳转到某个场景
 */
fun Activity.navigateScene(scene: FragmentNavigationScene) {
    if (this is FragmentNavigationActivity && this.fragmentNavigationScene.sceneId == scene.sceneId) {

    }
}

fun AbsFragment.findScene(): FragmentNavigationScene {
    val activity = requireActivity() as FragmentNavigationActivity
    return activity.fragmentNavigationScene
}