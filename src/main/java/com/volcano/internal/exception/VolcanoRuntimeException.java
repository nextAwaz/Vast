package com.volcano.internal.exception;

/**
 * Volcano运行时异常基类
 * 所有Volcano特定异常的父类
 */
public class VolcanoRuntimeException extends RuntimeException {
    public VolcanoRuntimeException() {
        super();
    }

    public VolcanoRuntimeException(String message) {
        super(message);
    }

    public VolcanoRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public VolcanoRuntimeException(Throwable cause) {
        super(cause);
    }

    /**
     * 获取异常的详细上下文信息
     */
    public String getDetailedMessage() {
        return getClass().getSimpleName() + ": " + getMessage();
    }

    /**
     * 获取用户友好的错误信息
     */
    public String getUserFriendlyMessage() {
        String simpleName = getClass().getSimpleName();
        String message = getMessage();
        return "[" + simpleName + "] " + (message != null ? message : "An error occurred");
    }
}