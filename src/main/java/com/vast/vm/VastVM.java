package com.vast.vm;

import com.vast.ast.Program;
import com.vast.internal.Input;
import com.vast.parser.Lexer;
import com.vast.parser.Parser;
import com.vast.parser.Token;
import com.vast.interpreter.Interpreter;

import java.util.*;
import java.util.stream.Collectors;

public class VastVM {//Vast 虚拟机核心类
    // 内置类映射
    private static final Map<String, Class<?>> BUILTIN_CLASSES = new HashMap<>();

    // 全局变量
    private static final Map<String, Object> GLOBAL_VARS = new HashMap<>();

    // 实例变量
    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final Map<String, Object> localVariables = new HashMap<>();
    private Object lastResult = null;
    private boolean debugMode = false;

    static {
        // 注册内置类
        BUILTIN_CLASSES.put("Sys", com.vast.internal.Sys.class);
        BUILTIN_CLASSES.put("Time", com.vast.internal.TimeUtil.class);
        BUILTIN_CLASSES.put("Array", com.vast.internal.ArrayUtil.class);
        BUILTIN_CLASSES.put("Ops", com.vast.internal.Ops.class);
        BUILTIN_CLASSES.put("DataType", com.vast.internal.DataType.class);
        BUILTIN_CLASSES.put("EncodingUtil", com.vast.internal.EncodingUtil.class);
        BUILTIN_CLASSES.put("EnhancedInput", Input.class);
    }

    public VastVM() {
        // 自动导入所有内置类
        for (Map.Entry<String, Class<?>> entry : BUILTIN_CLASSES.entrySet()) {
            importedClasses.put(entry.getKey(), entry.getValue());
        }

        // 初始化全局变量
        initializeGlobalVariables();
    }

    public static void registerClass(String className, Class<?> clazz) {
        BUILTIN_CLASSES.put(className, clazz);
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    public Map<String, Class<?>> getImportedClasses() {
        return importedClasses;
    }

    public Map<String, Object> getLocalVariables() {
        return localVariables;
    }

    /**
     * 执行源代码
     */
    public void execute(List<String> sourceLines) throws Exception {
        executeWithResult(sourceLines);
    }


    /**
     * 执行源代码并返回结果
     */
    public Object executeWithResult(List<String> sourceLines) throws Exception {
        String source = String.join("\n", sourceLines);

        if (debugMode) {
            System.out.println("@ Source code:");
            System.out.println(source);
            System.out.println("@ End source code");
        }

        // 词法分析
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        if (debugMode) {
            System.out.println("@ Tokens:");
            tokens.forEach(System.out::println);
        }

        // 语法分析
        Parser parser = new Parser(tokens);
        Program program = parser.parseProgram();

        if (debugMode) {
            System.out.println("@ AST:");
            System.out.println(program);
        }

        // 使用新的运行时执行
        VastRuntime runtime = new VastRuntime(this, importedClasses, localVariables);
        runtime.setDebugMode(debugMode);
        runtime.execute(program);

        return getLastResult();
    }

    /**
     * 初始化全局变量
     */
    private void initializeGlobalVariables() {
        // 添加一些有用的全局变量
        GLOBAL_VARS.put("PI", Math.PI);
        GLOBAL_VARS.put("E", Math.E);
        GLOBAL_VARS.put("true", true);
        GLOBAL_VARS.put("false", false);
        GLOBAL_VARS.put("null", null);

        // 复制全局变量到局部变量
        localVariables.putAll(GLOBAL_VARS);
    }

    public Object getLastResult() {
        return lastResult;
    }

    public void reset() {
        importedClasses.clear();
        localVariables.clear();
        lastResult = null;
        debugMode = false;

        // 重新初始化全局变量
        initializeGlobalVariables();
    }

    public static Map<String, Class<?>> getBuiltinClasses() {
        return Collections.unmodifiableMap(BUILTIN_CLASSES);
    }

    /**
     * 获取 VM 状态信息（用于调试）
     */
    public String getVMInfo() {
        StringBuilder info = new StringBuilder();
        info.append("VastVM Status:\n");
        info.append("  Debug Mode: ").append(debugMode).append("\n");
        info.append("  Imported Classes: ").append(importedClasses.size()).append("\n");
        info.append("  Local Variables: ").append(localVariables.size()).append("\n");
        info.append("  Last Result: ").append(lastResult).append("\n");

        if (debugMode) {
            info.append("  Imported Classes: ").append(importedClasses.keySet()).append("\n");
            info.append("  Local Variables: ").append(
                    localVariables.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", "))
            ).append("\n");
        }

        return info.toString();
    }
}