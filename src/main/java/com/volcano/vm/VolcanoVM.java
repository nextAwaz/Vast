package com.volcano.vm;

import java.util.*;

import com.volcano.internal.*;
import com.volcano.registry.LibraryRegistry;
import com.volcano.registry.VolcanoExternalLibrary;

public class VolcanoVM {
    static final Map<String, Class<?>> BUILTIN_CLASSES = new HashMap<>();
    static final Map<String, Object> GLOBAL_VARS = new HashMap<>();

    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final Map<String, Object> localVariables = new HashMap<>();
    private Object lastResult = null;
    private boolean debugMode = false;
    private final LibraryRegistry libraryRegistry = LibraryRegistry.getInstance();

    static {
        BUILTIN_CLASSES.put("Sys", Sys.class);
        BUILTIN_CLASSES.put("Time", TimeUtil.class);
        BUILTIN_CLASSES.put("Array", ArrayUtil.class);
        BUILTIN_CLASSES.put("Ops", Ops.class);
        BUILTIN_CLASSES.put("DataType", DataType.class);
    }

    public static void registerClass(String className, Class<?> clazz) {
        BUILTIN_CLASSES.put(className, clazz);
    }

    public static void setGlobal(String name, Object value) {
        GLOBAL_VARS.put(name, value);
    }

    public static Object getGlobal(String name) {
        return GLOBAL_VARS.get(name);
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    public void execute(List<String> sourceLines) throws Exception {
        executeWithResult(sourceLines);
    }

    /**
     * 执行脚本时支持库加载
     */
    public Object executeWithResult(List<String> sourceLines) throws Exception {
        // 在执行前可以加载所有库或按需加载
        VolcanoRuntime runtime = new VolcanoRuntime(importedClasses, localVariables);
        runtime.setDebugMode(this.debugMode);

        // 检查是否需要加载所有库
        if (shouldLoadAllLibraries(sourceLines)) {
            libraryRegistry.loadAllLibraries(this);
        }

        runtime.execute(sourceLines);
        this.lastResult = runtime.getLastResult();
        return this.lastResult;
    }
    private boolean shouldLoadAllLibraries(List<String> sourceLines) {
        return sourceLines.stream()
                .anyMatch(line -> line.trim().equals("imp libs"));
    }

    /**
     * 获取当前导入的类映射
     */
    public Map<String, Class<?>> getImportedClasses() {
        return importedClasses;
    }

    /**
     * 获取本地变量映射
     */
    public Map<String, Object> getLocalVariables() {
        return localVariables;
    }

    /**
     * 获取库注册表
     */
    public LibraryRegistry getLibraryRegistry() {
        return libraryRegistry;
    }


    public Object getLastResult() {
        return lastResult;
    }

    public void reset() {
        importedClasses.clear();
        localVariables.clear();
        lastResult = null;
        debugMode = false;
    }

    public static Map<String, Class<?>> getBuiltinClasses() {
        return Collections.unmodifiableMap(BUILTIN_CLASSES);
    }

    Object getGiveResult() {
        return null;
    }
}
