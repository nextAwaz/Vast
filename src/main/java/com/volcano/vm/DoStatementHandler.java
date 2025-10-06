package com.volcano.vm;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;
import com.volcano.internal.exception.*;

/**
 * Do语句处理器 - 专门处理do语句的所有逻辑
 * 格式: do([className])([methodName])([arguments])
 */
public class DoStatementHandler {

    private final VolcanoRuntime runtime;
    private final Map<String, Class<?>> importedClasses;
    private final Map<String, Object> variables;
    private MethodInvocationHandler methodInvocationHandler;//处理外部Method

    public DoStatementHandler(VolcanoRuntime runtime,
                              Map<String, Class<?>> importedClasses,
                              Map<String, Object> variables) {
        this.runtime = runtime;
        this.importedClasses = importedClasses;
        this.variables = variables;
    }

    /**
     * 执行do语句
     */
    public Object executeDoStatement(String className, String methodName, String arguments) throws Exception {
        // 验证基本格式
        validateDoStatementFormat(className, methodName, arguments);

        // 解析参数
        String[] args = parseArguments(arguments);

        if (className.isEmpty()) {
            // 情况1: 对外部程序操作
            return handleExternalProgramOperation(methodName, args);
        } else if (methodName.isEmpty()) {
            // 情况2: 操作类的全局变量
            return handleClassVariableOperation(className, args);
        } else {
            // 情况3: 调用类的静态方法
            return handleMethodCall(className, methodName, args);
        }
    }

    /**
     * 验证do语句格式
     */
    private void validateDoStatementFormat(String className, String methodName, String arguments) {
        // 三个括号都必须存在（由解析器保证）
        // 这里主要验证逻辑约束

        if (className.isEmpty() && methodName.isEmpty()) {
            throw new NotGrammarException("Do statement must specify either class or method when both are empty");
        }

        if (className.isEmpty() && arguments.contains("var ")) {
            throw new NotGrammarException("Cannot use 'var' when no class is specified in do statement");
        }
    }

