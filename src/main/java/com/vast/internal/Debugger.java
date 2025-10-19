package com.vast.internal;

/**
 * 调试器类 - 统一管理调试输出<p>
 * 三个等级：<p>
 * - basic: 基本模式，只显示错误堆栈追踪<p>
 * - detail: 详细模式，显示基本信息和@符号细节<p>
 * - base: 底层模式，显示所有信息包括AST细节
 */
public class Debugger {
    public enum Level {
        BASIC,      // 基本模式
        DETAIL,     // 详细模式
        BASE        // 底层模式
    }

    private static Debugger instance;
    private Level currentLevel = Level.BASIC;
    private boolean enabled = false;

    private Debugger() {
        // 私有构造函数
    }

    public static Debugger getInstance() {
        if (instance == null) {
            instance = new Debugger();
        }
        return instance;
    }

    public void setLevel(Level level) {
        this.currentLevel = level;
        this.enabled = (level != Level.BASIC);
    }

    public Level getLevel() {
        return currentLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDetailEnabled() {
        return currentLevel == Level.DETAIL || currentLevel == Level.BASE;
    }

    public boolean isBaseEnabled() {
        return currentLevel == Level.BASE;
    }

    // 基本调试输出 - 在所有模式下都显示
    public void logBasic(String message) {
        System.out.println(message);
    }

    // 详细调试输出 - 在detail和base模式下显示
    public void logDetail(String message) {
        if (isDetailEnabled()) {
            System.out.println("@ " + message);
        }
    }

    // 底层调试输出 - 只在base模式下显示
    public void logBase(String message) {
        if (isBaseEnabled()) {
            System.out.println("@ [BASE] " + message);
        }
    }

    // 错误输出 - 在所有模式下都显示
    public void logError(String message) {
        System.err.println("[ERROR] " + message);
    }

    // 警告输出 - 在所有模式下都显示
    public void logWarning(String message) {
        System.out.println("@ [WARNING] " + message);
    }

    // 类型检查相关输出
    public void logTypeCheck(String message) {
        if (isDetailEnabled()) {
            System.out.println("@ [TYPE-CHECK] " + message);
        }
    }

    // 变量相关输出
    public void logVariable(String message) {
        if (isDetailEnabled()) {
            System.out.println("@ [VAR] " + message);
        }
    }

    // 表达式相关输出
    public void logExpression(String message) {
        if (isDetailEnabled()) {
            System.out.println("@ [EXPR] " + message);
        }
    }

    // AST相关输出 - 只在base模式下显示
    public void logAST(String message) {
        if (isBaseEnabled()) {
            System.out.println("@ [AST] " + message);
        }
    }

    // 词法分析相关输出 - 只在base模式下显示
    public void logLexer(String message) {
        if (isBaseEnabled()) {
            System.out.println("@ [LEXER] " + message);
        }
    }

    // 语法分析相关输出 - 只在base模式下显示
    public void logParser(String message) {
        if (isBaseEnabled()) {
            System.out.println("@ [PARSER] " + message);
        }
    }

    // 重置调试器
    public void reset() {
        this.currentLevel = Level.BASIC;
        this.enabled = false;
    }

    @Override
    public String toString() {
        return "Debugger{level=" + currentLevel + ", enabled=" + enabled + "}";
    }
}