package com.au.module_android.ui.views

import android.view.View

/**
 * @author Allan
 */
sealed class YourToolbarInfo() {
    data class Defaults(val title:String?) : YourToolbarInfo()
    data class Yours<VB>(val layoutId:Int, val viewBindingInitializer: (View) -> VB) : YourToolbarInfo()
}