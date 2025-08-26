package com.au.audiorecordplayer.draws

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.au.audiorecordplayer.cam2.view.DrawFrameLayout
import com.au.module_android.ui.views.ViewFragment

class DrawTestFragment : ViewFragment() {
    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return DrawFrameLayout(inflater.context)
    }
}