package com.au.module_android.ui.navigation

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.au.module_android.ui.FragmentNavigationActivity
import com.au.module_android.ui.base.AbsFragment
import com.au.module_android.utils.asOrNull

/**
 * 在同一个场景下，切换不同的页面。
 *
 * 内部会保存当前页面的参数，下次进入的时候会自动恢复。
 *
 * 参数传递是通过 viewModel 传递的。因此，所有的数据都走 viewModel liveData 的监听
 */
fun INavigationPage.navigateTo(pageId:String, extraData:Map<String, Any?>? = null) {
    if(extraData != null) viewModel.updatePageData(pageId, extraData)

    val activity = (this as Fragment).activity as FragmentNavigationActivity
    val scene = activity.fragmentNavigationScene
    scene.list.find { it.pageId == pageId }?.let { page->
        activity.navigateTo(page)
    }
}

/**
 * 清理当前页面。返回到上一个页面。
 * 如果 clearTo为空就返回一层；如果 clearTo不为空，就返回到指定的 pageId 页面。
 */
fun INavigationPage.navigateBack(clearTo:String? = null) {

}

fun INavigationPage.navigateBack(clearList:List<String>) {

}

/**
 * 跳转到某个场景
 */
fun Activity.navigateScene(scene: FragmentNavigationScene) {
    if (this is FragmentNavigationActivity && this.fragmentNavigationScene.sceneId == scene.sceneId) {

    }
}

fun INavigationPage.findScene() : FragmentNavigationScene? {
    return ((this as Fragment).activity as? FragmentNavigationActivity)?.viewModel?.defaultScene
}