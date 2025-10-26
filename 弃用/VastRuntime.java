package com.vast.vm;//弃用

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.interpreter.Interpreter;
import java.util.*;

public class VastRuntime {
    private final Map<String, Class<?>> importedClasses;
    private final Map<String, Object> variables;
    private final VastVM vm;
    private boolean debugMode = false;

    public VastRuntime(VastVM vm, Map<String, Class<?>> importedClasses, Map<String, Object> variables) {
        this.vm = vm;
        this.importedClasses = importedClasses;
        this.variables = variables;

        // 自动导入DataType类
        this.importedClasses.put("DataType", com.vast.internal.DataType.class);
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * 执行AST程序
     */
    public void execute(Program program) throws Exception {
        // 使用解释器执行AST - 传入 VastVM 实例
        Interpreter interpreter = new Interpreter(vm);

        // 设置调试模式
        if (debugMode) {
            System.out.println("@ Executing AST program with " + program.getStatements().size() + " statements");
        }

        interpreter.interpret(program);
    }

    /**
     * 获取最后结果
     */
    public Object getLastResult() {
        // 这里需要从解释器获取最后结果
        // 实际实现中应该在解释器中暴露这个方法
        return null;
    }
}