package com.allan.androidlearning.androidui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.allan.androidlearning.activities.DarkModeAndLocalesFragment
import com.allan.androidlearning.R
import com.allan.androidlearning.databinding.FragmentAndroidUi1Binding
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.bindings.BindingFragment

class AndroidUi1Fragment : BindingFragment<FragmentAndroidUi1Binding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.settingsBtn.onClick {
            FragmentShellActivity.start(requireContext(), DarkModeAndLocalesFragment::class.java)
        }
        buttons()
        blocks()
        darkModeAndLocalesTest()
    }

    fun buttons() {
        //必须设置点击事件才有按压色
        binding.androidUi1Primary.onClick {
        }
        binding.androidUi1Secondary.onClick {
        }
    }

    fun blocks() {
        binding.androidUiSwitchBlock1.initValue(true)
        binding.androidUiSwitchBlock2.initValue(false)
        binding.androidUiSwitchLayout.initValue(false, false)
        binding.androidUiSwitchBtnLayout.initValue(false)
    }

    @SuppressLint("DefaultLocale")
    private fun Color.toRgbStr() :String {
        val r = String.format("%d", (red() * 255).toInt())
        val g = String.format("%d", (green() * 255).toInt())
        val b = String.format("%d", (blue() * 255).toInt())
        return "($r, $g, $b)"
    }

    fun darkModeAndLocalesTest() {
        val activityContext = requireContext()
        val languageContext = ContextCompat.getContextForLanguage(Globals.app)
        // Module-AndroidColor 相关的颜色上下文对比示例已停用。
        // val str1 = getString(R.string.app_name)
        // val str2 = Globals.app.getString(R.string.app_name)
        // val str3 = Globals.getString(R.string.app_name)
        //
        // val colorRes = com.au.module_androidcolor.R.color.color_text_normal
        // val color1 = activityContext.getColor(colorRes)
        // val color2 = Globals.app.getColor(colorRes)
        // val color3 = languageContext.getColor(colorRes)
        //
        // binding.text.text = getString(R.string.resource_context_demo,
        //     str1, Color.valueOf(color1).toRgbStr(),
        //     str2, Color.valueOf(color2).toRgbStr(),
        //     str3, Color.valueOf(color3).toRgbStr())
        binding.image1.setImageDrawable(activityContext.getDrawable(R.drawable.test_night_image))
        binding.image2.setImageDrawable(Globals.app.getDrawable(R.drawable.test_night_image))
        binding.image3.setImageDrawable(languageContext.getDrawable(R.drawable.test_night_image))
        // binding.testColor1.setTextColor(color1)
        // binding.testColor2.setTextColor(color2)
        // binding.testColor3.setTextColor(color3)
    }
}
