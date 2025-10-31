package com.au.module_androidui.popup

import android.graphics.Point
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.au.module_android.utils.asOrNull
import com.au.module_android.R

/**
 *
 * 调用的时候，需要注意width和height的赋值问题
 * 显示在按钮的上方或者下方，根据是否显示可以自动更新位置
 *  isGravityEnd : true ,弹窗与view的右边缘对齐，可以通过设置xoff来横向偏移
 *  isGravityEnd : false ,弹窗与view的左边缘对齐，可以通过设置xoff来横向偏移
 *  isDropViewBottom:是否强制显示在下方
 *                 null：根据按钮位置自动判断（高于屏幕一半，显示按钮下方，否则上方），
 *                 true：总是显示按钮下方
 *                 false:总是显示按钮上方
 */
fun PopupWindow.showAtView(
    anchor: View,
    isGravityEnd: Boolean,
    xoff: Int = 0,
    yoff: Int = 0,
    isDropViewBottom: Boolean? = null,
    @StyleRes animStyle: Int? = null
) {
    val anchorLocation = intArrayOf(0, 0)
    val screenPoint = Point()
    anchor.context.asOrNull<AppCompatActivity>()?.window?.decorView?.let {
        //适配分屏应用
        screenPoint.x = it.width
        screenPoint.y = it.height
    }
    anchor.getLocationInWindow(anchorLocation)
    val isShowBottom = isDropViewBottom ?: (anchorLocation[1] < screenPoint.y / 2)
    if (isShowBottom) {
        if (isGravityEnd) {
            animationStyle = animStyle ?: R.style.PopupWindowScaleEndTopIn
            val x = screenPoint.x - anchorLocation[0] - anchor.width + xoff
            val y = anchorLocation[1] + anchor.height + yoff
            if (isShowing) {
                update(x, y, width, height)
            } else {
                showAtLocation(anchor, Gravity.TOP or Gravity.END, x, y)
            }
        } else {
            animationStyle = animStyle ?: R.style.PopupWindowScaleStartTopIn
            val x = anchorLocation[0] + xoff
            val y = anchorLocation[1] + anchor.height + yoff
            if (isShowing) {
                update(x, y, width, height)
            } else {
                showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
            }
        }
    } else {
        if (isGravityEnd) {
            animationStyle = animStyle ?: R.style.PopupWindowScaleEndTopInToTop
            val x = screenPoint.x - anchorLocation[0] - anchor.width + xoff
            val y = screenPoint.y - anchorLocation[1] + yoff
            if (isShowing) {
                update(x, y, width, height)
            } else {
                showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, x, y)
            }
        } else {
            animationStyle = animStyle ?: R.style.PopupWindowScaleStartTopInToTop
            val x = anchorLocation[0] + xoff
            val y = screenPoint.y - anchorLocation[1] + yoff
            if (isShowing) {
                update(x, y, width, height)
            } else {
                showAtLocation(anchor, Gravity.BOTTOM or Gravity.START, x, y)
            }
        }
    }
}