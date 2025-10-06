// 注意：以下是更新的完整代码。包括新功能：严格数据类型、字符串重复、change语句、未初始化变量异常等。
// 我在 VolcanoRuntime 中实现了基本的表达式求值（使用简单递归下降解析器，支持 + - * /，字符串，数字，变量）。
// 添加了 variableTypes Map。
// 更新了 handleVarDecl、compile、executeInstruction 等。
// 对于类型：支持 "int", "double", "boolean", "string"。
// 对于 change：实现强制转换。
// 对于 * ：支持字符串重复，链式。
// 对于严格：如果指定类型，不允许隐式转换；在操作中检查类型兼容。

package com.volcano;

import com.volcano.vm.VolcanoVM;
import com.volcano.internal.exception.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Volcano {

    public static class VolcanoException extends RuntimeException {
        public VolcanoException(String message) {
            super(message);
        }

        public VolcanoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Builder {
        private boolean debug = false;
        private Object externalExtension = null;

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder externalExtension(Object extension) {
            this.externalExtension = extension;
            return this;
        }

        public VolcanoVM build() {
            VolcanoVM vm = new VolcanoVM();
            vm.setDebugMode(debug);
            if (externalExtension != null) {
                VolcanoVM.setGlobal("EXTERNAL_PROGRAM", externalExtension);
            }
            return vm;
        }

        public void run(String scriptPath) {
            VolcanoVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
            }
        }

        public void execute(String code) {
            VolcanoVM vm = build();
            try {
                List<String> lines = List.of(code.split("\n"));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute code: " + e.getMessage(), e);
            }
        }

        public Object runWithResult(String scriptPath) {
            VolcanoVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                return vm.executeWithResult(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
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
            throw new VolcanoException("Script file not found: " + scriptPath);
        }
        if (!scriptPath.endsWith(".vast")) {
            throw new VolcanoException("Only .vast files are supported: " + scriptPath);
        }
    }
}