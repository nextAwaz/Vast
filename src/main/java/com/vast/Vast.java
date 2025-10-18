package com.vast;

import com.vast.vm.VastVM;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Vast {// Vast 脚本执行入口

    public static class VastException extends RuntimeException {
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

    public static void run(String scriptPath) {
        run(scriptPath, false);
    }

    public static void run(String scriptPath, boolean debug) {
        builder().debug(debug).run(scriptPath);
    }

    public static void execute(String code) {
        execute(code, false);
    }

    public static void execute(String code, boolean debug) {
        builder().debug(debug).execute(code);
    }

    public static Object runWithResult(String scriptPath) {
        return runWithResult(scriptPath, false);
    }

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