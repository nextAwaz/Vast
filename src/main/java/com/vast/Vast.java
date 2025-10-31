package com.vast;

import com.vast.internal.VastExceptions;
import com.vast.vm.VastVM;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Vast {
    // Vast 脚本执行入口

    public static class VastException extends VastExceptions.VastRuntimeException {
        public VastException(String message) {
            super(message);
        }

        public VastException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Builder {
        private boolean debug = false;

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public VastVM build() {
            VastVM vm = new VastVM();
            vm.setDebugMode(debug);
            return vm;
        }

        public void run(String scriptPath) {
            validateScriptFile(scriptPath);
            VastVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VastException("Failed to execute script: " + e.getMessage(), e);
            }
        }

        public Object runWithResult(String scriptPath) {
            validateScriptFile(scriptPath);
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
        run(scriptPath, false);
    }

    // 保留旧的 run 方法用于兼容性
    public static void run(String scriptPath, boolean debug) {
        builder().debug(debug).run(scriptPath);
    }

    // 保留旧的 runWithResult 方法用于兼容性
    public static Object runWithResult(String scriptPath) {
        return runWithResult(scriptPath, false);
    }

    // 保留旧的 runWithResult 方法用于兼容性
    public static Object runWithResult(String scriptPath, boolean debug) {
        return builder().debug(debug).runWithResult(scriptPath);
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
     * 获取 Vast 版本信息
     */
    public static String getVersion() {
        return "0.1.2";
    }

    /**
     * 获取 VM 信息（用于调试）
     */
    public static String getVMInfo() {
        VastVM vm = new VastVM();
        return vm.getVMInfo();
    }

    /**
     * 重置 VM 状态（用于测试）
     */
    @Deprecated
    public static void resetVM() {
        // 注意：这个方法不建议在生产代码中使用
        // 主要用于单元测试和调试
        VastVM vm = new VastVM();
        vm.reset();
    }
}