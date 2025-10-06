package com.volcano.internal.exception;

/**
 * 未知异常
 * 虚拟机无法识别的异常类型
 */
public class UnknownVolcanoException extends VolcanoRuntimeException {
    private final String originalExceptionType;

    public UnknownVolcanoException() {
        super("Unknown exception occurred in Volcano VM");
        this.originalExceptionType = "Unknown";
    }

    public UnknownVolcanoException(String message) {
        super(message);
        this.originalExceptionType = "Unknown";
    }

    public UnknownVolcanoException(String message, Throwable cause) {
        super(message, cause);
        this.originalExceptionType = cause != null ? cause.getClass().getSimpleName() : "Unknown";
    }

    public UnknownVolcanoException(Throwable cause) {
        super("Unknown exception: " + (cause != null ? cause.getMessage() : "null"), cause);
        this.originalExceptionType = cause != null ? cause.getClass().getSimpleName() : "Unknown";
    }

    public String getOriginalExceptionType() {
        return originalExceptionType;
    }
}