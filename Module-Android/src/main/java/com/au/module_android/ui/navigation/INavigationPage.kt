package com.au.module_android.ui.navigation

interface INavigationPage {
    fun isStartPage():Boolean
    fun pageId():String
    fun loadAndObserverData()
    val viewModel : FragmentNavigationViewModel
}