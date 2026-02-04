package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import android.view.ViewTreeObserver;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
/**
 * Blur Controller that handles all blur logic for the attached View.
 * It honors View size changes, View animation and Visibility changes.
 * <p>
 * The basic idea is to draw the view hierarchy on a bitmap, excluding the attached View,
 * then blur and draw it on the system Canvas.
 * <p>
 * It uses {@link ViewTreeObserver.OnPreDrawListener} to detect when
 * blur should be updated.
 * <p>
 */
public final class PreDrawBlurController implements BlurController {

    @ColorInt
    public static final int TRANSPARENT = 0;

    private float blurRadius = DEFAULT_BLUR_RADIUS;

    private final BlurAlgorithm blurAlgorithm;
    private final float scaleFactor;
    private final boolean applyNoise;
    private BlurViewCanvas internalCanvas;
    private Bitmap internalBitmap;

    @SuppressWarnings("WeakerAccess")
    final View blurView;
    private int overlayColor;
    private int overlayStartColor = Color.TRANSPARENT;
    private int overlayEndColor = Color.TRANSPARENT;
    private int overlayGradientDirection = BlurView.GRADIENT_NONE;
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ViewGroup rootView;
    private final int[] rootLocation = new int[2];
    private final int[] blurViewLocation = new int[2];
    private int gradientDirection = BlurView.GRADIENT_NONE;
    private final OverlayGradientCache overlayGradientCache = new OverlayGradientCache();

