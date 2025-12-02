package com.au.module_android.ui.navigation

import android.os.Bundle

data class FragmentNavigationScene(
    val sceneId:String,
    val list: List<FragmentNavigationPage>,
    val startPageId: String,
    val entryParams: Bundle? = null,
)