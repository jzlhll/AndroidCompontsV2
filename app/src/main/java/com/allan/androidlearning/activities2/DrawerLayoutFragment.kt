package com.allan.androidlearning.activities2

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.GravityCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import com.allan.androidlearning.databinding.ActivityDrawerLayoutBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_androidui.widget.CustomFontText
import com.google.android.material.navigation.NavigationView

@EntryFrgName()
class DrawerLayoutFragment : BindingFragment<ActivityDrawerLayoutBinding>() {
    private lateinit var drawer : DrawerLayout
    private lateinit var navigationView : NavigationView
    private lateinit var mask : View

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.PaddingNavigationBar
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        this.drawer = binding.root
        this.navigationView = binding.nav
        this.mask = binding.mask

        binding.content.openDrawer.onClick {
            drawer.openDrawer(GravityCompat.START)
        }
        binding.content.root.apply {
            for (i in 0..100) {
                val text = CustomFontText(context)
                text.textSize = 24f
                text.text = "this a item number is $i"
                addView(text)
            }
        }

        drawer.setScrimColor(Color.TRANSPARENT)
        drawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // slideOffset: 0（完全关闭）到 1（完全打开）
                // 计算主内容应该平移的距离：抽屉宽度 * 滑动比率
                val moveX = drawerView.width * slideOffset
                mask.alpha = slideOffset * 0.6f

                // 如果是左侧抽屉，主内容应向右平移
                if (drawerView.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    binding.content.root.translationX = moveX
                } else {
                    binding.content.root.translationX = -moveX
                }
            }

            override fun onDrawerOpened(drawerView: View) {
            }

            override fun onDrawerClosed(drawerView: View) {
                // 抽屉关闭后，将主内容复位
                binding.content.root.translationX = 0f
                // 清除遮罩
                mask.alpha = 0f
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        drawer.post {
            if (drawer.isAttachedToWindow) {
                val heights = requireActivity().currentStatusBarAndNavBarHeight()
                heights?.let { pair->
                    val statusBarHeight = pair.first
                    val navBarHeight = pair.second

                    binding.content.root.updatePadding(top = statusBarHeight, bottom = navBarHeight)
                    navigationView.updatePadding(top = statusBarHeight, bottom = navBarHeight)
                    navigationView.layoutParams = navigationView.layoutParams.apply {
                        width = drawer.width * 2 / 3
                    }
                }
            }
        }
    }
}