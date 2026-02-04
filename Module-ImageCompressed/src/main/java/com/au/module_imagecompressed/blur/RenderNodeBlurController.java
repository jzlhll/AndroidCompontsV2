package com.au.module_imagecompressed.blur;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.au.module_imagecompressed.blur.SizeScaler.Size;

@RequiresApi(api = Build.VERSION_CODES.S)
public class RenderNodeBlurController implements BlurController {
    private final int[] targetLocation = new int[2];
    private final int[] blurViewLocation = new int[2];

    private final BlurView blurView;
    private final BlurTarget target;
    private final RenderNode blurNode = new RenderNode("BlurView node");
    private final float scaleFactor;
    private final boolean applyNoise;

    private Drawable frameClearDrawable;
    private int overlayColor;
    private float blurRadius = 1f;
    private boolean enabled = true;
    private int gradientDirection = BlurView.GRADIENT_NONE;

    private final GradientCache gradientCache = new GradientCache();

    // Potentially cached stuff from the slow software path
    @Nullable
    private Bitmap cachedBitmap;
    @Nullable
    private RenderScriptBlur fallbackBlur;

    // This tracks BlurView location in scrollable containers, during animations, etc.
    private final ViewTreeObserver.OnPreDrawListener drawListener = () -> {
        saveOnScreenLocation();
        updateRenderNodeProperties();
        return true;
    };

    public RenderNodeBlurController(@NonNull BlurView blurView, @NonNull BlurTarget target, int overlayColor, float scaleFactor, boolean applyNoise) {
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.target = target;
        this.scaleFactor = scaleFactor;
        this.applyNoise = applyNoise;
        blurView.setWillNotDraw(false);
        blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!enabled) {
            return true;
        }
        saveOnScreenLocation();

