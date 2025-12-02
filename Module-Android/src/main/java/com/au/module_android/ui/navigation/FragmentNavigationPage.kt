package com.au.module_android.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment

data class FragmentNavigationPage(
    val pageId:String,
    val fragmentClass:Class<out Fragment>,
    val params: Bundle? = null,
    val isStartPage: Boolean = false
)