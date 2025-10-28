package com.vast.internal;

/**
 * 简化的调试器类 - 只控制是否显示堆栈追踪
 */
public class Debugger {
    private static Debugger instance;
    private boolean showStackTrace = false;

    private Debugger() {
        // 私有构造函数
    }

    public static Debugger getInstance() {
        if (instance == null) {
            instance = new Debugger();
        }
        return instance;
    }

    public void setShowStackTrace(boolean show) {
        this.showStackTrace = show;
        if (show) {
            System.out.println("@ [DEBUG] Stack trace enabled");
        }
    }

    public boolean isShowStackTrace() {
        return showStackTrace;
    }

    // 基本日志输出 - 总是显示
    public void log(String message) {
        System.out.println(message);
    }

    // 调试输出 - 只在调试模式下显示
    public void debug(String message) {
        if (showStackTrace) {
            System.out.println("@ " + message);
        }
    }

    // 错误输出 - 总是显示
    public void error(String message) {
        System.err.println("[ERROR] " + message);
    }

    // 错误输出带异常 - 根据调试模式决定是否显示堆栈
    public void error(String message, Exception e) {
        error(message);
        if (showStackTrace && e != null) {
            e.printStackTrace();
        }
    }

    // 警告输出 - 总是显示
    public void warning(String message) {
        System.out.println("@ [WARNING] " + message);
    }

    @Override
    public String toString() {
        return "Debugger{showStackTrace=" + showStackTrace + "}";
    }
}