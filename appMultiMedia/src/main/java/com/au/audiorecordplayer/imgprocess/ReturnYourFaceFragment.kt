package com.au.audiorecordplayer.imgprocess

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.View
import androidx.core.net.toFile
import com.au.audiorecordplayer.databinding.FragmentReturnYourFaceBinding
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.widget.SuitScreenHelper
import com.au.module_cached.delegate.AppDataStoreStringCache
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString
import com.au.module_imagecompressed.PickUriWrap
import com.au.module_imagecompressed.pickUriWrapForResult
import com.au.module_simplepermission.PickerType
import java.io.File
import java.io.FileInputStream

class ReturnYourFaceFragment : BindingFragment<FragmentReturnYourFaceBinding>() {
    private var mLastFile by AppDataStoreStringCache("returnYourFaceLastFile", "")

    val singleResult = pickUriWrapForResult().also { it.paramsBuilder.setIgnoreSizeKb(2048) }

    private val thresholdPadding = 10.dp

    /**
     * 线的坐标信息
     */
    private val lineMap = hashMapOf<String, LineInfo>()
    private var mCurrentLineInfo: LineInfo? = null

    private fun initLineMap() {
        lineMap.put("horizontalLine1",
            HorizontalLineInfo("horizontalLine1",
                binding.horzLine1,
                1,
                binding.horzLine1.y,
                minY = -1f,
                maxY = binding.horzLine2.y - thresholdPadding,
                false))
        lineMap.put("horizontalLine2",
            HorizontalLineInfo("horizontalLine2",
                binding.horzLine2,
                2,
                binding.horzLine2.y,
                minY = binding.horzLine1.y + thresholdPadding,
                maxY = -1f,
                false))
//        lineMap.put("horizontalLine3",
//            HorizontalLineInfo("horizontalLine3",
//                binding.horzLine3,
//                binding.horzLine3.y,
//                false))

        lineMap.put("verticalLine1",
            VerticalLineInfo("verticalLine1",
                binding.verticalLine1,
                1,
                binding.verticalLine1.x,
                minX = -1f,
                maxX = binding.verticalLine2.x - thresholdPadding,
                false))
        lineMap.put("verticalLine2",
            VerticalLineInfo("verticalLine2",
                binding.verticalLine2,
                2,
                binding.verticalLine2.x,
                minX = binding.verticalLine1.x + thresholdPadding,
                maxX = -1f,
                false))

        lineMap.forEach { (k, v)->
            v.line.setBackgroundColor(Color.BLACK)
            when (v) {
                is HorizontalLineInfo -> {
                    logdNoFile { "lineMap: $k y ${v.y}" }
                }
                is VerticalLineInfo -> {
                    logdNoFile { "lineMap: $k x ${v.x}" }
                }
            }
        }
    }

    private fun findTouchLine(x: Float, y: Float) : LineInfo?{
        lineMap.forEach { (k, v)->
            if (v is VerticalLineInfo) {
                val lineWidth = v.line.width.toFloat()
                val lineLeft = v.x
                if (x >= lineLeft - thresholdPadding
                        && x <= lineLeft + lineWidth + thresholdPadding) {
                    return v
                }
            } else if (v is HorizontalLineInfo) {
                val lineHeight = v.line.height.toFloat()
                val lineTop = v.y
                if (y >= lineTop - thresholdPadding
                    && y <= lineTop + lineHeight + thresholdPadding) {
                    return v
                }
            }
        }
        return null
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activated = false

    private val listener = object : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            val action = event?.action

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    logdNoFile { "ACTION down: x*y ${event.x} ${event.y} rawX*Y ${event.rawX} ${event.rawY}" }
                    lastTouchY = event.y
                    lastTouchX = event.x
                    val lineInfo = findTouchLine(event.x, event.y) ?: return false
                    mCurrentLineInfo = lineInfo

                    lineInfo.isEnabled = true
                    lineInfo.line.setBackgroundColor(Color.RED)
                    activated = true
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!activated) return false

