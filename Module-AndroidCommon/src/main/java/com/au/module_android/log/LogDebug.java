package com.au.module_android.log;

import com.au.module_android.Globals;

import java.io.File;

/**
 * 这个类一被接触就立刻初始化。因此不要把别的内容放在这里。
 */
public class LogDebug {
    public static final boolean ALWAYS_FILE_LOG
            = new File(
            Globals.INSTANCE.getGoodFilesDir().getAbsolutePath() + File.separator + "hasAuLogFileMask")
            .exists();
    public static final boolean ALWAYS_LOG_DEBUG
            = new File(Globals.INSTANCE.getGoodFilesDir().getAbsolutePath() + File.separator + "hasAuDebugLogMask")
            .exists();
}