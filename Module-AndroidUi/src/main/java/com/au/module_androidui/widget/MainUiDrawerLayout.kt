package com.au.module_androidui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import androidx.drawerlayout.widget.DrawerLayout

/** 在测量子项前将抽屉宽度设为容器宽度的 84%，避免布局后补测量。 */
class MainUiDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DrawerLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val drawerWidth = MeasureSpec.getSize(widthMeasureSpec) * 84 / 100
        for (index in 0 until childCount) {
            val params = getChildAt(index).layoutParams as LayoutParams
            val gravity = Gravity.getAbsoluteGravity(params.gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK
            if (gravity == Gravity.LEFT || gravity == Gravity.RIGHT) {
                params.width = drawerWidth
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
