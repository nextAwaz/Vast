package com.volcano.internal;

/**
 * 弃用的数学类 - 建议使用运算符替代
 */
public class DeprecatedMath {
    public static int add(int a, int b) {
        System.err.println("Warning: Math.add is deprecated, use + operator instead");
        return a + b;
    }

    public static int sub(int a, int b) {
        System.err.println("Warning: Math.sub is deprecated, use - operator instead");
        return a - b;
    }

    public static int mul(int a, int b) {
        System.err.println("Warning: Math.mul is deprecated, use * operator instead");
        return a * b;
    }

    public static int div(int a, int b) {
        System.err.println("Warning: Math.div is deprecated, use / operator instead");
        return b != 0 ? a / b : 0;
    }

    public static int mod(int a, int b) {
        System.err.println("Warning: Math.mod is deprecated, use % operator instead");
        return a % b;
    }

    public static boolean gt(int a, int b) {
        System.err.println("Warning: Math.gt is deprecated, use > operator instead");
        return a > b;
    }

    public static boolean lt(int a, int b) {
        System.err.println("Warning: Math.lt is deprecated, use < operator instead");
        return a < b;
    }

    public static boolean eq(Object a, Object b) {
        System.err.println("Warning: Math.eq is deprecated, use == operator instead");
        return a == b || (a != null && a.equals(b));
    }

    public static int random(int max) {
        return (int) (Math.random() * max);
    }

    public static double sqrt(double value) {
        return Math.sqrt(value);
    }

    public static double pow(double base, double exponent) {
        return Math.pow(base, exponent);
    }
}