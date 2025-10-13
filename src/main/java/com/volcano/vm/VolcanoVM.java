// VolcanoVM.java - 迁移到新系统
package com.volcano.vm;

import com.volcano.registry.LibraryRegistry;
import java.util.*;

public class VolcanoVM {
    static final Map<String, Class<?>> BUILTIN_CLASSES = new HashMap<>();
    static final Map<String, Object> GLOBAL_VARS = new HashMap<>();

    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final Map<String, Object> localVariables = new HashMap<>();
    private Object lastResult = null;
    private boolean debugMode = false;

    // 库注册表实例
    private final LibraryRegistry libraryRegistry = LibraryRegistry.getInstance();

    static {
        BUILTIN_CLASSES.put("Sys", com.volcano.internal.Sys.class);
        BUILTIN_CLASSES.put("Time", com.volcano.internal.TimeUtil.class);
        BUILTIN_CLASSES.put("Array", com.volcano.internal.ArrayUtil.class);
        BUILTIN_CLASSES.put("Ops", com.volcano.internal.Ops.class);
        BUILTIN_CLASSES.put("DataType", com.volcano.internal.DataType.class);
    }

    public VolcanoVM() {
        // 自动导入所有内置类
        for (Map.Entry<String, Class<?>> entry : BUILTIN_CLASSES.entrySet()) {
            importedClasses.put(entry.getKey(), entry.getValue());
        }
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

    public LibraryRegistry getLibraryRegistry() {
        return libraryRegistry;
    }

    public void execute(List<String> sourceLines) throws Exception {
        executeWithResult(sourceLines);
    }

    public Object executeWithResult(List<String> sourceLines) throws Exception {
        VolcanoRuntime runtime = new VolcanoRuntime(this, importedClasses, localVariables);
        runtime.setDebugMode(this.debugMode);

        // 检查是否需要加载所有库
        if (shouldLoadAllLibraries(sourceLines)) {
            libraryRegistry.loadAllLibraries(this);
        } else {
            // 检查并加载单个库导入（非内置库）
            loadIndividualLibraries(sourceLines);
        }

        runtime.execute(sourceLines);
        this.lastResult = runtime.getLastResult();
        return this.lastResult;
    }


    private boolean shouldLoadAllLibraries(List<String> sourceLines) {
        return sourceLines.stream()
                .anyMatch(line -> line.trim().equals("imp libs"));
    }

    private void loadIndividualLibraries(List<String> sourceLines) {
        for (String line : sourceLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("imp ")) {
                String libName = trimmed.substring(4).trim();
                // 跳过内置库和 "imp libs"
                if (!BUILTIN_CLASSES.containsKey(libName) && !libName.equals("libs")) {
                    libraryRegistry.loadLibraryByName(libName, this);
                }
            }
        }
    }

    public Object getLastResult() {
        return lastResult;
    }

    public void reset() {
        importedClasses.clear();
        localVariables.clear();
        lastResult = null;
        debugMode = false;
        libraryRegistry.cleanup();
    }

    public static Map<String, Class<?>> getBuiltinClasses() {
        return Collections.unmodifiableMap(BUILTIN_CLASSES);
    }

    Object getGiveResult() {
        return null;
    }

    // 注册内置外置库的静态方法
    public static void registerBuiltinLibrary(String name, Class<? extends com.volcano.registry.VolcanoExternalLibrary> libraryClass) {
        LibraryRegistry.getInstance().registerBuiltinLibrary(name, libraryClass);
    }

    /**
     * 获取库注册表信息（用于调试）
     */
    public String getLibraryRegistryInfo() {
        return libraryRegistry.getLibraryInfo();
    }
}