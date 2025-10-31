package com.vast.internal;

import java.util.List;
import java.util.Set;

/**
 * Vast异常总类
 * 包含所有Vast特定异常的静态内部类定义
 */
public class VastExceptions {

    /**
     * Vast运行时异常基类
     * 所有Vast特定异常的父类
     */
    public static class VastRuntimeException extends RuntimeException {
        public VastRuntimeException() {
            super();
        }

        public VastRuntimeException(String message) {
            super(message);
        }

        public VastRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }

        public VastRuntimeException(Throwable cause) {
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

    /**
     * 类型不匹配异常
     */
    public static class TypeMismatchException extends VastRuntimeException {
        public TypeMismatchException(String variableName, String expectedType, String actualType, int lineNumber) {
            super("Type mismatch for variable '" + variableName +
                    "': expected " + expectedType + ", but got " + actualType +
                    " at line " + lineNumber);
        }

        public TypeMismatchException(String variableName, String expectedType, String actualType,
                                     int lineNumber, int columnNumber) {
            super("Type mismatch for variable '" + variableName +
                    "': expected " + expectedType + ", but got " + actualType +
                    " at line " + lineNumber + ", column " + columnNumber);
        }
    }

    /**
     * 指代不明异常
     * 当省略类名调用静态方法但存在多个同名方法时抛出
     */
    public static class AmbiguousReferenceException extends VastRuntimeException {
        public AmbiguousReferenceException(String methodName, Set<String> conflictingClasses) {
            super("Ambiguous reference to method '" + methodName +
                    "'. It exists in multiple classes: " + conflictingClasses);
        }

        public AmbiguousReferenceException(String methodName, Set<String> conflictingClasses,
                                           int lineNumber, int columnNumber) {
            super("Ambiguous reference to method '" + methodName +
                    "' at line " + lineNumber + ", column " + columnNumber +
                    ". It exists in multiple classes: " + conflictingClasses);
        }

        public static AmbiguousReferenceException forMethod(String methodName, Set<String> classes) {
            return new AmbiguousReferenceException(methodName, classes);
        }

        public static AmbiguousReferenceException forMethod(String methodName, Set<String> classes,
                                                            int lineNumber, int columnNumber) {
            return new AmbiguousReferenceException(methodName, classes, lineNumber, columnNumber);
        }
    }

    /**
     * 参数传递异常
     * 通过use语句传参数时出现的异常总类
     */
    public static class PassParameterException extends VastRuntimeException {
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

    /**
     * 无法更改异常
     * 当尝试给final修饰的变量重新赋值时抛出
     */
    public static class CannotBeChanged extends PassParameterException {
        public CannotBeChanged() {
            super("Cannot change final variable");
        }

        public CannotBeChanged(String message) {
            super(message);
        }

        public CannotBeChanged(String message, Throwable cause) {
            super(message, cause);
        }

        // 使用静态工厂方法替代重复的构造函数
        public static CannotBeChanged forVariable(String variableName) {
            return new CannotBeChanged("Variable '" + variableName + "' is final and cannot be reassigned");
        }

        public static CannotBeChanged forField(String className, String fieldName) {
            return new CannotBeChanged("Field '" + fieldName + "' in class '" + className + "' is final and cannot be modified");
        }
    }

    /**
     * 耗尽资源异常
     * 当没有足够的算力来运行脚本时抛出
     */
    public static class ExhaustedResourcesException extends VastRuntimeException {
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

    /**
     * 计算异常
     * 在进行代数运算时出现的异常
     */
    public static class MathError extends VastRuntimeException {
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

        // 新增带行号和列号的构造函数
        public MathError(String message, int lineNumber, int columnNumber) {
            super("Math error at line " + lineNumber + ", column " + columnNumber + ": " + message);
        }

        // 常见的数学异常
        public static MathError divisionByZero() {
            return new MathError("Division by zero is undefined");
        }

        public static MathError divisionByZero(int lineNumber, int columnNumber) {
            return new MathError("Division by zero is undefined", lineNumber, columnNumber);
        }

        public static MathError invalidSquareRoot(double value) {
            return new MathError("Cannot calculate square root of negative number: " + value);
        }

        public static MathError invalidSquareRoot(double value, int lineNumber, int columnNumber) {
            return new MathError("Cannot calculate square root of negative number: " + value, lineNumber, columnNumber);
        }

        public static MathError overflow(String operation) {
            return new MathError("Arithmetic overflow in operation: " + operation);
        }

        public static MathError overflow(String operation, int lineNumber, int columnNumber) {
            return new MathError("Arithmetic overflow in operation: " + operation, lineNumber, columnNumber);
        }

        public static MathError invalidLogarithm(double value) {
            return new MathError("Cannot calculate logarithm of non-positive number: " + value);
        }

        public static MathError invalidLogarithm(double value, int lineNumber, int columnNumber) {
            return new MathError("Cannot calculate logarithm of non-positive number: " + value, lineNumber, columnNumber);
        }
    }



    /**
     * 找不到外置库异常
     * 当通过imp引入不存在的外置库时抛出
     */
    public static class NonExistentExternalLibraryException extends NotGrammarException {
        public NonExistentExternalLibraryException() {
            super("External library not found");
        }

        public NonExistentExternalLibraryException(String message) {
            super(message);
        }

        public NonExistentExternalLibraryException(String message, Throwable cause) {
            super(message, cause);
        }

        // 使用静态工厂方法替代重复的构造函数
        public static NonExistentExternalLibraryException forLibrary(String libraryName) {
            return new NonExistentExternalLibraryException("External library '" + libraryName + "' not found or cannot be loaded");
        }

        public static NonExistentExternalLibraryException forLibrary(String libraryName, Throwable cause) {
            return new NonExistentExternalLibraryException("External library '" + libraryName + "' not found or cannot be loaded", cause);
        }
    }

    /**
     * 不存在的对象异常
     * 向外部程序传参数时，声明的接受参数的对象不存在
     */
    public static class NonExistentObject extends PassParameterException {
        public NonExistentObject() {
            super("Target object does not exist");
        }

        public NonExistentObject(String customMessage) {
            super(customMessage);
        }

        public NonExistentObject(String message, Throwable cause) {
            super(message, cause);
        }

        // 移除重复的构造函数，只保留一个带两个String参数的构造函数
        public NonExistentObject(String objectType, String objectName) {
            super(objectType + " '" + objectName + "' does not exist");
        }

        // 为方法不存在的情况创建专门的静态工厂方法
        public static NonExistentObject methodNotFound(String className, String methodName) {
            return new NonExistentObject("Method '" + methodName + "' not found in class '" + className + "'");
        }

        public static NonExistentObject classNotFound(String className) {
            return new NonExistentObject("Class", className);
        }

        public static NonExistentObject variableNotFound(String varName) {
            return new NonExistentObject("Variable", varName);
        }

        public static NonExistentObject fieldNotFound(String className, String fieldName) {
            return new NonExistentObject("Field '" + fieldName + "' not found in class '" + className + "'");
        }
    }

    /**
     * 语法异常
     * 当语句或关键字拼写错误、参数数量不匹配或特殊符号使用错误时抛出
     */
    public static class NotGrammarException extends VastRuntimeException {
        public NotGrammarException() {
            super("Syntax error detected");
        }

        public NotGrammarException(String message) {
            super(message);
        }

        public NotGrammarException(String message, Throwable cause) {
            super(message, cause);
        }

        public NotGrammarException(String statement, int lineNumber) {
            super("Syntax error at line " + lineNumber + ": " + statement);
        }

        public NotGrammarException(String expected, String actual, int lineNumber) {
            super("Syntax error at line " + lineNumber + ": expected '" + expected + "', but found '" + actual + "'");
        }

        // 新增构造函数，用于处理带行号和列号的情况
        public NotGrammarException(String message, int lineNumber, int columnNumber) {
            super("Syntax error at line " + lineNumber + ", column " + columnNumber + ": " + message);
        }

        public NotGrammarException(String expected, String actual, int lineNumber, int columnNumber) {
            super("Syntax error at line " + lineNumber + ", column " + columnNumber + ": expected '" + expected + "', but found '" + actual + "'");
        }

        // 新增构造函数，用于处理两个字符串参数的情况
        public NotGrammarException(String context, String details) {
            super("Syntax error in " + context + ": " + details);
        }

        public static NotGrammarException withSuggestion(String context, String actual,
                                                         List<String> suggestions) {
            StringBuilder message = new StringBuilder();
            message.append("Syntax error in ").append(context).append(": '").append(actual).append("'");

            if (!suggestions.isEmpty()) {
                message.append(". Did you mean: ");
                for (int i = 0; i < suggestions.size(); i++) {
                    if (i > 0) {
                        message.append(i == suggestions.size() - 1 ? " or " : ", ");
                    }
                    message.append("'").append(suggestions.get(i)).append("'");
                }
                message.append("?");
            }

            return new NotGrammarException(message.toString());
        }

        // 参数数量不匹配的异常
        public static NotGrammarException parameterCountMismatch(String methodName, int expected, int actual, int lineNumber) {
            return new NotGrammarException("Parameter count mismatch in '" + methodName + "': expected " + expected +
                    ", but got " + actual + " at line " + lineNumber);
        }

        public static NotGrammarException parameterCountMismatch(String className, String methodName, int expected, int actual, int lineNumber) {
            return new NotGrammarException("Parameter count mismatch in " + className + "." + methodName + "(): expected " +
                    expected + ", but got " + actual + " at line " + lineNumber);
        }

        // 外部传参数数量不匹配
        public static NotGrammarException externalParameterMismatch(String operation, int expected, int actual) {
            return new NotGrammarException(operation + " parameter mismatch: expected " + expected +
                    " parameters, but got " + actual);
        }

        // 特殊符号使用错误 - 添加行号参数
        public static NotGrammarException invalidSymbolUsage(String symbol, String context, int lineNumber) {
            return new NotGrammarException("Invalid use of symbol '" + symbol + "' in " + context + " at line " + lineNumber);
        }

        public static NotGrammarException invalidSymbolUsage(String symbol, String context, String correctUsage, int lineNumber) {
            return new NotGrammarException("Invalid use of symbol '" + symbol + "' in " + context +
                    ". Correct usage: " + correctUsage + " at line " + lineNumber);
        }

        // 专门针对 :: 符号的异常
        public static NotGrammarException invalidDoubleColonUsage(String context, int lineNumber) {
            return new NotGrammarException("Invalid use of '::' symbol in " + context + " at line " + lineNumber +
                    ". '::' should be used in input statements like: input(var a :: nextLine)");
        }

        public static NotGrammarException doubleColonNotInInput(int lineNumber) {
            return new NotGrammarException("'::' symbol can only be used in input statements at line " + lineNumber);
        }

        // 括号不匹配异常
        public static NotGrammarException parenthesisMismatch(String context, int lineNumber) {
            return new NotGrammarException("Parenthesis mismatch in " + context + " at line " + lineNumber);
        }

        public static NotGrammarException missingClosingParenthesis(String context, int lineNumber) {
            return new NotGrammarException("Missing closing parenthesis in " + context + " at line " + lineNumber);
        }

        public static NotGrammarException missingOpeningParenthesis(String context, int lineNumber) {
            return new NotGrammarException("Missing opening parenthesis in " + context + " at line " + lineNumber);
        }

        // 语句结构错误
        public static NotGrammarException invalidStatementStructure(String statementType, String details, int lineNumber) {
            return new NotGrammarException("Invalid " + statementType + " statement structure at line " + lineNumber +
                    ": " + details);
        }

        // 关键字使用错误
        public static NotGrammarException keywordMisuse(String keyword, String context, int lineNumber) {
            return new NotGrammarException("Misuse of keyword '" + keyword + "' in " + context + " at line " + lineNumber);
        }

        // 新增：没有行号信息的版本（用于运行时解析）
        public static NotGrammarException invalidSymbolUsage(String symbol, String context) {
            return new NotGrammarException("Invalid use of symbol '" + symbol + "' in " + context);
        }

        public static NotGrammarException invalidSymbolUsage(String symbol, String context, String correctUsage) {
            return new NotGrammarException("Invalid use of symbol '" + symbol + "' in " + context +
                    ". Correct usage: " + correctUsage);
        }
    }

    /**
     * 空令牌异常
     * 当尝试访问或操作空令牌时抛出
     */
    public static class NullTokenException extends VastRuntimeException {
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

    /**
     * 未知异常
     * 虚拟机无法识别的异常类型
     */
    public static class UnknownVastException extends VastRuntimeException {
        private final String originalExceptionType;

        public UnknownVastException() {
            super("Unknown exception occurred in Vast VM");
            this.originalExceptionType = "Unknown";
        }

        public UnknownVastException(String message) {
            super(message);
            this.originalExceptionType = "Unknown";
        }

        public UnknownVastException(String message, Throwable cause) {
            super(message, cause);
            this.originalExceptionType = cause != null ? cause.getClass().getSimpleName() : "Unknown";
        }

        public UnknownVastException(Throwable cause) {
            super("Unknown exception: " + (cause != null ? cause.getMessage() : "null"), cause);
            this.originalExceptionType = cause != null ? cause.getClass().getSimpleName() : "Unknown";
        }

        public String getOriginalExceptionType() {
            return originalExceptionType;
        }
    }
}