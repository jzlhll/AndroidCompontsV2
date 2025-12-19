package com.au.module_android.log;


import com.au.module_android.BuildConfig;

public final class Logger {
    private final String tag;

    private static boolean isTraceEnabled = BuildConfig.DEBUG;

    private static boolean isDebugEnabled = BuildConfig.DEBUG;

    Logger(String tag) {
        this.tag = tag;
    }

    public void debug(String message, Object... args) {
        if (isDebugEnabled) android.util.Log.d(tag, formatMessage(message, args));
    }

    public void d(String message, Object... args) {
        if (isDebugEnabled) android.util.Log.d(tag, formatMessage(message, args));
    }

    public void warn(String message, Object... args) {
        android.util.Log.w(tag, formatMessage(message, args));
    }

    public void warn(String message, Throwable throwable, Object... args) {
        android.util.Log.w(tag, formatMessage(message, args), throwable);
    }

    public void w(String message, Object... args) {
        android.util.Log.w(tag, formatMessage(message, args));
    }

    public void w(String message, Throwable throwable, Object... args) {
        android.util.Log.w(tag, formatMessage(message, args), throwable);
    }

    public void error(String message, Object... args) {
        android.util.Log.e(tag, formatMessage(message, args));
    }

    public void error(String message, Throwable throwable, Object... args) {
        android.util.Log.e(tag, formatMessage(message, args), throwable);
    }

    public void e(String message, Object... args) {
        android.util.Log.e(tag, formatMessage(message, args));
    }

    public void e(String message, Throwable throwable, Object... args) {
        android.util.Log.e(tag, formatMessage(message, args), throwable);
    }

    public void info(String message, Object... args) {
        android.util.Log.i(tag, formatMessage(message, args));
    }

    public void trace(String message, Object... args) {
        if (isTraceEnabled) android.util.Log.d(tag, formatMessage(message, args));
    }

    public boolean isTraceEnabled() {
        return isTraceEnabled;
    }

    public boolean isDebugEnabled() {
        return isDebugEnabled;
    }

    public static void setDebugEnabled(boolean debugEnabled) {
        isDebugEnabled = debugEnabled;
    }

    public static void setTraceEnabled(boolean traceEnabled) {
        isTraceEnabled = traceEnabled;
    }

    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return "Formatting error: " + message;
        }
    }
}
