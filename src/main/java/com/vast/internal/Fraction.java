package com.vast.internal;

/**
 * 分数类，用于表示分数运算的结果
 */
public class Fraction {
    private final int numerator;
    private final int denominator;
    private boolean isPermanent;

    public Fraction(int numerator, int denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("Denominator cannot be zero");
        }

        // 使用局部变量进行计算
        int gcd = gcd(Math.abs(numerator), Math.abs(denominator));
        int simplifiedNumerator = numerator / gcd;
        int simplifiedDenominator = denominator / gcd;

        // 确保分母为正
        if (simplifiedDenominator < 0) {
            simplifiedNumerator = -simplifiedNumerator;
            simplifiedDenominator = -simplifiedDenominator;
        }

        // 一次性赋值给 final 字段
        this.numerator = simplifiedNumerator;
        this.denominator = simplifiedDenominator;
    }

    // 从数值创建分数
    public static Fraction fromNumber(Object number) {
        if (number instanceof Integer) {
            return new Fraction((Integer) number, 1);
        } else if (number instanceof Double) {
            // 将浮点数转换为分数（近似）
            double value = (Double) number;
            return approximateFraction(value);
        } else {
            throw new IllegalArgumentException("Cannot convert " + number + " to fraction");
        }
    }

    // 最大公约数
    private static int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    // 将浮点数近似为分数
    private static Fraction approximateFraction(double value) {
        final double EPSILON = 1e-6;
        int sign = value < 0 ? -1 : 1;
        value = Math.abs(value);

        // 处理整数情况
        if (Math.abs(value - Math.round(value)) < EPSILON) {
            return new Fraction(sign * (int) Math.round(value), 1);
        }

        // 连分数展开
        int n0 = 0, d0 = 1;
        int n1 = 1, d1 = 0;
        double x = value;

        while (true) {
            int a = (int) Math.floor(x);
            int n2 = a * n1 + n0;
            int d2 = a * d1 + d0;

            if (d2 == 0) break;

            double approx = (double) n2 / d2;
            if (Math.abs(approx - value) < EPSILON) {
                return new Fraction(sign * n2, d2);
            }

            n0 = n1; d0 = d1;
            n1 = n2; d1 = d2;

            if (x - a < EPSILON) break;
            x = 1.0 / (x - a);
        }

        // 如果无法精确表示，返回最接近的分数
        return new Fraction(sign * (int) Math.round(value * 1000000), 1000000);
    }

    public int getNumerator() { return numerator; }
    public int getDenominator() { return denominator; }
    public boolean isPermanent() { return isPermanent; }
    public void setPermanent(boolean permanent) { this.isPermanent = permanent; }

    public double toDouble() {
        return (double) numerator / denominator;
    }

    public int toInt() {
        return numerator / denominator;
    }

    @Override
    public String toString() {
        if (denominator == 1) {
            return String.valueOf(numerator);
        } else {
            return numerator + "/" + denominator;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Fraction fraction = (Fraction) obj;
        return numerator == fraction.numerator && denominator == fraction.denominator;
    }

    @Override
    public int hashCode() {
        return 31 * numerator + denominator;
    }
}