package com.au.module_android.log;

public class LoggerFactory {
    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getSimpleName());
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public static Logger getLogger() {
        return new Logger(ALogJ.TAG);
    }
}