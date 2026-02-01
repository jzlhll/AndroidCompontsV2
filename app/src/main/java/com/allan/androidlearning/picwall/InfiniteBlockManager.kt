package com.allan.androidlearning.picwall

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.dpFloat
import com.au.module_imagecompressed.PickUriWrap
import java.util.Collections
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Block Manager
 * 负责维护Blocks的坐标、响应手势变更状态、以及Bitmap的加载
 */
class InfiniteBlockManager(
    private val callback: Callback
) : BitmapLoadHelper.OnBitmapLoadListener {

    interface Callback {
        fun invalidateView(from:String)
        fun invalidateView(rect: RectF)
    }

    companion object {
        private const val CORNER_RADIUS_DP = 16
        private const val BLOCK_GAP_DP = 16
        private const val W_EXTEND = 1f
        private const val H_EXTEND = 1f
        private const val W_MAX_EXTEND = 1.5f
        private const val H_MAX_EXTEND = 1.5f
        private const val BLOCK_WIDTH_RATIO = 0.75f // 屏幕宽度的 3/4
        private const val BLOCK_ASPECT_RATIO = 4f / 3f // 高:宽 = 4:3
        // 滑动阻尼系数：数值越小阻力越大，1.0为完全跟手
        private const val SLIDE_SCALE = 1f
        private const val CALC_INTERVAL_MS = 16L

        // 缩放常量
        private const val MAX_SCALE = 1.0f
        private const val MIN_SCALE = 0.21f
        // 慢速滑动阈值，低于此速度认为可以加载高清图
        private const val SPEED_THRESHOLD_DP = 16
    }

    enum class ScrollDirection {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    // --- 成员变量 ---
    private var blockWidth: Float = 0f
    private var blockHeight: Float = 0f
    private var blockGap: Float = 0f
    var cornerRadius: Float = 0f
        private set
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    // 画布偏移量 (Screen Pixels)
    var realOffsetX = 0f
        private set
    var realOffsetY = 0f
        private set

    // 缩放相关
    var currentScale = MAX_SCALE
        private set

    // 节流控制
    private var lastCalcTime = 0L
    private var currentScrollDirection = ScrollDirection.NONE
    private var mIsScrolling = false
    private var mIsScaling = false
    private var isFastScrolling = false
    private var speedThresholdPx = 0f

    // 数据存储
    private var drawBlockList = ArrayList<BlockInfo>()
    private var activeBlockMap = HashMap<Long, BlockInfo>() // Key: Long (index)

    // 辅助类
    private val bitmapLoadHelper = BitmapLoadHelper(this)

    // 排序比较器
    private val distanceComparator = Comparator<BlockInfo> { o1, o2 ->
        o1.distance.compareTo(o2.distance)
    }

    fun onSizeChanged(w: Int, h: Int) {
        viewWidth = w
        viewHeight = h

        // 预计算尺寸
        blockWidth = w * BLOCK_WIDTH_RATIO
        blockHeight = blockWidth * BLOCK_ASPECT_RATIO
        blockGap = BLOCK_GAP_DP.dpFloat
        cornerRadius = CORNER_RADIUS_DP.dpFloat
        speedThresholdPx = SPEED_THRESHOLD_DP.dpFloat

        // 初始偏移：让 (0,0) 块居中
        // 目标：Center(0,0) = ViewCenter
        // Center(0,0).x = 0 * stepX + blockWidth/2 = blockWidth/2
        // ViewCenter.x = viewWidth/2
        // Canvas(0,0) = (ViewCenter - Offset) / Scale
        // Offset = ViewCenter - CanvasCenter * Scale
        // 假设 Scale=1, Offset = ViewCenter - BlockCenter
        realOffsetX = (viewWidth - blockWidth) / 2f
        realOffsetY = (viewHeight - blockHeight) / 2f

        // 尺寸变化，重置或重新计算

        if (blockWidth > 0f && blockHeight > 0f && mHasData) {
            calculateDrawBlocks("onSize Changed")
        }
    }

    fun onDetachedFromWindow() {
        bitmapLoadHelper.onDetachedFromWindow()
    }

    fun onAttachedToWindow() {
        bitmapLoadHelper.onAttachedToWindow()
        // 恢复时重新计算并刷新，确保界面状态正确

        if (blockWidth > 0f && blockHeight > 0f && mHasData) {
            calculateDrawBlocks("onAttached To Window")
            callback.invalidateView("onAttached To Window")
        }
    }

    // --- 数据获取 ---

    private var mHasData = false

    fun setFrameImageList(list: List<PickUriWrap>) {
        bitmapLoadHelper.onInitial(list)
        mHasData = list.isNotEmpty()
        calculateDrawBlocks("set Frame ImageList")
        callback.invalidateView("set Frame ImageList")
    }

    fun getDrawBlockList(): List<BlockInfo> {
        return drawBlockList
    }

    // --- 手势处理代理 ---

    fun onScroll(distanceX: Float, distanceY: Float) {
        // 应用滑动映射比例
        realOffsetX -= distanceX * SLIDE_SCALE
        realOffsetY -= distanceY * SLIDE_SCALE

        val distance = hypot(distanceX, distanceY)
        isFastScrolling = distance > speedThresholdPx

        bitmapLoadHelper.onScrollStateChanged(true)
        throttleCalculate()
    }

    fun onScrollStateChanged(isScrolling: Boolean, direction: ScrollDirection) {
        mIsScrolling = isScrolling
        currentScrollDirection = direction
        bitmapLoadHelper.onScrollStateChanged(isScrolling)
        if (!isScrolling) {
            // 停止滑动，重置方向并强制刷新一次
            currentScrollDirection = ScrollDirection.NONE
            calculateDrawBlocks("onScroll State Changed")
            callback.invalidateView("onScroll State Changed")
        }
    }

    fun onScrollStateChanged(isScrolling: Boolean) {
        onScrollStateChanged(isScrolling, ScrollDirection.NONE)
    }

    fun onScaleBegin(scaleCenterView: PointF) {
        // 计算当前缩放中心对应的Canvas坐标
        // Canvas = (View - Offset) / Scale
        val scaleCenterCanvas = PointF(
            (scaleCenterView.x - realOffsetX) / currentScale,
            (scaleCenterView.y - realOffsetY) / currentScale
        )

        mIsScaling = true
        bitmapLoadHelper.onScaleStart(scaleCenterCanvas, currentScale)
    }

    fun onScale(scaleFactor: Float, scaleCenterView: PointF) {
        var targetScale = currentScale * scaleFactor

        // 限制缩放范围
        targetScale = max(MIN_SCALE, min(targetScale, MAX_SCALE))

        // 计算当前缩放中心对应的Canvas坐标 (Scale前)
        val scaleCenterCanvas = PointF(
            (scaleCenterView.x - realOffsetX) / currentScale,
            (scaleCenterView.y - realOffsetY) / currentScale
        )

        // 更新 Offset 以保持缩放中心不动
        // View = Canvas * Scale + Offset
        // Offset = View - Canvas * Scale
        realOffsetX = scaleCenterView.x - scaleCenterCanvas.x * targetScale
        realOffsetY = scaleCenterView.y - scaleCenterCanvas.y * targetScale

        currentScale = targetScale

        throttleCalculate()
    }

    fun onScaleEnd(scaleCenterView: PointF) {
        // 计算当前缩放中心对应的Canvas坐标
        val scaleCenterCanvas = PointF(
            (scaleCenterView.x - realOffsetX) / currentScale,
            (scaleCenterView.y - realOffsetY) / currentScale
        )
        mIsScaling = false
        bitmapLoadHelper.onScaleEnd(scaleCenterCanvas, currentScale)
        calculateDrawBlocks("onScale End")
        callback.invalidateView("onScale End")
    }

    fun getBitmap(blockInfo: BlockInfo, scale: Float) : Bitmap? {
        return bitmapLoadHelper.getCachedBitmap(blockInfo, scale, true)
    }

    // --- 核心计算逻辑 ---

    private fun calculateDrawBlocks(from:String) {
        //ignore //if (blockWidth <= 0f || blockHeight <= 0f) return
        logdNoFile { "calculate Draw Blocks isFastScrolling $isFastScrolling from( $from )" }
        // 只有在缩放中，或者快速滑动中，才认为是低质量模式
        val useLowQuality = mIsScaling || (mIsScrolling && isFastScrolling)

        // 1. 计算可见区域 Rect (Canvas坐标)
        val viewCenterX = viewWidth / 2f
        val viewCenterY = viewHeight / 2f
        val currentCenterX = (viewCenterX - realOffsetX) / currentScale
        val currentCenterY = (viewCenterY - realOffsetY) / currentScale

        // 基础扩展 + 方向额外扩展（修正逻辑：保持至少 W_EXTEND/H_EXTEND 的 buffer）
        val finalExtendLeft = if (currentScrollDirection == ScrollDirection.LEFT) W_MAX_EXTEND else W_EXTEND
        val finalExtendRight = if (currentScrollDirection == ScrollDirection.RIGHT) W_MAX_EXTEND else W_EXTEND
        val finalExtendTop = if (currentScrollDirection == ScrollDirection.UP) H_MAX_EXTEND else H_EXTEND
        val finalExtendBottom = if (currentScrollDirection == ScrollDirection.DOWN) H_MAX_EXTEND else H_EXTEND

        val visibleHalfW = (viewWidth / currentScale / 2f)
        val visibleHalfH = (viewHeight / currentScale / 2f)

        val visibleRect = RectF(
            currentCenterX - visibleHalfW * finalExtendLeft,
            currentCenterY - visibleHalfH * finalExtendTop,
            currentCenterX + visibleHalfW * finalExtendRight,
            currentCenterY + visibleHalfH * finalExtendBottom
        )

        // 1.5 计算真实可见区域（仅在非运动状态时计算）
        // 修改：如果是慢速滑动，也允许计算真实可见区域（从而加载高清图）
        val realVisibleRect = if (!useLowQuality) {
            RectF(
                currentCenterX - visibleHalfW,
                currentCenterY - visibleHalfH,
                currentCenterX + visibleHalfW,
                currentCenterY + visibleHalfH
            )
        } else {
            null
        }

        // 2. 计算Grid索引范围
        val stepX = blockWidth + blockGap
        val stepY = blockHeight + blockGap

        // 由于使用了交错布局，min/max 的计算需要留出足够的 buffer
        // 因为右边的列会下沉，所以要多计算上方；左边的列会抬升，要多计算下方
        // 简单起见，上下都多扩一行
        val minIdxX = floor(visibleRect.left / stepX).toInt() - 1
        val maxIdxX = floor(visibleRect.right / stepX).toInt() + 1
        // 注意：minIdxY 和 maxIdxY 不再是全局固定的，需要在循环中针对每一列动态计算

        // 3. 生成/复用 BlockInfo
        val newDrawList = ArrayList<BlockInfo>()
        val newActiveMap = HashMap<Long, BlockInfo>()

        var reuseCount = 0
        var createCount = 0

        // 遍历生成
        for (ix in minIdxX..maxIdxX) {
            // 针对每一列，计算因交错布局偏移后的有效 Y 索引范围
            // blockTop = iy * stepY + ix * (h/3)
            // blockBottom = blockTop + h
            // 可见条件：blockBottom > rect.top && blockTop < rect.bottom
            
            val yOffset = ix * (blockHeight / 3f)
            val minIdxY = floor((visibleRect.top - yOffset) / stepY).toInt() - 1
            val maxIdxY = floor((visibleRect.bottom - yOffset) / stepY).toInt() + 1

            for (iy in minIdxY..maxIdxY) {
                // Key 优化：使用位运算合成 Long Key
                // (ix << 32) | (iy & 0xFFFFFFFFL)
                val key = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)

                var block = activeBlockMap[key]
                if (block == null) {
                    // 新增块
                    block = createBlockInfo(ix, iy, stepX, stepY, key)
                    createCount++
                } else {
                    reuseCount++
                }

                // 只有当块真正与可见区域相交时才添加
                if (RectF.intersects(visibleRect, RectF(block.pointLT.x, block.pointLT.y, block.pointRB.x, block.pointRB.y))) {
                    // 更新距离用于排序
                    block.distance = hypot(block.centerPoint.x - currentCenterX, block.centerPoint.y - currentCenterY)
                    
                    // 判断是否在真实可见区域内（仅在非运动状态时计算）
                    block.isRealVisible = if (realVisibleRect != null) {
                        RectF.intersects(realVisibleRect, RectF(block.pointLT.x, block.pointLT.y, block.pointRB.x, block.pointRB.y))
                    } else {
                        false
                    }
                    
                    newDrawList.add(block)
                    newActiveMap[key] = block
                }
            }
        }

        // 4. 更新列表和Map
        activeBlockMap = newActiveMap
        // 5. 排序
        Collections.sort(newDrawList, distanceComparator)
        logdNoFile { "DrawBlocks: reuse=$reuseCount, create=$createCount, total=${newDrawList.size}" }

        //6. load bitmap async
        bitmapLoadHelper.loadAllBitmapsAsync(newDrawList, currentScale)

        drawBlockList = newDrawList
    }

    private fun createBlockInfo(ix: Int, iy: Int, stepX: Float, stepY: Float, key: Long): BlockInfo {
        // 核心交错逻辑：每一列相比前一列，下沉 1/3 高度
        // offset = ix * (blockHeight / 3)
        // 这样 ix=1 比 ix=0 低，ix=-1 比 ix=0 高
        val yOffset = ix * (blockHeight / 3f)

        val left = ix * stepX
        val top = iy * stepY + yOffset
        val right = left + blockWidth
        val bottom = top + blockHeight

        val lt = PointF(left, top)
        val rt = PointF(right, top)
        val rb = PointF(right, bottom)
        val lb = PointF(left, bottom)
        val center = PointF(left + blockWidth / 2f, top + blockHeight / 2f)

        return BlockInfo(lt, rt, rb, lb, center, key, 0f)
    }

    private fun throttleCalculate() {
        val now = System.currentTimeMillis()
        if (now - lastCalcTime > CALC_INTERVAL_MS) {
            calculateDrawBlocks("throttle Calculate")
            callback.invalidateView("throttle Calculate")
            lastCalcTime = now
        }
    }

    // --- 接口实现 ---

    override fun onBitmapLoaded(blockInfo: BlockInfo, currentScale: Float) {
//        val left = blockInfo.pointLT.x * currentScale + realOffsetX
//        val top = blockInfo.pointLT.y * currentScale + realOffsetY
//        val right = blockInfo.pointRB.x * currentScale + realOffsetX
//        val bottom = blockInfo.pointRB.y * currentScale + realOffsetY
//        val rect = RectF(left, top, right, bottom)
        callback.invalidateView("onBitmap Loaded")
    }
}
