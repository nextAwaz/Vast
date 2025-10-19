package com.vast;

import com.vast.internal.Debugger;
import com.vast.internal.exception.VastExceptions;
import com.vast.vm.VastVM;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Vast {// Vast 脚本执行入口

    public static class VastException extends VastExceptions.VastRuntimeException {
        public VastException(String message) {
            super(message);
        }

        public VastException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Builder {
        private Debugger.Level debugLevel = Debugger.Level.BASIC;

        public Builder debugLevel(Debugger.Level level) {
            this.debugLevel = level;
            return this;
        }

        public VastVM build() {
            VastVM vm = new VastVM();
            vm.setDebugLevel(debugLevel);
            return vm;
        }

        public void run(String scriptPath) {
            VastVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VastException("Failed to execute script: " + e.getMessage(), e);
            }
        }

        public void execute(String code) {
            VastVM vm = build();
            try {
                List<String> lines = List.of(code.split("\n"));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VastException("Failed to execute code: " + e.getMessage(), e);
            }
        }

        public Object runWithResult(String scriptPath) {
            VastVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                return vm.executeWithResult(lines);
            } catch (Exception e) {
                throw new VastException("Failed to execute script: " + e.getMessage(), e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // 保留旧的 run 方法用于兼容性
    public static void run(String scriptPath) {
        run(scriptPath, Debugger.Level.BASIC);
    }

    // 保留旧的 run 方法用于兼容性
    public static void run(String scriptPath, boolean debug) {
        run(scriptPath, debug ? Debugger.Level.DETAIL : Debugger.Level.BASIC);
    }

    // 新的 run 方法使用调试等级
    public static void run(String scriptPath, Debugger.Level debugLevel) {
        builder().debugLevel(debugLevel).run(scriptPath);
    }

    // 保留旧的 execute 方法用于兼容性
    public static void execute(String code) {
        execute(code, Debugger.Level.BASIC);
    }

    // 保留旧的 execute 方法用于兼容性
    public static void execute(String code, boolean debug) {
        execute(code, debug ? Debugger.Level.DETAIL : Debugger.Level.BASIC);
    }

    // 新的 execute 方法使用调试等级
    public static void execute(String code, Debugger.Level debugLevel) {
        builder().debugLevel(debugLevel).execute(code);
    }

    // 保留旧的 runWithResult 方法用于兼容性
    public static Object runWithResult(String scriptPath) {
        return runWithResult(scriptPath, Debugger.Level.BASIC);
    }

    // 保留旧的 runWithResult 方法用于兼容性
    public static Object runWithResult(String scriptPath, boolean debug) {
        return runWithResult(scriptPath, debug ? Debugger.Level.DETAIL : Debugger.Level.BASIC);
    }

    // 新的 runWithResult 方法使用调试等级
    public static Object runWithResult(String scriptPath, Debugger.Level debugLevel) {
        return builder().debugLevel(debugLevel).runWithResult(scriptPath);
    }

    private static void validateScriptFile(String scriptPath) {
        File file = new File(scriptPath);
        if (!file.exists()) {
            throw new VastException("Script file not found: " + scriptPath);
        }
        if (!scriptPath.endsWith(".vast")) {
            throw new VastException("Only .vast files are supported: " + scriptPath);
        }
    }

    /**
     * 快速执行一段代码（用于测试）
     */
    public static Object quickEval(String code) {
        try {
            VastVM vm = new VastVM();
            List<String> lines = List.of(code.split("\n"));
            return vm.executeWithResult(lines);
        } catch (Exception e) {
            throw new VastException("Evaluation failed: " + e.getMessage(), e);
        }
    }
}