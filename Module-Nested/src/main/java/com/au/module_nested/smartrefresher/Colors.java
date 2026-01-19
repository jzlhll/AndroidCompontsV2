package com.au.module_nested.smartrefresher;

public final class Colors {
    private static final int COLOR_FIRST = 0xff0099cc;
    public static int[] loadingColors() {
           return new int[]{COLOR_FIRST,0xffff4444,0xff669900,0xffaa66cc,0xffff8800};
    }

    /**
     * 全局设置下拉时的默认颜色
     */
    public static int sPullDownColor = COLOR_FIRST;

    /**
     * 全局设置是否启用随机颜色
     */
    public static boolean sEnableRandomColor = true;
}