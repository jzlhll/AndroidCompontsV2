package com.au.module_android.ui.navigation

import android.os.Bundle

object FragmentNavigationConfig {
    private val scenes = mutableMapOf<String, FragmentNavigationScene>()

    private val entryParamsCheckers = mutableMapOf<String, (Bundle?) -> String?>()

    /**
     * 在 init 中调用添加 检查器
     * @param sceneId 场景 id
     * @param checker 检查器 检查参数，返回 pageId，为空就走默认的 startPageId
     */
    fun addEntryParamsChecker(sceneId:String, checker:(entryParams:Bundle?) -> String?) {
        entryParamsCheckers[sceneId] = checker
    }

    /**
     * 检查参数，返回 pageId，为空就走默认的 startPageId
     */
    fun checkEntryParams(sceneId:String, entryParams: Bundle?) : String? {
        return entryParamsCheckers[sceneId]?.invoke(entryParams)
    }

    fun addScene(scene: FragmentNavigationScene) {
        scenes[scene.sceneId] = scene
    }

    fun getScene(sceneId: String) : FragmentNavigationScene {
        return scenes[sceneId]!!
    }
}