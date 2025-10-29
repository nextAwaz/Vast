package com.vast.internal;

import com.vast.vm.VastVM;
import com.vast.ast.expressions.VariableExpression;

import java.util.*;
import java.util.stream.Collectors;

public class SmartErrorSuggestor {// 更加优雅的错误建议系统，借鉴于linux的命令行建议系统
    private final VastVM vm;
    private final Debugger debugger;

    // 内置关键字和函数列表
    private static final Set<String> BUILTIN_KEYWORDS = Set.of(
            "imp", "loop", "use", "swap", "var", "int", "string", "bool",
            "double", "float", "char", "true", "false", "if", "else",
            "while", "for"
    );

    public SmartErrorSuggestor(VastVM vm) {
        this.vm = vm;
        this.debugger = vm.getDebugger();
    }

    /**
     * 为未知变量提供智能建议
     */
    public String suggestForUnknownVariable(String variableName) {
        Set<String> candidates = new HashSet<>();

        // 1. 收集所有已知变量
        if (vm.getLocalVariables() != null) {
            candidates.addAll(vm.getLocalVariables().keySet());
        }

        // 2. 收集内置关键字
        candidates.addAll(BUILTIN_KEYWORDS);

        // 3. 收集内置类名
        if (VastVM.getBuiltinClasses() != null) {
            candidates.addAll(VastVM.getBuiltinClasses().keySet());
        }

        // 4. 收集导入的类名
        if (vm.getImportedClasses() != null) {
            candidates.addAll(vm.getImportedClasses().keySet());
        }

        // 查找相似的候选
        List<String> suggestions = StringSimilarity.findSimilarStrings(
                variableName, candidates, 3, 0.6);

        return formatSuggestionMessage(variableName, suggestions, "variable");
    }

    /**
     * 为未知方法提供智能建议
     */
    public String suggestForUnknownMethod(String methodName, String className) {
        Set<String> candidates = new HashSet<>();

        // 收集类中的所有方法
        try {
            Class<?> clazz = findClass(className);
            if (clazz != null) {
                Arrays.stream(clazz.getMethods())
                        .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                        .forEach(method -> candidates.add(method.getName()));
            }
        } catch (Exception e) {
            debugger.debug("Failed to collect methods from class: " + className);
        }

        // 查找相似的候选
        List<String> suggestions = StringSimilarity.findSimilarStrings(
                methodName, candidates, 3, 0.6);

        return formatSuggestionMessage(methodName, suggestions, "method");
    }

    /**
     * 为未知类提供智能建议
     */
    public String suggestForUnknownClass(String className) {
        Set<String> candidates = new HashSet<>();

        // 收集所有已知类
        if (VastVM.getBuiltinClasses() != null) {
            candidates.addAll(VastVM.getBuiltinClasses().keySet());
        }
        if (vm.getImportedClasses() != null) {
            candidates.addAll(vm.getImportedClasses().keySet());
        }

        // 查找相似的候选
        List<String> suggestions = StringSimilarity.findSimilarStrings(
                className, candidates, 3, 0.6);

        return formatSuggestionMessage(className, suggestions, "class");
    }

    /**
     * 格式化建议消息
     */
    private String formatSuggestionMessage(String target, List<String> suggestions, String type) {
        if (suggestions.isEmpty()) {
            return String.format("Unknown %s '%s'", type, target);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s or function '%s' does not exist!",
                type.substring(0, 1).toUpperCase() + type.substring(1), target));

        if (suggestions.size() == 1) {
            sb.append(String.format(" Are you mean: %s?", suggestions.get(0)));
        } else {
            sb.append(" Are you mean: ");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) {
                    sb.append(i == suggestions.size() - 1 ? " or " : ", ");
                }
                sb.append(suggestions.get(i));
            }
            sb.append("?");
        }

        return sb.toString();
    }

    private Class<?> findClass(String className) {
        // 首先检查内置类
        Map<String, Class<?>> builtinClasses = VastVM.getBuiltinClasses();
        if (builtinClasses.containsKey(className)) {
            return builtinClasses.get(className);
        }

        // 然后检查导入的类
        if (vm.getImportedClasses().containsKey(className)) {
            return vm.getImportedClasses().get(className);
        }

        // 最后尝试动态加载
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}