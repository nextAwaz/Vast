package com.volcano;

import com.volcano.vm.VolcanoVM;
import com.volcano.internal.exception.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * VolcanoScript 主类 - 作为外部库的入口点
 * 其他程序可以通过 Volcano.run("script.vast") 来执行脚本
 */
public class Volcano {

    /**
     * 执行 VolcanoScript 文件
     * @param scriptPath 脚本文件路径
     */
    public static void run(String scriptPath) {
        run(scriptPath, false);
    }

    /**
     * 执行 VolcanoScript 文件（带调试开关）
     * @param scriptPath 脚本文件路径
     * @param debug 是否启用调试模式
     */
    public static void run(String scriptPath, boolean debug) {
        try {
            validateScriptFile(scriptPath);
            List<String> lines = Files.readAllLines(Paths.get(scriptPath));
            VolcanoVM vm = new VolcanoVM();
            vm.setDebugMode(debug);
            vm.execute(lines);
        } catch (VolcanoRuntimeException e) {
            // 重新抛出Volcano特定的异常
            throw new VolcanoException("Script execution failed: " + e.getUserFriendlyMessage(), e);
        } catch (Exception e) {
            // 包装其他异常
            throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
        }
    }

    /**
     * 直接执行 VolcanoScript 代码字符串
     * @param code 脚本代码
     */
    public static void execute(String code) {
        execute(code, false);
    }

    /**
     * 直接执行 VolcanoScript 代码字符串（带调试开关）
     * @param code 脚本代码
     * @param debug 是否启用调试模式
     */
    public static void execute(String code, boolean debug) {
        try {
            List<String> lines = List.of(code.split("\n"));
            VolcanoVM vm = new VolcanoVM();
            vm.setDebugMode(debug);
            vm.execute(lines);
        } catch (VolcanoRuntimeException e) {
            throw new VolcanoException("Code execution failed: " + e.getUserFriendlyMessage(), e);
        } catch (Exception e) {
            throw new VolcanoException("Failed to execute code: " + e.getMessage(), e);
        }
    }

    /**
     * 执行脚本并返回结果（用于有返回值的场景）
     * @param scriptPath 脚本文件路径
     * @return 执行结果
     */
    public static Object runWithResult(String scriptPath) {
        return runWithResult(scriptPath, false);
    }

    /**
     * 执行脚本并返回结果（用于有返回值的场景，带调试开关）
     * @param scriptPath 脚本文件路径
     * @param debug 是否启用调试模式
     * @return 执行结果
     */
    public static Object runWithResult(String scriptPath, boolean debug) {
        try {
            validateScriptFile(scriptPath);
            List<String> lines = Files.readAllLines(Paths.get(scriptPath));
            VolcanoVM vm = new VolcanoVM();
            vm.setDebugMode(debug);
            return vm.executeWithResult(lines);
        } catch (VolcanoRuntimeException e) {
            throw new VolcanoException("Script execution failed: " + e.getUserFriendlyMessage(), e);
        } catch (Exception e) {
            throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
        }
    }

    /**
     * 验证脚本文件
     */
    private static void validateScriptFile(String scriptPath) {
        File file = new File(scriptPath);
        if (!file.exists()) {
            throw new VolcanoException("Script file not found: " + scriptPath);
        }
        if (!scriptPath.endsWith(".vast")) {
            throw new VolcanoException("Only .vast files are supported: " + scriptPath);
        }
    }

    /**
     * Volcano 异常类
     */
    public static class VolcanoException extends RuntimeException {
        public VolcanoException(String message) {
            super(message);
        }

        public VolcanoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}