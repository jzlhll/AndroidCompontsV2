package com.au.module_androidui.dialogs;

import android.graphics.drawable.ColorDrawable;

import androidx.annotation.ColorInt;

public class ColorDrawableUtil {
    @ColorInt
    public static final int WHITE       = 0xcFFFFFFF;

    @ColorInt
    public static int SYSTEM_DIALOG_OVERLAY = 0x60000000;

    public ColorDrawable whiteDrawable() {
        return new ColorDrawable(WHITE);
    }

    public ColorDrawable systemDialogOverlayDrawable() {
        return new ColorDrawable(SYSTEM_DIALOG_OVERLAY);
    }
}