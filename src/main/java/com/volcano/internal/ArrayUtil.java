package com.volcano.internal;

/**
 * 数组操作内置库
 */
public class ArrayUtil {
    public static int length(Object[] array) {
        return array != null ? array.length : 0;
    }

    public static boolean contains(Object[] array, Object value) {
        if (array == null) return false;
        for (Object item : array) {
            if (eq(item, value)) return true;
        }
        return false;
    }

    public static Object[] create(int size) {
        return new Object[size];
    }

    public static Object get(Object[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return null;
        }
        return array[index];
    }

    public static void set(Object[] array, int index, Object value) {
        if (array != null && index >= 0 && index < array.length) {
            array[index] = value;
        }
    }

    private static boolean eq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}