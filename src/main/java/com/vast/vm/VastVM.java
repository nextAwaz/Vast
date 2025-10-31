package com.vast.vm;

import com.vast.ast.Program;
import com.vast.internal.Debugger;
import com.vast.internal.Input;
import com.vast.internal.SmartErrorSuggestor;
import com.vast.internal.VastExceptions;
import com.vast.parser.Lexer;
import com.vast.parser.Parser;
import com.vast.parser.Token;
import com.vast.interpreter.Interpreter;

import java.io.File;
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
    private final Debugger debugger;
    private Interpreter interpreter;

    private final SmartErrorSuggestor errorSuggestor;// 智能错误建议器

    // 对于外置库的支持
    private final VastLibraryLoader libraryLoader;


    static {
        // 注册内置类
        BUILTIN_CLASSES.put("Sys", com.vast.internal.Sys.class);
        BUILTIN_CLASSES.put("Time", com.vast.internal.TimeUtil.class);
        BUILTIN_CLASSES.put("Ops", com.vast.internal.Ops.class);
        BUILTIN_CLASSES.put("DataType", com.vast.internal.DataType.class);
        BUILTIN_CLASSES.put("EnhancedInput", Input.class);
    }

    public VastVM() {
        // 自动导入所有内置类
        for (Map.Entry<String, Class<?>> entry : BUILTIN_CLASSES.entrySet()) {
            importedClasses.put(entry.getKey(), entry.getValue());
        }

        this.debugger = Debugger.getInstance();
        this.interpreter = new Interpreter(this);
        this.errorSuggestor = new SmartErrorSuggestor(this);
        this.libraryLoader = VastLibraryLoader.getInstance();

        // 初始化全局变量
        initializeGlobalVariables();

        // 扫描并加载可用库
        libraryLoader.scanAndLoadAvailableLibraries(this);
    }

    /**
     * 获取自定义语法管理器
     */
    public CustomSyntaxManager getCustomSyntaxManager() {
        return libraryLoader.getCustomSyntaxManager();
    }

    /**
     * 检查是否启用了高级特性
     */
    public boolean hasAdvancedFeatures() {
        return !libraryLoader.getCustomSyntaxManager().getAllRules().isEmpty();
    }

    /**
     * 扫描并加载可用库
     */
    private void scanAndLoadAvailableLibraries() {
        // 扫描当前目录
        scanDirectoryForLibraries(new File("."));

        // 扫描 vast_libs 目录
        File libsDir = new File("vast_libs");
        if (libsDir.exists() && libsDir.isDirectory()) {
            scanDirectoryForLibraries(libsDir);
        }
    }

    /**
     * 扫描目录中的库文件
     */
    private void scanDirectoryForLibraries(File dir) {
        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".zip"));

        if (files != null) {
            for (File file : files) {
                if (libraryLoader.isVastVMJar(file)) {
                    continue; // 跳过 VM 自身的 JAR
                }

                try {
                    // 从文件名推断库名（移除扩展名）
                    String libraryName = file.getName().replaceFirst("[.][^.]+$", "");
                    libraryLoader.loadLibraryFromFile(file, libraryName, this);
                } catch (Exception e) {
                    debugger.debug("Failed to auto-load library: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * 执行导入语句
     */
    public boolean handleImport(String importPath) {
        return libraryLoader.loadLibraryFromImport(importPath, this);
    }

    /**
     * 解析方法名对应的类名
     */
    public String resolveMethodClass(String methodName) {
        return libraryLoader.resolveClassNameForMethod(methodName, this);
    }

    /**
     * 获取库加载器信息
     */
    public String getLibraryLoaderInfo() {
        return libraryLoader.getLoaderInfo();
    }

    public void setDebugMode(boolean debug) {
        debugger.setShowStackTrace(debug);
    }
    public Debugger getDebugger() {
        return debugger;
    }

    public SmartErrorSuggestor getErrorSuggestor() {
        return errorSuggestor;
    }


    public static void registerClass(String className, Class<?> clazz) {
        BUILTIN_CLASSES.put(className, clazz);
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

        if (debugger.isShowStackTrace()) {
            debugger.debug("Source code:\n" + source);
        }

        try {
            // 词法分析
            Lexer lexer = new Lexer(source);
            List<Token> tokens = lexer.scanTokens();

            if (debugger.isShowStackTrace()) {
                debugger.debug("Tokens:");
                tokens.forEach(token -> debugger.debug("  " + token));
            }

            // 语法分析
            Parser parser = new Parser(tokens);
            Program program = parser.parseProgram();

            if (debugger.isShowStackTrace()) {
                debugger.debug("AST:\n" + program);
            }

            // 使用持久化的解释器执行
            interpreter.interpret(program);

            // 获取最后结果
            this.lastResult = interpreter.getLastResult();
            return getLastResult();

        } catch (VastExceptions.VastRuntimeException e) {
            debugger.error(e.getUserFriendlyMessage(), e);
            throw e;
        } catch (Exception e) {
            VastExceptions.UnknownVastException vastException =
                    new VastExceptions.UnknownVastException("Unexpected error during execution", e);
            debugger.error(vastException.getUserFriendlyMessage(), e);
            throw vastException;
        }
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

    /**
     * 重置 VM 状态（用于 shell 中的 reset 命令）
     */
    public void reset() {
        // 创建新的解释器（重置所有状态）
        this.interpreter = new Interpreter(this);

        // 重置其他状态
        importedClasses.clear();
        lastResult = null;

        // 重新导入内置类
        for (Map.Entry<String, Class<?>> entry : BUILTIN_CLASSES.entrySet()) {
            importedClasses.put(entry.getKey(), entry.getValue());
        }

        // 重新初始化全局变量
        initializeGlobalVariables();

        // 重新扫描和加载库
        if (libraryLoader != null) {
            libraryLoader.cleanup(); // 先清理已加载的库
            libraryLoader.scanAndLoadAvailableLibraries(this); // 重新扫描加载
        }

        debugger.log("@ VM state has been reset");

        // 如果有高级特性，显示相关信息
        if (hasAdvancedFeatures()) {
            debugger.debug("Advanced features loaded: " +
                    libraryLoader.getCustomSyntaxManager().getAllRules().size() + " custom rules");
        }
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