    private final ViewTreeObserver.OnPreDrawListener drawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            // Not invalidating a View here, just updating the Bitmap.
            // This relies on the HW accelerated bitmap drawing behavior in Android
            // If the bitmap was drawn on HW accelerated canvas, it holds a reference to it and on next
            // drawing pass the updated content of the bitmap will be rendered on the screen
            updateBlur();
            return true;
        }
    };

    private boolean blurEnabled = true;
    private boolean initialized;

    @Nullable
    private Drawable frameClearDrawable;

    /**
     * @param blurView    View which will draw it's blurred underlying content
     * @param rootView    Root View where blurView's underlying content starts drawing.
     *                    Can be Activity's root content layout (android.R.id.content)
     * @param algorithm   sets the blur algorithm
     * @param scaleFactor a scale factor to downscale the view snapshot before blurring.
     *                    Helps achieving stronger blur and potentially better performance at the expense of blur precision.
     * @param applyNoise  optional blue noise texture over the blurred content to make it look more natural. True by default.
     */
    public PreDrawBlurController(@NonNull View blurView,
                                 @NonNull ViewGroup rootView,
                                 @ColorInt int overlayColor,
                                 BlurAlgorithm algorithm,
                                 float scaleFactor,
                                 boolean applyNoise) {
        this.rootView = rootView;
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.blurAlgorithm = algorithm;
        this.scaleFactor = scaleFactor;
        this.applyNoise = applyNoise;

        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        init(measuredWidth, measuredHeight);
    }

    @SuppressWarnings("WeakerAccess")
    void init(int measuredWidth, int measuredHeight) {
        setBlurAutoUpdate(true);
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            // Will be initialized later when the View reports a size change
            blurView.setWillNotDraw(true);
            return;
        }

        blurView.setWillNotDraw(false);
        SizeScaler.Size bitmapSize = sizeScaler.scale(measuredWidth, measuredHeight);
        internalBitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, blurAlgorithm.getSupportedBitmapConfig());
        internalCanvas = new BlurViewCanvas(internalBitmap);
        initialized = true;
        // Usually it's not needed, because `onPreDraw` updates the blur anyway.
        // But it handles cases when the PreDraw listener is attached to a different Window, for example
        // when the BlurView is in a Dialog window, but the root is in the Activity.
        // Previously it was done in `draw`, but it was causing potential side effects and Jetpack Compose crashes
        updateBlur();
    }

    @SuppressWarnings("WeakerAccess")
    void updateBlur() {
        if (!blurEnabled || !initialized) {
            return;
        }

        if (frameClearDrawable == null) {
            internalBitmap.eraseColor(Color.TRANSPARENT);
        } else {
            frameClearDrawable.draw(internalCanvas);
        }

        internalCanvas.save();
        setupInternalCanvasMatrix();
        try {
            rootView.draw(internalCanvas);
        } catch (Exception e) {
            // Can potentially fail on rendering Hardware Bitmaps or something like that
            Log.e("BlurView", "Error during snapshot capturing", e);
        }
        internalCanvas.restore();

        blurAndSave();
    }

    /**
     * Set up matrix to draw starting from blurView's position
     */
    private void setupInternalCanvasMatrix() {
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);

        int left = blurViewLocation[0] - rootLocation[0];
        int top = blurViewLocation[1] - rootLocation[1];

        // https://github.com/Dimezis/BlurView/issues/128
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        float scaledLeftPosition = -left / scaleFactorW;
        float scaledTopPosition = -top / scaleFactorH;

        internalCanvas.translate(scaledLeftPosition, scaledTopPosition);
        internalCanvas.scale(1 / scaleFactorW, 1 / scaleFactorH);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!blurEnabled || !initialized) {
            return true;
        }
        // Not blurring itself or other BlurViews to not cause recursive draw calls
        // Related: https://github.com/Dimezis/BlurView/issues/110
        if (canvas instanceof BlurViewCanvas) {
            return false;
        }

        // https://github.com/Dimezis/BlurView/issues/128
        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();

        canvas.save();
        // Don't draw outside of the BlurView bounds if parent has clipChildren = false
        canvas.clipRect(0f, 0f, blurView.getWidth(), blurView.getHeight());
        canvas.save();
        canvas.scale(scaleFactorW, scaleFactorH);
        blurAlgorithm.render(canvas, internalBitmap);
        // restore scale so we don't upscale the noise texture
        canvas.restore();
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayStartColor != Color.TRANSPARENT || overlayEndColor != Color.TRANSPARENT) {
            int w = blurView.getWidth();
            int h = blurView.getHeight();
            if (w > 0 && h > 0) {
                // If gradient direction is NONE, we can't draw a meaningful gradient unless we default it.
                // Assuming we default to TOP_TO_BOTTOM if NONE, OR we don't draw.
                // But for "overlay" it might be better to just draw solid startColor if NONE.
                // However, user asked for gradient. Let's use TOP_TO_BOTTOM as default fallback if needed,
                // or just rely on gradientDirection being set.
                // Given the context of "progressive blur", we assume direction matches blur direction.
                Shader shader = overlayGradientCache.getShader(w, h, overlayStartColor, overlayEndColor, overlayGradientDirection);
                overlayPaint.setShader(shader);
                canvas.drawRect(0, 0, w, h, overlayPaint);
            }
        } else if (overlayColor != TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        // restore clip rect
        canvas.restore();
        return true;
    }

    private void blurAndSave() {
        internalBitmap = blurAlgorithm.blur(internalBitmap, blurRadius);
        if (!blurAlgorithm.canModifyBitmap()) {
            internalCanvas.setBitmap(internalBitmap);
        }
    }

    @Override
    public void updateBlurViewSize() {
        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        init(measuredWidth, measuredHeight);
    }

    @Override
    public void destroy() {
        setBlurAutoUpdate(false);
        blurAlgorithm.destroy();
        initialized = false;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        setBlurAutoUpdate(enabled);
        blurView.invalidate();
        return this;
    }

    public BlurViewFacade setBlurAutoUpdate(final boolean enabled) {
        rootView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            rootView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            // Track changes in the blurView window too, for example if it's in a bottom sheet dialog
            if (rootView.getWindowId() != blurView.getWindowId()) {
                blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            }
        }
        return this;
    }

    @Override
    public BlurViewFacade setBlurGradient(int direction) {
        this.gradientDirection = direction;
        return this;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            // Clear gradient colors to prefer solid color if this is called last
            this.overlayStartColor = Color.TRANSPARENT;
            this.overlayEndColor = Color.TRANSPARENT;
            blurView.invalidate();
        }
        return this;
    }

    @Override
    public BlurViewFacade setOverlayGradientColor(int startColor, int endColor, int direction) {
        if (this.overlayStartColor != startColor || this.overlayEndColor != endColor || this.overlayGradientDirection != direction) {
            this.overlayStartColor = startColor;
            this.overlayEndColor = endColor;
            this.overlayGradientDirection = direction;
            blurView.invalidate();
        }
        return this;
    }

    private static class OverlayGradientCache {
        @Nullable
        private Shader shader;
        int w;
        int h;
        int startColor;
        int endColor;
        int direction = -1;

        @Nullable
        Shader getShader(int w, int h, int startColor, int endColor, int gradientDirection) {
            // Check if cache is valid
            if (isCacheValid(w, h, startColor, endColor, gradientDirection)) {
                return shader;
            }

            // Cache miss, create new shader
            float right = w;
            float bottom = h;

            Shader shader = switch (gradientDirection) {
                case BlurView.GRADIENT_TOP_TO_BOTTOM -> new LinearGradient(0, 0, 0, bottom, startColor, endColor, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_BOTTOM_TO_TOP -> new LinearGradient(0, bottom, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_LEFT_TO_RIGHT -> new LinearGradient(0, 0, right, 0, startColor, endColor, Shader.TileMode.CLAMP);
                case BlurView.GRADIENT_RIGHT_TO_LEFT -> new LinearGradient(right, 0, 0, 0, startColor, endColor, Shader.TileMode.CLAMP);
                default -> {
                    // Fallback or default for NONE?
                    // Let's assume Top-to-Bottom as a sensible default for an explicit gradient request
                    // Or maybe we should just return null?
                    // User said "linear transparency function", implying vertical usually.
                    yield new LinearGradient(0, 0, 0, bottom, startColor, endColor, Shader.TileMode.CLAMP);
                }
            };

            // Update cache
            update(shader, w, h, startColor, endColor, gradientDirection);

            return shader;
        }

        private boolean isCacheValid(int w, int h, int startColor, int endColor, int direction) {
            return shader != null &&
                    this.w == w &&
                    this.h == h &&
                    this.startColor == startColor &&
                    this.endColor == endColor &&
                    this.direction == direction;
        }

        private void update(Shader shader, int w, int h, int startColor, int endColor, int direction) {
            this.shader = shader;
            this.w = w;
            this.h = h;
            this.startColor = startColor;
            this.endColor = endColor;
            this.direction = direction;
        }
    }
}
