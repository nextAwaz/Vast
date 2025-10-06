package com.volcano.vm;

/**
 * 外部程序扩展接口 - 推荐外部程序实现此接口，以便在VolcanoScript运行时自动初始化和清理。
 * 这使得嵌入更简单。
 */
public interface ExternalVolcanoExtension {
    /**
     * 初始化方法 - 在脚本执行前调用
     */
    default void init() {
        // 默认空实现
    }

    /**
     * 清理方法 - 在脚本执行后调用
     */
    default void cleanup() {
        // 默认空实现
    }
}