package com.au.module_androidui.ui.navigation

import androidx.fragment.app.Fragment
import com.au.module_androidui.ui.FragmentNavigationActivity

/**
 * 在同一个场景下，切换不同的页面。
 *
 * 内部会保存当前页面的参数，下次进入的时候会自动恢复。
 *
 * 参数传递是通过 viewModel 传递的。因此，所有的数据都走 viewModel liveData 的监听
 */
fun INavigationPage.navigateTo(pageId:String, extraData:Map<String, Any?>? = null, clearCurrent: Boolean = false) {
    if(extraData != null) viewModel.updatePageData(pageId, extraData)
    val activity = (this as Fragment).activity as FragmentNavigationActivity
    activity.navigateTo(pageId, clearCurrent)
}

/**
 * 清理当前页面。返回到上一个页面。
 * 如果 clearTo为空就返回一层；如果 clearTo不为空，就返回到指定的 pageId 页面。
 */
fun INavigationPage.navigateBack(clearTo:String? = null, extraData:Map<String, Any?>? = null) {
    val activity = (this as Fragment).activity as FragmentNavigationActivity
    activity.navigateBack(clearTo, extraData)
}

fun INavigationPage.finishScene() {
    (this as Fragment).requireActivity().finishAfterTransition() //完成登录整体 scene 销毁
}

fun INavigationPage.findScene() : FragmentNavigationScene? {
    return ((this as Fragment).activity as? FragmentNavigationActivity)?.viewModel?.scene
}