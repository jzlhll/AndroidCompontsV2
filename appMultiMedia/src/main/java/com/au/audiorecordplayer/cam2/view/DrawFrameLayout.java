package com.au.audiorecordplayer.cam2.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.au.module_android.utils.ALogJ;

/**
 * Draw demo：
 * Canvas+paint+path最基本的绘制
 * 使用Canvas(Bitmap)+onTouch做跟随画笔 Bitmap缓冲区；保存也方便。再通过List缓存paint用于回撤实现。
 *
 * 更复杂：
 * 双缓冲Bitmap实现；
 * SurfaceView(surface) 如果有一些自动动作，则应该实现SurfaceView绘制，控制帧率。
 *
 * 优化点：
 * canvas.clipRect() 区域更新。
 */
public class DrawFrameLayout extends View {
    public DrawFrameLayout(@NonNull Context context) {
        super(context);
    }

    public DrawFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DrawFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private Bitmap cacheBitmap;
    Canvas cacheCanvas = null;// 定义cacheBitmap上的Canvas对象

    private final Path mCurrentPath = new Path();
    private final Paint paint = new Paint();

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        ALogJ.t("on size changed! " + w + ", " + h);
        if(cacheBitmap != null) cacheBitmap.recycle();
        cacheBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        cacheCanvas = new Canvas();
        cacheCanvas.setBitmap(cacheBitmap);
        cacheCanvas.drawColor(Color.TRANSPARENT);

        paint.setColor(Color.GREEN); // 设置默认的画笔颜色
        // 设置画笔风格
        paint.setStyle(Paint.Style.STROKE);	//设置填充方式为描边
        paint.setStrokeJoin(Paint.Join.ROUND);		//设置笔刷的图形样式
        paint.setStrokeCap(Paint.Cap.ROUND);	//设置画笔转弯处的连接风格
        paint.setStrokeWidth(5); // 设置默认笔触的宽度为5像素
        paint.setAntiAlias(true); // 使用抗锯齿功能
        paint.setDither(true); // 使用抖动效果
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float preX;	//起始点的x坐标值
    private float preY;//起始点的y坐标值

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // 获取触摸事件的发生位置
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath.moveTo(x, y); // 将绘图的起始点移到（x,y）坐标点的位置
                preX = x;
                preY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(x - preX);
                float dy = Math.abs(y - preY);
                if (dx >= 5 || dy >= 5) { // 判断是否在允许的范围内
                    mCurrentPath.quadTo(preX, preY, (x + preX) / 2, (y + preY) / 2);
                    preX = x;
                    preY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                cacheCanvas.drawPath(mCurrentPath, paint); //绘制路径
                mCurrentPath.reset();
                break;
        }
        ALogJ.t("on touch ed");
        postInvalidate();
        return true;
    }

    Paint bmpPaint = new Paint();	//采用默认设置创建一个画笔

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        ALogJ.t("onDraw ");
        canvas.drawText("adfajdks", 0, 0, 0, 0, paint);
        canvas.drawBitmap(cacheBitmap, 0, 0, bmpPaint); //绘制cacheBitmap
        canvas.drawPath(mCurrentPath, paint);	//绘制路径
        canvas.save();	//保存canvas的状态
        //canvas.restore();	//恢复canvas之前保存的状态，防止保存后对canvas执行的操作对后续的绘制有影响
    }
}
