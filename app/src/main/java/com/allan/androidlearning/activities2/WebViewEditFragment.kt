package com.allan.androidlearning.activities2

import android.os.Bundle
import com.allan.androidlearning.databinding.ActivityJsHtmlBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_androidui.ui.bindings.BindingFragment

/**
 * @author allan
 * @date :2024/12/5 9:40
 * @description:
 */
@EntryFrgName()
class WebViewEditFragment : BindingFragment<ActivityJsHtmlBinding>() {

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.webView.loadUrl("file:///android_asset/webEdit.html")
    }
}