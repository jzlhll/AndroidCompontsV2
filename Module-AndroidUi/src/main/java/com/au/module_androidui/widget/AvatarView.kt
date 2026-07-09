package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import com.au.module_androidui.R
import com.au.module_androidui.databinding.AvatarViewBinding
import kotlin.math.abs
import kotlin.math.min

class AvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {
    private val binding: AvatarViewBinding = AvatarViewBinding.inflate(LayoutInflater.from(context), this)
    private val avatarTextSizeRatio = 0.4f

    private val colorArray = intArrayOf(
        "#FFDBCEAF".toColorInt(),
        "#FFB7A196".toColorInt(),
        "#FF5F5AA2".toColorInt(),
        "#FF096B72".toColorInt(),
        "#FF38658B".toColorInt(),
        "#FFD0877E".toColorInt(),
        "#FF7192BE".toColorInt(),
        "#FF697A21".toColorInt()
    )

    init {
        context.obtainStyledAttributes(attrs, R.styleable.AvatarView).apply {
            val defaultSrc = getResourceId(R.styleable.AvatarView_avatarDefaultSrc, -1)
            if (defaultSrc != -1) {
                setAvatarResource(defaultSrc)
            }
            val defaultText = getString(R.styleable.AvatarView_avatarDefaultText).orEmpty()
            if (defaultText.isNotEmpty()) {
                setAvatarText(defaultText)
            }
            recycle()
        }
        avatarText.fontMode = FontMode.MID
    }

    /**
     * 根据文本内容生成固定的背景颜色
     * 相同文本总是显示相同颜色
     */
    private fun getColorFromText(text: String): Int {
        if (text.isEmpty()) return colorArray.random()

        // 使用字符的hashCode来选择颜色，确保相同文字总是相同颜色
        val positiveHash = abs(text.hashCode().toLong())
        val index = (positiveHash % colorArray.size).toInt()
        return colorArray[index]
    }

    val avatarText : CustomFontText
        get() = binding.textAvatar
    val avatarImage : ImageView
        get() {
            showAvatarImage()
            return binding.urlAvatar
        }

    private fun showAvatarImage() {
        super.setCardBackgroundColor(Color.WHITE)
        binding.urlAvatar.visibility = VISIBLE
        binding.textAvatar.visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateAvatarTextSize(w, h)
    }

    private fun updateAvatarTextSize(w: Int = width, h: Int = height) {
        val shortSide = min(w, h)
        if (shortSide <= 0) return
        avatarText.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * avatarTextSizeRatio)
    }

    /**
     * 设置头像的图片资源
     */
    fun setAvatarResource(resId: Int) {
        binding.urlAvatar.setImageResource(resId)
        showAvatarImage()
    }

    /**
     * 设置头像的文本内容，文字始终大写白色，并设置自带背景颜色
     */
    fun setAvatarText(text: String) {
        val upperText = text.uppercase()
        avatarText.text = upperText
        avatarText.setTextColor(Color.WHITE)
        updateAvatarTextSize()
        super.setCardBackgroundColor(getColorFromText(upperText))
        binding.urlAvatar.visibility = GONE
        binding.textAvatar.visibility = VISIBLE
    }

    override fun setCardBackgroundColor(color: Int) {
        throw IllegalArgumentException("Must use setAvatarText() to auto set background color")
    }
}