                    when (mCurrentLineInfo) {
                        is VerticalLineInfo -> {
                            val lineInfo = mCurrentLineInfo as VerticalLineInfo
                            val deltaX: Float = event.x - lastTouchX
                            changeVerticalLine(lineInfo, deltaX)
                        }

                        is HorizontalLineInfo -> {
                            val lineInfo = mCurrentLineInfo as HorizontalLineInfo
                            val deltaY: Float = event.y - lastTouchY
                            changeHorizontalLine(lineInfo, deltaY)
                        }
                        else -> {}
                    }

                    lastTouchY = event.y
                    lastTouchX = event.x
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activated = false
                    logdNoFile { "finder is up $mCurrentLineInfo" }
                    mCurrentLineInfo?.let { lineInfo ->
                        lineInfo.isEnabled = false
                        lineInfo.line.setBackgroundColor(Color.BLACK)

                        resetMinMax()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun changeVerticalLine(lineInfo: VerticalLineInfo, deltaX: Float) {
        val line = lineInfo.line
        val l = (line.left + deltaX)
        val r = (line.right + deltaX)

        if (l >= lineInfo.minX && r <= lineInfo.maxX) {
            line.layout(
                l.toInt(),
                line.top,
                r.toInt(),
                line.bottom
            )
            lineInfo.x = l
        }
    }

    private fun changeHorizontalLine(lineInfo: HorizontalLineInfo, deltaY: Float) {
        val line = lineInfo.line
        val t = (line.top + deltaY)
        val b = (line.bottom + deltaY)

        if (t >= lineInfo.minY && b <= lineInfo.maxY) {
            line.layout(
                line.left,
                t.toInt(),
                line.right,
                b.toInt()
            )
            lineInfo.y = t
        }
    }

    private fun resetMinMax() {
        (lineMap["horizontalLine1"] as HorizontalLineInfo).let {
            it.minY = -1f
            it.maxY = binding.horzLine2.y - thresholdPadding
        }
        (lineMap["horizontalLine2"] as HorizontalLineInfo).let {
            it.minY = binding.horzLine1.y + thresholdPadding
            it.maxY = -1f
        }
        (lineMap["verticalLine1"] as VerticalLineInfo).let {
            it.minX = -1f
            it.maxX = binding.verticalLine2.x - thresholdPadding
        }
        (lineMap["verticalLine2"] as VerticalLineInfo).let {
            it.minX = binding.verticalLine1.x + thresholdPadding
            it.maxX = -1f
        }
    }

    fun parseImageSize(file: File) {
        val inputStream = FileInputStream(file)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        if (options.outWidth > 0 && options.outHeight > 0) {
            val suitHelper = SuitScreenHelper(binding.viewFinder, requireActivity()
            ) { Size(options.outWidth, options.outHeight) }
            suitHelper.doOnCreate()
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.selectedImageButton.onClick {
            singleResult.launchOneByOne(PickerType.IMAGE, null) { uri->
                mLastFile = uri.toGsonString()
                logdNoFile { "selectedImage: $uri" }
                binding.selectedImageButton.gone()
                binding.adjustImageGroup.visible()
                binding.viewFinder.glideSetAny(uri.uriParsedInfo.uri)
                parseImageSize(uri.uriParsedInfo.uri.toFile())
            }
        }

        binding.root.setOnTouchListener(listener)

        binding.verticalLine1.post {
            if (isResumed) {
                initLineMap()

                if (mLastFile.isNotEmpty()) {
                    ConfirmCenterDialog.show(
                        childFragmentManager,
                        "提示",
                        "是否继续上次操作？",
                        "是",
                        "否"
                    ) {
                        binding.selectedImageButton.gone()
                        binding.adjustImageGroup.visible()
                        val lastUrl = mLastFile.fromGson<PickUriWrap>()?.uriParsedInfo?.uri
                        lastUrl?.let { uri->
                            binding.viewFinder.glideSetAny(uri)
                            parseImageSize(uri.toFile())
                        }
                        it.dismissAllowingStateLoss()
                    }
                }
            }
        }
    }
}