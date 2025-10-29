package com.vast.vm;

import com.vast.ast.Program;
import com.vast.internal.Debugger;
import com.vast.internal.Input;
import com.vast.internal.SmartErrorSuggestor;
import com.vast.internal.exception.VastExceptions;
import com.vast.parser.Lexer;
import com.vast.parser.Parser;
import com.vast.parser.Token;
import com.vast.interpreter.Interpreter;
import com.vast.registry.VastExternalLibrary;
import com.vast.registry.VastLibraryLoader;
import com.vast.registry.VastLibraryRegistry;

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

    private final SmartErrorSuggestor errorSuggestor;// 智能错误建议器

    // 对于外置库的支持
    private final VastLibraryLoader libraryLoader;
    private final VastLibraryRegistry libraryRegistry;

    // 调试器
    private final Debugger debugger;
    private Interpreter interpreter;

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

        // 初始化库加载器和注册表
        this.libraryLoader = VastLibraryLoader.getInstance();
        this.libraryRegistry = VastLibraryRegistry.getInstance(); // 初始化注册表

        // 扫描并加载可用库
        this.libraryLoader.scanAndLoadAvailableLibraries(this);

        this.debugger = Debugger.getInstance();

        this.interpreter = new Interpreter(this);// 初始化解释器

        this.errorSuggestor = new SmartErrorSuggestor(this);

        // 初始化全局变量
        initializeGlobalVariables();
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
            libraryLoader.scanAndLoadAvailableLibraries(this);
        }

        debugger.log("@ VM state has been reset");
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

    //============= 有关外置库的内容 ================
    public VastLibraryLoader getLibraryLoader() {
        return libraryLoader;
    }

    /**
     * 手动注册外置库
     */
    public void registerExternalLibrary(String libraryId, Class<? extends VastExternalLibrary> libraryClass) {
        libraryRegistry.registerLibrary(libraryId, libraryClass);
        System.out.println("@ Registering external library: " + libraryId);
    }

    /**
     * 手动注册外置库实例
     */
    public void registerExternalLibraryInstance(String libraryId, VastExternalLibrary instance) {
        libraryRegistry.registerLibraryInstance(libraryId, instance);
    }

    /**
     * 加载外置库
     */
    public boolean loadExternalLibrary(String libraryId) {
        return libraryLoader.loadLibraryFromImport(libraryId, this);
    }

    /**
     * 获取库注册表
     */
    public VastLibraryRegistry getLibraryRegistry() {
        return libraryRegistry;
    }
}