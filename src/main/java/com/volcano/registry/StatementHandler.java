package com.volcano.registry;

import com.volcano.vm.VolcanoRuntime;
import com.volcano.internal.exception.VolcanoRuntimeException;

/**
 * 自定义语句处理器
 */
@FunctionalInterface
public interface StatementHandler {
    void handle(VolcanoRuntime runtime, String statement, String arguments, int lineNumber)
            throws VolcanoRuntimeException;
}