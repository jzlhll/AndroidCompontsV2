package com.allan.androidlearning.picwall

import android.graphics.PointF

/**
 * 存储绘制块的坐标信息
 */
data class BlockInfo(
    val pointLT: PointF,
    val pointRT: PointF,
    val pointRB: PointF,
    val pointLB: PointF,
    val centerPoint: PointF,
    val key: String,
    var distance: Float = 0f,
    var isRealVisible: Boolean = false
)