        if (canvas.isHardwareAccelerated()) {
            hardwarePath(canvas);
        } else {
            // Rendering on a software canvas.
            // Presumably this is something taking a programmatic screenshot,
            // or maybe a software-based View/Fragment transition.
            // This is slow and shouldn't be a common case for this controller.
            softwarePath(canvas);
        }
        return true;
    }

    // Not doing any scaleFactor-related manipulations here, because RenderEffect blur internally
    // already scales down the snapshot depending on the blur radius.
    // https://cs.android.com/android/platform/superproject/main/+/main:external/skia/src/core/SkImageFilterTypes.cpp;drc=61197364367c9e404c7da6900658f1b16c42d0da;l=2103
    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/hwui/jni/RenderEffect.cpp;l=39;drc=61197364367c9e404c7da6900658f1b16c42d0da?q=nativeCreateBlurEffect&ss=android%2Fplatform%2Fsuperproject%2Fmain
    private void hardwarePath(Canvas canvas) {
        // TODO would be good to keep it the size of the BlurView instead of the target, but then the animation
        //  like translation and rotation would go out of bounds. Not sure if there's a good fix for this
        blurNode.setPosition(0, 0, target.getWidth(), target.getHeight());
        updateRenderNodeProperties();

        drawSnapshot();

        canvas.save();
        // Don't draw outside of the BlurView bounds if parent has clipChildren = false
        canvas.clipRect(0f, 0f, blurView.getWidth(), blurView.getHeight());
        // Draw on the system canvas
        canvas.drawRenderNode(blurNode);
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayColor != Color.TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        canvas.restore();
    }

    private void updateRenderNodeProperties() {
        float layoutTranslationX = -getLeft();
        float layoutTranslationY = -getTop();

        // Pivot point for the rotation and scale (in case it's applied)
        blurNode.setPivotX(blurView.getWidth() / 2f - layoutTranslationX);
        blurNode.setPivotY(blurView.getHeight() / 2f - layoutTranslationY);
        blurNode.setTranslationX(layoutTranslationX);
        blurNode.setTranslationY(layoutTranslationY);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            // There's a bug on API 31 - blurNode doesn't get re-rendered on setting new translation/scale/rotation,
            // so we need to re-apply the blur effect to trigger a redraw.
            applyBlur();
        }
    }

    private void drawSnapshot() {
        RecordingCanvas recordingCanvas = blurNode.beginRecording();
        if (frameClearDrawable != null) {
            frameClearDrawable.draw(recordingCanvas);
        }
        recordingCanvas.drawRenderNode(target.renderNode);
        // Looks like the order of this doesn't matter
        applyBlur();
        blurNode.endRecording();
    }

    private void softwarePath(Canvas canvas) {
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        Size original = new Size(blurView.getWidth(), blurView.getHeight());
        Size scaled = sizeScaler.scale(original);
        if (cachedBitmap == null || cachedBitmap.getWidth() != scaled.width || cachedBitmap.getHeight() != scaled.height) {
            cachedBitmap = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888);
        }
        Canvas softwareCanvas = new Canvas(cachedBitmap);

        softwareCanvas.save();
        setupCanvasMatrix(softwareCanvas, original, scaled);
        if (frameClearDrawable != null) {
            frameClearDrawable.draw(canvas);
        }
        try {
            target.draw(softwareCanvas);
        } catch (Exception e) {
            // Can potentially fail on rendering Hardware Bitmaps or something like that
            Log.e("BlurView", "Error during snapshot capturing", e);
        }
        softwareCanvas.restore();

        if (fallbackBlur == null) {
            fallbackBlur = new RenderScriptBlur(blurView.getContext());
        }
        fallbackBlur.blur(cachedBitmap, blurRadius);
        canvas.save();
        canvas.scale((float) original.width / scaled.width, (float) original.height / scaled.height);
        fallbackBlur.render(canvas, cachedBitmap);
        canvas.restore();
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayColor != Color.TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
    }

    /**
     * Set up matrix to draw starting from blurView's position
     */
    private void setupCanvasMatrix(Canvas canvas, Size targetSize, Size scaledSize) {
        // https://github.com/Dimezis/BlurView/issues/128
        float scaleFactorH = (float) targetSize.height / scaledSize.height;
        float scaleFactorW = (float) targetSize.width / scaledSize.width;

        float scaledLeftPosition = -getLeft() / scaleFactorW;
        float scaledTopPosition = -getTop() / scaleFactorH;

        canvas.translate(scaledLeftPosition, scaledTopPosition);
        canvas.scale(1 / scaleFactorW, 1 / scaleFactorH);
    }

    private int getTop() {
        return blurViewLocation[1] - targetLocation[1];
    }

    private int getLeft() {
        return blurViewLocation[0] - targetLocation[0];
    }

    @Override
    public void updateBlurViewSize() {
        // No-op, the size is updated in draw method, it's cheap and not called frequently
        //todo applyBlur();
    }

    @Override
    public void destroy() {
        blurNode.discardDisplayList();
        if (fallbackBlur != null) {
            fallbackBlur.destroy();
            fallbackBlur = null;
        }
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.enabled = enabled;
        blurView.invalidate();
        return this;
    }

    @Override
    public BlurViewFacade setBlurAutoUpdate(boolean enabled) {
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
        }
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        applyBlur();
        return this;
    }

    private void applyBlur() {
        // scaleFactor is only used to increase the blur radius
        // because RenderEffect already scales down the snapshot when needed.
        float realBlurRadius = blurRadius * scaleFactor;
        RenderEffect blur = RenderEffect.createBlurEffect(realBlurRadius, realBlurRadius, Shader.TileMode.CLAMP);

        if (gradientDirection != BlurView.GRADIENT_NONE && blurView.getWidth() > 0 && blurView.getHeight() > 0) {
            int w = blurView.getWidth();
            int h = blurView.getHeight();
            Shader gradient = gradientCache.getShader(w, h);
            if (gradient != null) {
                RenderEffect mask = RenderEffect.createShaderEffect(gradient);
                blur = RenderEffect.createBlendModeEffect(blur, mask, BlendMode.DST_IN);
            }
        }

        blurNode.setRenderEffect(blur);
    }

    private class GradientCache {
        @Nullable
        private Shader shader;
        int w;
        int h;
        int left;
        int top;
        int direction = -1;

        @Nullable
        Shader getShader(int w, int h) {
            if (gradientDirection == BlurView.GRADIENT_NONE) {
                shader = null;
                return null;
            }

            int currentLeft = getLeft();
            int currentTop = getTop();

            // Check if cache is valid
            if (isCacheValid(w, h, currentLeft, currentTop, gradientDirection)) {
                return shader;
            }

            // Cache miss, create new shader
            // Colors: Opaque to Transparent
            // Opaque keeps the blur, Transparent removes it (shows underlying sharp content)
            int c1 = Color.BLACK;
            int c2 = Color.TRANSPARENT;

            float left = currentLeft;
            float top = currentTop;
            float right = left + w;
            float bottom = top + h;

            Shader shader = switch (gradientDirection) {
                case BlurView.GRADIENT_TOP_TO_BOTTOM ->
                    // Top: Blur (Opaque), Bottom: Sharp (Transparent)
                        new LinearGradient(0, top, 0, bottom, c1, c2, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_BOTTOM_TO_TOP -> new LinearGradient(0, bottom, 0, top, c1, c2, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_LEFT_TO_RIGHT -> new LinearGradient(left, 0, right, 0, c1, c2, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_RIGHT_TO_LEFT -> new LinearGradient(right, 0, left, 0, c1, c2, Shader.TileMode.CLAMP);
                default -> null;
            };

            // Update cache
            update(shader, w, h, currentLeft, currentTop, gradientDirection);

            return shader;
        }

        private boolean isCacheValid(int w, int h, int left, int top, int direction) {
            return shader != null &&
                    this.w == w &&
                    this.h == h &&
                    this.left == left &&
                    this.top == top &&
                    this.direction == direction;
        }

        private void update(Shader shader, int w, int h, int left, int top, int direction) {
            this.shader = shader;
            this.w = w;
            this.h = h;
            this.left = left;
            this.top = top;
            this.direction = direction;
        }
    }

    @Override
    public BlurViewFacade setBlurGradient(int direction) {
        if (this.gradientDirection != direction) {
            this.gradientDirection = direction;
            applyBlur();
        }
        return this;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }

    void updateRotation(float rotation) {
        blurNode.setRotationZ(-rotation);
    }

    public void updateScaleX(float scaleX) {
        blurNode.setScaleX(1 / scaleX);
    }

    public void updateScaleY(float scaleY) {
        blurNode.setScaleY(1 / scaleY);
    }

    private void saveOnScreenLocation() {
        target.getLocationOnScreen(targetLocation);
        blurView.getLocationOnScreen(blurViewLocation);
    }
}
