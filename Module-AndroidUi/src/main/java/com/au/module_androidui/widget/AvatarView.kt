package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.cardview.widget.CardView
import com.au.module_android.widget.CustomFontText
import com.au.module_androidui.databinding.AvatarViewBinding
import kotlin.math.absoluteValue

class AvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CardView(context, attrs) {
    private val binding: AvatarViewBinding = AvatarViewBinding.inflate(LayoutInflater.from(context), this)

    private val colorArray = intArrayOf(
        Color.parseColor("#FF4285F4"), // 蓝色
        Color.parseColor("#FFEA4335"), // 红色
        Color.parseColor("#FF34A853"), // 绿色
        Color.parseColor("#FFFBBC05"), // 黄色
        Color.parseColor("#FFFF6B6B"), // 珊瑚红
        Color.parseColor("#FF48DBFB"), // 青色
        Color.parseColor("#FF9A4BB0"), // 紫色
        Color.parseColor("#FFFF9800")  // 橙色
    )

    /**
     * 根据文本内容生成固定的背景颜色
     * 相同文本总是显示相同颜色
     */
    private fun getColorFromText(text: String): Int {
        if (text.isEmpty()) return colorArray.random()

        // 使用字符的hashCode来选择颜色，确保相同文字总是相同颜色
        val index = text.hashCode().absoluteValue % colorArray.size
        return colorArray[index]
    }

    val avatarText : CustomFontText
        get() = binding.textAvatar
    val avatarImage : ImageView
        get() = binding.urlAvatar

    /**
     * 设置头像的文本内容，文字始终大写白色，并设置自带背景颜色
     */
    fun setAvatarText(text: String) {
        val upperText = text.uppercase()
        avatarText.text = upperText
        avatarText.setTextColor(Color.WHITE)
        super.setCardBackgroundColor(getColorFromText(upperText))
    }

    override fun setCardBackgroundColor(color: Int) {
        throw IllegalArgumentException("Must use setAvatarText() to auto set background color")
    }
}