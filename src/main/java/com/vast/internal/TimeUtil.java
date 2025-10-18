package com.vast.internal;

import java.util.Date;

//时间操作内置库
public class TimeUtil {
    public static void wait(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String now() {
        return new Date().toString();
    }

    public static long timestamp() {
        return System.currentTimeMillis();
    }

    public static String format(long timestamp) {
        return new Date(timestamp).toString();
    }
}