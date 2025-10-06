package com.volcano.internal.exception;

/**
 * 计算异常
 * 在进行代数运算时出现的异常
 */
public class MathError extends VolcanoRuntimeException {
    public MathError() {
        super("Math calculation error");
    }

    public MathError(String message) {
        super(message);
    }

    public MathError(String message, Throwable cause) {
        super(message, cause);
    }

    public MathError(String operation, String reason) {
        super("Math error in operation '" + operation + "': " + reason);
    }

    // 常见的数学异常
    public static MathError divisionByZero() {
        return new MathError("Division by zero is undefined");
    }

    public static MathError invalidSquareRoot(double value) {
        return new MathError("Cannot calculate square root of negative number: " + value);
    }

    public static MathError overflow(String operation) {
        return new MathError("Arithmetic overflow in operation: " + operation);
    }

    public static MathError invalidLogarithm(double value) {
        return new MathError("Cannot calculate logarithm of non-positive number: " + value);
    }
}