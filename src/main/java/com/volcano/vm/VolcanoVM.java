package com.volcano.vm;

import java.util.*;
import java.lang.reflect.Method;
import com.volcano.internal.*;

/**
 * VolcanoScript 虚拟机核心
 */
public class VolcanoVM {
    // 改为包内可见，通过 Volcano 类来访问
    static final Map<String, Class<?>> BUILTIN_CLASSES = new HashMap<>();
    static final Map<String, Object> GLOBAL_VARS = new HashMap<>();

    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final Map<String, Object> localVariables = new HashMap<>();
    private Object lastResult = null;
    private boolean debugMode = false;

    static {
        BUILTIN_CLASSES.put("Sys", Sys.class);
        BUILTIN_CLASSES.put("Time", TimeUtil.class);
        BUILTIN_CLASSES.put("Array", ArrayUtil.class);
        BUILTIN_CLASSES.put("Ops", Ops.class);
        // DataType 自动导入，无需显式导入
        BUILTIN_CLASSES.put("DataType", DataType.class);
    }

    // 静态方法供 Volcano 类调用
    public static void registerClass(String className, Class<?> clazz) {
        BUILTIN_CLASSES.put(className, clazz);
    }

    public static void setGlobal(String name, Object value) {
        GLOBAL_VARS.put(name, value);
    }

    public static Object getGlobal(String name) {
        return GLOBAL_VARS.get(name);
    }

    /**
     * 设置调试模式
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * 执行脚本
     */
    public void execute(List<String> sourceLines) throws Exception {
        executeWithResult(sourceLines);
    }

    /**
     * 执行脚本并返回结果
     */
    public Object executeWithResult(List<String> sourceLines) throws Exception {
        VolcanoRuntime runtime = new VolcanoRuntime(importedClasses, localVariables);
        runtime.setDebugMode(this.debugMode); // 传递调试模式
        runtime.execute(sourceLines);
        this.lastResult = runtime.getLastResult();
        return this.lastResult;
    }

    /**
     * 获取最后一次执行的结果
     */
    public Object getLastResult() {
        return lastResult;
    }

    /**
     * 清空状态（用于多次执行）
     */
    public void reset() {
        importedClasses.clear();
        localVariables.clear();
        lastResult = null;
        debugMode = false;
    }

    /**
     * 获取give语句的结果（包内可见）
     */
    Object getGiveResult() {
        // 这个方法会被Volcano.java通过反射调用
        // 实际实现应该在VolcanoRuntime中
        return null;
    }
}