package com.au.module_android.widget

import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import com.au.module_android.utils.dp
import com.au.module_android.utils.getScreenFullSize

/**
 * 让一个View，能够适配屏幕高宽
 * @param view 目标
 * @param fetchImageSizeBlock 获取图片尺寸的函数。可以运行在子线程
 */
class SuitScreenHelper(private val view: View,
                       private val activity: android.app.Activity,
                       private val fetchImageSizeBlock:()->Size) {
    companion object {
        private const val IS_DEBUG = true
        private const val TAG = "SuitScreenHelper"
    }

    var minPadding = 12.dp

    var endOfSuitCallback:(()->Unit) = {}

    @WorkerThread
    fun doOnCreate() {
        val size = fetchImageSizeBlock()
        val origHeight = size.height
        val origWidth = size.width
        //修正尺寸显示
        view.post {
            val size = activity.getScreenFullSize()
            val currentWidth = view.width
            val currentHeight = view.height

            val minHeightByWidth = currentWidth * origHeight / origWidth //基于宽度的最小高度要求

            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            val topMargin = lp.topMargin
            val bottomMargin = lp.bottomMargin

            if (IS_DEBUG) Log.d(TAG, "screen$size curSize: ($currentWidth*$currentHeight minHeightByWidth:$minHeightByWidth) bottomMargin:$bottomMargin topMargin:$topMargin")
            if (currentHeight > minHeightByWidth) {
                if (IS_DEBUG) Log.d(TAG, "bottomMargin++")
                lp.bottomMargin = bottomMargin + currentHeight - minHeightByWidth
                view.layoutParams = lp
            } else if (currentHeight < minHeightByWidth) {
                val padding = minPadding
                if (topMargin - padding + currentHeight >= minHeightByWidth) {
                    if (IS_DEBUG) Log.d(TAG, "topMargin--")
                    //1. 只将topMargin修改到最少为12dp的margin的情况
                    val newTopMargin = currentHeight + topMargin - minHeightByWidth
                    lp.topMargin = newTopMargin
                } else if (topMargin - padding + bottomMargin - padding + currentHeight >= minHeightByWidth) {
                    if (IS_DEBUG) Log.d(TAG, "topMargin-ToMax & bottomMargin--")
                    //2. 只修改上面topMargin不够，修改下面bottomMargin
                    val newTopMargin = padding
                    val newBottomMargin = (topMargin + bottomMargin + currentHeight) - (padding + minHeightByWidth)
                    lp.topMargin = newTopMargin
                    lp.bottomMargin = newBottomMargin
                } else {
                    //不得不修改宽度了。
                    if (IS_DEBUG) Log.d(TAG, "topMargin-ToMax & bottomMargin-ToMax & widthMargin--")
                    val newHeight = topMargin - padding + bottomMargin - padding + currentHeight
                    val minWidthByHeight = newHeight * origWidth / origHeight //基于新的高度，做最小宽度的计算
                    lp.topMargin = padding
                    lp.bottomMargin = padding
                    lp.marginStart = (currentWidth - minWidthByHeight) / 2
                    lp.marginEnd = (currentWidth - minWidthByHeight) / 2
                }

                view.layoutParams = lp
            }

            endOfSuitCallback()

            if (IS_DEBUG) {
                view.postDelayed({
                    Log.d(TAG, "view new size: ${view.width} * ${view.height}")
                }, 1000)
            }
        }
    }
}