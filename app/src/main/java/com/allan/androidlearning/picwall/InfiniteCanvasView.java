package com.allan.androidlearning.picwall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.au.module_imagecompressed.PickUriWrap;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 无边界View绘制Bitmap绘制块的实现（增强版）
 * 支持任意方向滑动、捏合缩放、节流计算、可见区域绘制、Bitmap加载管理
 */
public class InfiniteCanvasView extends View implements InfiniteBlockManager.Callback {

    // --- 成员变量 ---
    @NonNull
    private final InfiniteBlockManager blockManager;
    @NonNull
    private final GestureDetector gestureDetector;
    @NonNull
    private final ScaleGestureDetector scaleGestureDetector;
    
    // 绘制相关
    private Paint blockPaint;
    private Paint bitmapPaint;
    private final RectF tempRectF = new RectF(); // 复用对象
    private final Path clipPath = new Path(); // 复用对象

    public InfiniteCanvasView(Context context) {
        this(context, null);
    }

    public InfiniteCanvasView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfiniteCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        // 初始化 Manager
        blockManager = new InfiniteBlockManager(this);
        // 初始化手势检测器
        gestureDetector = new GestureDetector(getContext(), new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
    }

    private void init() {
        // 初始化画笔
        blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blockPaint.setColor(Color.LTGRAY);
        blockPaint.setStyle(Paint.Style.FILL);

        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
    }

    public void setFrameImageList(@NonNull List<PickUriWrap> list) {
        blockManager.setFrameImageList(list);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        blockManager.onSizeChanged(w, h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blockManager.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        blockManager.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scaleResult = scaleGestureDetector.onTouchEvent(event);
        boolean gestureResult = gestureDetector.onTouchEvent(event);
        return scaleResult || gestureResult || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Log.d("au-", " onDraw: ");
        // 保存画布状态
        canvas.save();

        // 应用变换：先平移，再缩放 (V = C * S + T)
        canvas.translate(blockManager.getRealOffsetX(), blockManager.getRealOffsetY());
        float currentScale = blockManager.getCurrentScale();
        canvas.scale(currentScale, currentScale);

        // 绘制块
        List<BlockInfo> drawBlockList = blockManager.getDrawBlockList();
        float cornerRadius = blockManager.getCornerRadius();

        for (BlockInfo block : drawBlockList) {
            drawBlock(canvas, block, currentScale, cornerRadius);
        }

        canvas.restore();
    }
    
    private void drawBlock(Canvas canvas, BlockInfo block, float currentScale, float cornerRadius) {
        // 获取Bitmap
        android.graphics.Bitmap bitmap = blockManager.getBitmap(block, currentScale);

        // 绘制背景或Bitmap
        tempRectF.set(block.getPointLT().x, block.getPointLT().y, block.getPointRB().x, block.getPointRB().y);
        var isBitmapNotNull = bitmap != null;
        var isRecycled = bitmap != null && bitmap.isRecycled();

        if (isBitmapNotNull && !isRecycled) {
            // 绘制Bitmap (使用 clipPath 实现圆角)
            int saveCount = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(tempRectF, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.drawBitmap(bitmap, null, tempRectF, bitmapPaint);
            canvas.restoreToCount(saveCount);
        } else {
            // 绘制占位色块
            canvas.drawRoundRect(tempRectF, cornerRadius, cornerRadius, blockPaint);
        }
    }

    // --- 手势处理 ---

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            blockManager.onScroll(distanceX, distanceY);

            // 计算滑动方向
            InfiniteBlockManager.ScrollDirection direction;
            if (Math.abs(distanceX) > Math.abs(distanceY)) {
                // 水平滑动：distanceX > 0 表示内容向左移（手指左滑），distanceX < 0 表示内容向右移（手指右滑）
                // 对应 ScrollDirection.LEFT 和 ScrollDirection.RIGHT
                direction = distanceX > 0 ? InfiniteBlockManager.ScrollDirection.LEFT : InfiniteBlockManager.ScrollDirection.RIGHT;
            } else {
                // 垂直滑动：distanceY > 0 表示内容向上移（手指上滑），distanceY < 0 表示内容向下移（手指下滑）
                // 对应 ScrollDirection.UP 和 ScrollDirection.DOWN
                direction = distanceY > 0 ? InfiniteBlockManager.ScrollDirection.UP : InfiniteBlockManager.ScrollDirection.DOWN;
            }

            blockManager.onScrollStateChanged(true, direction);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Fling暂不视为持续滑动加载的阻碍，或者视具体需求
            blockManager.onScrollStateChanged(false);
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            blockManager.onScrollStateChanged(true);
            return true;
        }
    }
    
    // 复写onTouchEvent处理Up/Cancel事件来重置滑动状态
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean result = super.dispatchTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            blockManager.onScrollStateChanged(false);
        }
        return result;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private final PointF scaleCenterView = new PointF();

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            scaleCenterView.set(detector.getFocusX(), detector.getFocusY());
            blockManager.onScaleBegin(scaleCenterView);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleCenterView.set(detector.getFocusX(), detector.getFocusY());
            blockManager.onScale(detector.getScaleFactor(), scaleCenterView);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaleCenterView.set(detector.getFocusX(), detector.getFocusY());
            blockManager.onScaleEnd(scaleCenterView);
        }
    }

    @Override
    public void invalidateView(@NonNull String from) {
        Log.d("au-", "invalidateView: from " + from);
        postInvalidate();
    }

    @Override
    public void invalidateView(@NotNull RectF rect) {
        postInvalidate((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
    }
}
