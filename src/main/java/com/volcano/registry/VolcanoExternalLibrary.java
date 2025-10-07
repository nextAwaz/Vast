package com.volcano.registry;

import com.volcano.vm.VolcanoVM;
import java.util.Map;

/**
 * 外置库接口 - 所有外置库必须实现此接口
 */
public interface VolcanoExternalLibrary {

    /**
     * 库的元数据信息
     */
    LibraryMetadata getMetadata();

    /**
     * 初始化库 - 在库被加载时调用
     */
    void initialize(VolcanoVM vm, LibraryRegistry registry);

    /**
     * 清理资源 - 在脚本执行完毕或库被卸载时调用
     */
    default void cleanup() {
        // 默认空实现
    }

    /**
     * 获取库提供的静态类映射
     * key: 类名, value: 对应的Class对象
     */
    Map<String, Class<?>> getProvidedClasses();

    /**
     * 获取自定义关键字处理器映射
     * key: 关键字, value: 关键字处理器
     */
    default Map<String, KeywordHandler> getKeywordHandlers() {
        return Map.of();
    }

    /**
     * 获取自定义语句处理器映射  
     * key: 语句前缀, value: 语句处理器
     */
    default Map<String, StatementHandler> getStatementHandlers() {
        return Map.of();
    }
}