package com.volcano.registry;

import com.volcano.vm.VolcanoRuntime;
import com.volcano.internal.exception.VolcanoRuntimeException;

/**
 * 自定义关键字处理器
 */
@FunctionalInterface
public interface KeywordHandler {
    Object handle(VolcanoRuntime runtime, String keyword, String arguments, int lineNumber)
            throws VolcanoRuntimeException;
}