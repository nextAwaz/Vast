package com.vast.internal;

// 运算符支持内置库
public class Ops {
    // 逻辑运算方法
    public static boolean and(boolean a, boolean b) { return a && b; }
    public static boolean or(boolean a, boolean b) { return a || b; }
    public static boolean xor(boolean a, boolean b) { return a ^ b; }
    public static boolean not(boolean a) { return !a; }

    // 字符串操作
    public static String concat(String a, String b) { return a + b; }

    public static String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    public static boolean equals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    public static boolean notEquals(Object a, Object b) {
        return !equals(a, b);
    }
}