package com.volcano.internal.exception;

/**
 * 参数传递异常
 * 通过do或give语句传参数时出现的异常总类
 */
public class PassParameterException extends VolcanoRuntimeException {
    public PassParameterException() {
        super("Parameter passing error");
    }

    public PassParameterException(String message) {
        super(message);
    }

    public PassParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    public PassParameterException(String operation, String details) {
        super("Parameter error in " + operation + ": " + details);
    }

    public PassParameterException(String className, String methodName, String parameter) {
        super("Parameter '" + parameter + "' error in " + className + "." + methodName + "()");
    }
}