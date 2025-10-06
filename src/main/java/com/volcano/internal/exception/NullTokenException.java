package com.volcano.internal.exception;

/**
 * 空令牌异常
 * 当尝试访问或操作空令牌时抛出
 */
public class NullTokenException extends VolcanoRuntimeException {
    public NullTokenException() {
        super("Null token encountered");
    }

    public NullTokenException(String message) {
        super(message);
    }

    public NullTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public NullTokenException(String token, String context) {
        super("Null token '" + token + "' in context: " + context);
    }
}