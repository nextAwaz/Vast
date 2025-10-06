package com.volcano.internal.exception;

/**
 * 耗尽资源异常
 * 当没有足够的算力来运行脚本时抛出
 */
public class ExhaustedResourcesException extends VolcanoRuntimeException {
    public ExhaustedResourcesException() {
        super("System resources exhausted");
    }

    public ExhaustedResourcesException(String message) {
        super(message);
    }

    public ExhaustedResourcesException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExhaustedResourcesException(String resourceType, long currentUsage, long limit) {
        super(resourceType + " exhausted: " + currentUsage + "/" + limit + " (usage/limit)");
    }

    // 常见的资源耗尽异常
    public static ExhaustedResourcesException memoryExhausted(long usedMemory, long maxMemory) {
        return new ExhaustedResourcesException("Memory", usedMemory, maxMemory);
    }

    public static ExhaustedResourcesException stackOverflow() {
        return new ExhaustedResourcesException("Stack overflow - too many recursive calls or deep nesting");
    }

    public static ExhaustedResourcesException executionTimeout(long timeoutMs) {
        return new ExhaustedResourcesException("Script execution timeout after " + timeoutMs + "ms");
    }
}