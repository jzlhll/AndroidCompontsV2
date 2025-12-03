package com.au.module_android.ui.navigation

import android.os.Bundle

object FragmentNavigationConfig {
    private val checkList = mutableMapOf<String, (Bundle?) -> String?>()

    fun addEntryParamsChecker(sceneId:String, checker:(Bundle?) -> String?) {
        checkList[sceneId] = checker
    }

    fun checkEntryParams(sceneId:String, entryParams: Bundle?) : String? {
        return checkList[sceneId]?.invoke(entryParams)
    }
}