    /**
     * 将参数数组构建为参数字符串
     */
    private String buildArgumentsString(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            if (args[i] instanceof String) {
                sb.append("\"").append(args[i]).append("\"");
            } else {
                sb.append(args[i]);
            }
        }
        return sb.toString();
    }

    /**
     * 处理对外部程序的操作
     */
    private Object handleExternalProgramOperation(String methodName, String[] args) throws Exception {
        if (methodName.isEmpty()) {
            throw new NotGrammarException("Method name cannot be empty when no class is specified");
        }

        // 获取外部程序（通过VM注册）
        Object externalProgram = VolcanoVM.getGlobal("EXTERNAL_PROGRAM");
        if (externalProgram == null) {
            throw new PassParameterException("No external program is available for do statement");
        }

        // 调用外部程序的方法
        return invokeExternalMethod(externalProgram, methodName, args);
    }

    /**
     * 处理类的变量操作
     */
    private Object handleClassVariableOperation(String className, String[] args) throws Exception {
        Class<?> clazz = findClass(className);

        if (args.length == 0) {
            throw new NotGrammarException("Variable operation requires at least one argument");
        }

        List<VariableOperationResult> results = new ArrayList<>();

        for (String arg : args) {
            VariableOperationResult result = processVariableOperation(clazz, arg.trim());
            results.add(result);
        }

        // 返回最后一个操作的结果
        return results.get(results.size() - 1).getValue();
    }

    /**
     * 处理变量操作
     */
    private VariableOperationResult processVariableOperation(Class<?> clazz, String arg) throws Exception {
        if (arg.startsWith("var ")) {
            // 声明新变量
            return handleVariableDeclaration(clazz, arg.substring(4).trim());
        } else {
            // 赋值现有变量
            return handleVariableAssignment(clazz, arg);
        }
    }

    /**
     * 处理变量声明
     */
    private VariableOperationResult handleVariableDeclaration(Class<?> clazz, String declaration) throws Exception {
        String[] parts = declaration.split("=", 2);
        if (parts.length != 2) {
            throw new NotGrammarException("Invalid variable declaration: " + declaration);
        }

        String varName = parts[0].trim();
        Object value = evaluateExpression(parts[1].trim());

        try {
            // 检查字段是否已存在
            Field existingField = clazz.getDeclaredField(varName);

            // 字段已存在，重命名
            String newName = "_" + varName + "_";
            return setStaticField(clazz, newName, value);

        } catch (NoSuchFieldException e) {
            // 字段不存在，创建新字段（通过反射设置）
            return setStaticField(clazz, varName, value);
        }
    }

    /**
     * 处理变量赋值
     */
    private VariableOperationResult handleVariableAssignment(Class<?> clazz, String assignment) throws Exception {
        String[] parts = assignment.split("=", 2);
        if (parts.length != 2) {
            throw new NotGrammarException("Invalid variable assignment: " + assignment);
        }

        String varName = parts[0].trim();
        String valueExpr = parts[1].trim();

        // 如果值为空，保持原值
        if (valueExpr.isEmpty()) {
            Object currentValue = getStaticField(clazz, varName);
            return new VariableOperationResult(varName, currentValue, false);
        }

        Object value = evaluateExpression(valueExpr);
        return setStaticField(clazz, varName, value);
    }

    /**
     * 设置静态字段
     */
    private VariableOperationResult setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            // 类型转换
            Object convertedValue = convertValueToFieldType(value, field.getType());
            field.set(null, convertedValue);

            return new VariableOperationResult(fieldName, convertedValue, true);

        } catch (NoSuchFieldException e) {
            throw NonExistentObject.fieldNotFound(clazz.getSimpleName(), fieldName);
        }
    }

    /**
     * 获取静态字段值
     */
    private Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (NoSuchFieldException e) {
            throw NonExistentObject.fieldNotFound(clazz.getSimpleName(), fieldName);
        }
    }

    /**
     * 处理方法调用
     */
    private Object handleMethodCall(String className, String methodName, String[] args) throws Exception {
        // 构建参数字符串
        String argsStr = String.join(", ", args);
        return methodInvocationHandler.invokeMethod(className, methodName, argsStr);
    }

    /**
     * 调用外部程序方法
     */
    private Object invokeExternalMethod(Object externalProgram, String methodName, String[] args) throws Exception {
        Object[] evaluatedArgs = evaluateArguments(args);

        // 使用反射调用外部程序方法
        Method method = findBestMethod(externalProgram.getClass(), methodName, evaluatedArgs);
        if (method == null) {
            throw NonExistentObject.methodNotFound(externalProgram.getClass().getSimpleName(), methodName);
        }

        return method.invoke(externalProgram, evaluatedArgs);
    }

    /**
     * 查找类
     */
    private Class<?> findClass(String className) throws Exception {
        Class<?> clazz = importedClasses.get(className);
        if (clazz == null) {
            clazz = VolcanoVM.BUILTIN_CLASSES.get(className);
            if (clazz == null) {
                try {
                    clazz = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw NonExistentObject.classNotFound(className);
                }
            }
        }
        return clazz;
    }

    /**
     * 查找最佳匹配方法
     */
    private Method findBestMethod(Class<?> clazz, String methodName, Object[] args) {
        Method[] methods = clazz.getMethods();
        List<Method> candidateMethods = new ArrayList<>();

        // 收集所有同名方法
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                candidateMethods.add(method);
            }
        }

        if (candidateMethods.isEmpty()) {
            return null;
        }

        // 寻找参数最匹配的方法
        for (Method method : candidateMethods) {
            if (isArgsCompatible(method.getParameterTypes(), args)) {
                return method;
            }
        }

        // 尝试可变参数匹配
        for (Method method : candidateMethods) {
            if (isVarArgsCompatible(method, args)) {
                return method;
            }
        }

        return null;
    }

    /**
     * 检查参数兼容性
     */
    private boolean isArgsCompatible(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) {
            return false;
        }

        for (int i = 0; i < paramTypes.length; i++) {
            if (!isTypeCompatible(paramTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查可变参数兼容性
     */
    private boolean isVarArgsCompatible(Method method, Object[] args) {
        if (!method.isVarArgs()) {
            return false;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return false;
        }

        Class<?> varParamType = paramTypes[paramTypes.length - 1];
        if (!varParamType.isArray()) {
            return false;
        }

        Class<?> componentType = varParamType.getComponentType();

        if (paramTypes.length == 1) {
            // 只有可变参数
            for (Object arg : args) {
                if (!isTypeCompatible(componentType, arg)) {
                    return false;
                }
            }
            return true;
        } else {
            // 固定参数 + 可变参数
            if (args.length < paramTypes.length - 1) {
                return false;
            }

            // 检查固定参数
            for (int i = 0; i < paramTypes.length - 1; i++) {
                if (!isTypeCompatible(paramTypes[i], args[i])) {
                    return false;
                }
            }

            // 检查可变参数
            for (int i = paramTypes.length - 1; i < args.length; i++) {
                if (!isTypeCompatible(componentType, args[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 检查类型兼容性
     */
    private boolean isTypeCompatible(Class<?> paramType, Object arg) {
        if (paramType.isPrimitive()) {
            return (paramType == int.class && arg instanceof Integer) ||
                    (paramType == boolean.class && arg instanceof Boolean) ||
                    (paramType == double.class && arg instanceof Double) ||
                    (paramType == long.class && arg instanceof Long);
        }

        if (paramType.isArray()) {
            return true;
        }

        if (paramType == Object.class) {
            return true;
        }

        return paramType.isInstance(arg);
    }

    /**
     * 解析参数
     */
    private String[] parseArguments(String argsStr) {
        if (argsStr.trim().isEmpty()) {
            return new String[0];
        }

        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean inEscape = false;

        for (char c : argsStr.toCharArray()) {
            if (inEscape) {
                current.append(c);
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString().trim());
        }

        return args.toArray(new String[0]);
    }

    /**
     * 计算参数值
     */
    private Object[] evaluateArguments(String[] args) throws Exception {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = evaluateExpression(args[i]);
        }
        return result;
    }

    /**
     * 计算表达式
     */
    private Object evaluateExpression(String expr) throws Exception {
        // 使用runtime的表达式求值功能
        return runtime.evaluateExpression(expr);
    }

    /**
     * 值类型转换
     */
    private Object convertValueToFieldType(Object value, Class<?> fieldType) {
        if (value == null) {
            return getDefaultValue(fieldType);
        }

        if (fieldType.isInstance(value)) {
            return value;
        }

        // 基本类型转换
        if (fieldType == int.class || fieldType == Integer.class) {
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) {
                try { return Integer.parseInt((String) value); }
                catch (NumberFormatException e) { return 0; }
            }
            if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        }
        else if (fieldType == double.class || fieldType == Double.class) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value instanceof String) {
                try { return Double.parseDouble((String) value); }
                catch (NumberFormatException e) { return 0.0; }
            }
            if (value instanceof Boolean) return (Boolean) value ? 1.0 : 0.0;
        }
        else if (fieldType == boolean.class || fieldType == Boolean.class) {
            if (value instanceof Boolean) return value;
            if (value instanceof Number) return ((Number) value).doubleValue() != 0;
            if (value instanceof String) return !((String) value).isEmpty();
        }
        else if (fieldType == String.class) {
            return value.toString();
        }

        return value; // 无法转换，返回原值
    }

    /**
     * 获取默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == double.class) return 0.0;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    /**
     * 检查是否为独立的do语句（不是赋值语句的一部分）
     */
    private boolean isStandaloneDoStatement() {
        // 这个需要通过调用上下文来判断
        // 简化实现：假设总是独立语句
        return true;
    }

    /**
     * 变量操作结果
     */
    private static class VariableOperationResult {
        private final String variableName;
        private final Object value;
        private final boolean wasSet;

        public VariableOperationResult(String variableName, Object value, boolean wasSet) {
            this.variableName = variableName;
            this.value = value;
            this.wasSet = wasSet;
        }

        public String getVariableName() { return variableName; }
        public Object getValue() { return value; }
        public boolean wasSet() { return wasSet; }
    }
}