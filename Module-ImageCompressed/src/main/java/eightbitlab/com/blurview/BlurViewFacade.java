package eightbitlab.com.blurview;

import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public interface BlurViewFacade {

    /**
     * Enables/disables the blur. Enabled by default
     *
     * @param enabled true to enable, false otherwise
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setBlurEnabled(boolean enabled);

    /**
     * Can be used to stop blur auto update or resume if it was stopped before.
     * Enabled by default.
     *
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setBlurAutoUpdate(boolean enabled);

    /**
     * @param frameClearDrawable sets the drawable to draw before view hierarchy.
     *                           Can be used to draw Activity's window background if your root layout doesn't provide any background
     *                           Optional, by default frame is cleared with a transparent color.
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable);

    /**
     * @param radius sets the blur radius. The real blur radius is radius * scaleFactor.
     *               Default value is {@link BlurController#DEFAULT_BLUR_RADIUS}
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setBlurRadius(float radius);

    /**
     * Sets the color overlay to be drawn on top of blurred content
     *
     * @param overlayColor int color
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setOverlayColor(@ColorInt int overlayColor);

    /**
     * Sets the direction of the progressive blur gradient.
     * The blur will fade out in the specified direction.
     *
     * @param direction one of {@link BlurView#GRADIENT_TOP_TO_BOTTOM}, {@link BlurView#GRADIENT_BOTTOM_TO_TOP},
     *                  {@link BlurView#GRADIENT_LEFT_TO_RIGHT}, {@link BlurView#GRADIENT_RIGHT_TO_LEFT},
     *                  or {@link BlurView#GRADIENT_NONE}
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setBlurGradient(int direction);

    /**
     * Sets the overlay color gradient.
     * If set, this gradient will be drawn on top of the blurred content.
     *
     * @param startColor start color of the gradient
     * @param endColor   end color of the gradient
     * @param direction  gradient direction, see {@link BlurView#GRADIENT_TOP_TO_BOTTOM} etc.
     * @return {@link BlurViewFacade}
     */
    BlurViewFacade setOverlayGradientColor(@ColorInt int startColor, @ColorInt int endColor, int direction);
}
