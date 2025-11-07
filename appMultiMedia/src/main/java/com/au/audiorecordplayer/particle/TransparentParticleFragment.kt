package com.au.audiorecordplayer.particle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.au.module_android.ui.views.ViewFragment

class TransparentParticleFragment : ViewFragment() {

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FrameLayout(inflater.context).apply {
            //如果大于等于13才显示
            if (true) {
                addView(ScreenEffectView2(inflater.context).also {
                    it.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            } else {
                addView(ScreenEffectViewLower(inflater.context).also {
                    it.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            }
        }
    }

    override fun isPaddingStatusBar(): Boolean {
        return false
    }

    override fun isPaddingNavBar(): Boolean {
        return false
    }
}