package com.volcano.internal.exception;

/**
 * 语法异常
 * 当语句或关键字拼写错误、参数数量不匹配或特殊符号使用错误时抛出
 */
public class NotGrammarException extends VolcanoRuntimeException {
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