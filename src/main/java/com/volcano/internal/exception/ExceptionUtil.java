package com.volcano.internal.exception;

/**
 * 异常处理工具类
 */
public class ExceptionUtil {

    /**
     * 安全执行操作，捕获异常并转换为Volcano异常
     */
    public static <T> T safeExecute(VolcanoOperation<T> operation, String context) {
        try {
            return operation.execute();
        } catch (VolcanoRuntimeException e) {
            throw e; // 重新抛出Volcano特定异常
        } catch (Exception e) {
            throw new UnknownVolcanoException("Error in " + context, e);
        }
    }

    /**
     * 验证参数数量
     */
    public static void validateParameterCount(int expected, int actual, String operation, int lineNumber) {
        if (expected != actual) {
            throw NotGrammarException.parameterCountMismatch(operation, expected, actual, lineNumber);
        }
    }

    /**
     * 验证对象不为空
     */
    public static <T> T requireNonNull(T obj, String name, String context) {
        if (obj == null) {
            throw new NullTokenException(name, context);
        }
        return obj;
    }

    /**
     * 验证字符串不为空
     */
    public static String requireNonEmpty(String str, String name, String context) {
        if (str == null || str.trim().isEmpty()) {
            throw new NullTokenException(name + " cannot be empty", context);
        }
        return str;
    }

    /**
     * 操作接口
     */
    @FunctionalInterface
    public interface VolcanoOperation<T> {
        T execute() throws Exception;
    }
}