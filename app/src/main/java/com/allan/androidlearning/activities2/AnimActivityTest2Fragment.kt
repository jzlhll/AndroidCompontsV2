package com.allan.androidlearning.activities2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.au.module_android.click.onClick
import com.au.module_android.utils.dp
import com.au.module_androidui.ui.views.ViewFragment

class AnimActivityTest2Fragment : ViewFragment() {
    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(Button(requireContext()).also {
                it.text = "finish"
                it.textSize = 24f
                it.width = 200.dp
                it.height = 54.dp
                it.onClick {
                    requireActivity().finish()
                }
            })
        }
    }
}