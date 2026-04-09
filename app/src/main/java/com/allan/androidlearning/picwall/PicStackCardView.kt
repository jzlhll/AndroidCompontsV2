package com.allan.androidlearning.picwall

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import com.allan.androidlearning.R
import com.allan.androidlearning.databinding.ViewPicStackCardBinding
import com.au.module_android.utils.dp
import com.au.module_android.utils.dpFloat
import com.au.module_android.utilsmedia.UriParsedInfo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PicStackCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_FRAME_STROKE_DP = 10f
        private const val DEFAULT_CORNER_RADIUS_DP = 24f
        private const val DEFAULT_ASPECT_RATIO = 1f
        private const val MIN_ASPECT_RATIO = 0.35f
        private const val MAX_ASPECT_RATIO = 3.2f
    }

    private val binding = ViewPicStackCardBinding.inflate(LayoutInflater.from(context), this, true)
    private var contentInsetPx = DEFAULT_FRAME_STROKE_DP.dp
    private var contentAspectRatio = DEFAULT_ASPECT_RATIO

    /**
     * 绑定卡片展示数据。
     *
     * @param bean 图片数据
     * @param frameStrokeWidthDp 外层边框占位宽度，单位 dp
     * @return Unit
     * @throws Nothing
     */
    fun bindData(bean: UriParsedInfo, frameStrokeWidthDp: Float = DEFAULT_FRAME_STROKE_DP) {
        updateFrameStroke(frameStrokeWidthDp)
        contentAspectRatio = DEFAULT_ASPECT_RATIO
        updateContentLayout()

        Glide.with(binding.contentImg)
            .load(bean.uri)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .fitCenter()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    val width = resource.intrinsicWidth
                    val height = resource.intrinsicHeight
                    if (width > 0 && height > 0) {
                        val ratio = width.toFloat() / height.toFloat()
                        contentAspectRatio = max(MIN_ASPECT_RATIO, min(ratio, MAX_ASPECT_RATIO))
                        post { updateContentLayout() }
                    }
                    return false
                }
            })
            .into(binding.contentImg)
    }

    override fun onDetachedFromWindow() {
        Glide.with(binding.contentImg).clear(binding.contentImg)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            updateContentLayout()
        }
    }

    private fun updateFrameStroke(frameStrokeWidthDp: Float) {
        contentInsetPx = max(0f, frameStrokeWidthDp.dp)
        val drawable = AppCompatResources.getDrawable(context, R.drawable.bg_pic_stack_frame_placeholder)?.mutate()
        val frameDrawable = drawable as? android.graphics.drawable.GradientDrawable
        if (frameDrawable != null) {
            frameDrawable.cornerRadius = DEFAULT_CORNER_RADIUS_DP.dp
            frameDrawable.setStroke(max(1, contentInsetPx.roundToInt()), 0xFFFFF2D6.toInt())
            binding.frameImg.setImageDrawable(frameDrawable)
        } else {
            binding.frameImg.setImageResource(R.drawable.bg_pic_stack_frame_placeholder)
        }
    }

    private fun updateContentLayout() {
        val availableWidth = width - contentInsetPx * 2f
        val availableHeight = height - contentInsetPx * 2f
        if (availableWidth <= 0f || availableHeight <= 0f) {
            return
        }

        val availableRatio = availableWidth / availableHeight
        val targetWidth: Float
        val targetHeight: Float
        if (contentAspectRatio >= availableRatio) {
            targetWidth = availableWidth
            targetHeight = targetWidth / contentAspectRatio
        } else {
            targetHeight = availableHeight
            targetWidth = targetHeight * contentAspectRatio
        }

        val params = binding.contentImg.layoutParams as LayoutParams
        params.width = max(1, targetWidth.roundToInt())
        params.height = max(1, targetHeight.roundToInt())
        binding.contentImg.layoutParams = params
    }
}
