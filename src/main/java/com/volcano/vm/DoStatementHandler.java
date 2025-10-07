package com.volcano.vm;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;

import com.volcano.internal.exception.*;
import com.volcano.registry.LibraryRegistry;
import com.volcano.registry.VolcanoExternalLibrary;

public class DoStatementHandler {

    private final VolcanoRuntime runtime;
    private final Map<String, Class<?>> importedClasses;
    private final Map<String, Object> variables;
    private final LibraryRegistry libraryRegistry;

    public DoStatementHandler(VolcanoRuntime runtime,
                              Map<String, Class<?>> importedClasses,
                              Map<String, Object> variables) {
        this.runtime = runtime;
        this.importedClasses = importedClasses;
        this.variables = variables;
        this.libraryRegistry = runtime.getVolcanoVM().getLibraryRegistry();
    }

    public Object executeDoStatement(String className, String methodName, String arguments) throws Exception {
        // 验证基本格式
        validateDoStatementFormat(className, methodName, arguments);

        // 解析参数
        String[] args = parseArguments(arguments);

        if (className.isEmpty()) {
            // 情况1: 对外部程序操作 - 迁移到新的库系统
            return handleExternalProgramOperation(methodName, args);
        } else if (methodName.isEmpty()) {
            // 情况2: 操作类的全局变量
            return handleClassVariableOperation(className, args);
        } else {
            // 情况3: 调用类的静态方法
            return handleMethodCall(className, methodName, args);
        }
    }

    private void validateDoStatementFormat(String className, String methodName, String arguments) {
        if (className.isEmpty() && methodName.isEmpty()) {
            throw new NotGrammarException("Do statement must specify either class or method when both are empty");
        }

        if (className.isEmpty() && arguments.contains("var ")) {
            throw new NotGrammarException("Cannot use 'var' when no class is specified in do statement");
        }
    }

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

    private Object handleExternalProgramOperation(String methodName, String[] args) throws Exception {
        if (methodName.isEmpty()) {
            throw new NotGrammarException("Method name cannot be empty when no class is specified");
        }

        // 新的逻辑：查找已加载的库
        // 首先检查是否有名为 "ExternalExtension" 或 "ExternalWrapper" 的库
        VolcanoExternalLibrary externalLib = findExternalLibrary();
        if (externalLib != null) {
            return invokeLibraryMethod(externalLib, methodName, args);
        }

        // 如果没有找到外部库，尝试从局部变量中获取
        Object externalProgram = variables.get("_external_extension");
        if (externalProgram != null) {
            return invokeExternalMethod(externalProgram, methodName, args);
        }

        throw new PassParameterException("No external program or library is available for do statement");
    }

    private VolcanoExternalLibrary findLoadedLibrary(String libraryName) {
        // 这里需要访问 LibraryRegistry 的已加载库
        // 由于 LibraryRegistry 的 loadedLibraries 是私有的，我们需要添加一个公共方法
        // 或者通过反射访问（不推荐）

        // 临时方案：尝试通过类名查找
        try {
            // 这里简化处理，实际应该通过库注册表查找
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private VolcanoExternalLibrary findExternalLibrary() {
        // 检查常见的外部库名称
        String[] possibleNames = {"ExternalExtension", "ExternalWrapper"};
        for (String name : possibleNames) {
            VolcanoExternalLibrary lib = libraryRegistry.getLoadedLibrary(name);
            if (lib != null) {
                return lib;
            }
        }
        return null;
    }

    private Object invokeLibraryMethod(VolcanoExternalLibrary library, String methodName, String[] args) throws Exception {
        Class<?> clazz = library.getClass();
        Object[] evaluatedArgs = evaluateArguments(args);
        Method method = findBestMethod(clazz, methodName, evaluatedArgs);
        if (method == null) {
            throw NonExistentObject.methodNotFound(clazz.getSimpleName(), methodName);
        }
        return method.invoke(library, evaluatedArgs);
    }

    private VolcanoVM getVolcanoVM() {
        // 需要通过 runtime 访问，这里需要为 VolcanoRuntime 添加 getVolcanoVM 方法
        try {
            java.lang.reflect.Method method = runtime.getClass().getMethod("getVolcanoVM");
            return (VolcanoVM) method.invoke(runtime);
        } catch (Exception e) {
            throw new RuntimeException("Cannot access VolcanoVM from runtime", e);
        }
    }

    private Object invokeExternalMethod(Object externalProgram, String methodName, String[] args) throws Exception {
        Class<?> clazz = externalProgram.getClass();
        Object[] evaluatedArgs = evaluateArguments(args);
        Method method = findBestMethod(clazz, methodName, evaluatedArgs);
        if (method == null) {
            throw NonExistentObject.methodNotFound(clazz.getSimpleName(), methodName);
        }
        return method.invoke(externalProgram, evaluatedArgs);
    }

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

    private VariableOperationResult processVariableOperation(Class<?> clazz, String arg) throws Exception {
        // 识别 var 声明时，不再只检查 "var "（带空格）——也接受 "var(" 这种写法
        if (arg.startsWith("var")) {
            if (arg.length() == 3 || Character.isWhitespace(arg.charAt(3)) || arg.charAt(3) == '(') {
                // 声明新变量（传入声明体）
                return handleVariableDeclaration(clazz, arg.substring(3).trim());
            }
        } else {
            // 赋值现有变量
            return handleVariableAssignment(clazz, arg);
        }

        // 走默认分支（保持原逻辑）
        return handleVariableAssignment(clazz, arg);
    }

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

    private VariableOperationResult handleVariableAssignment(Class<?> clazz, String assignment) throws Exception {
        String[] parts = assignment.split("=", 2);
        if (parts.length != 2) {
            throw new NotGrammarException("Invalid variable assignment: " + assignment);
        }

        String varName = parts[0].trim();
        Object value = evaluateExpression(parts[1].trim());

        return setStaticField(clazz, varName, value);
    }

    private VariableOperationResult setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object convertedValue = convertValueToFieldType(value, field.getType());
            field.set(null, convertedValue);
            return new VariableOperationResult(fieldName, convertedValue, true);
        } catch (NoSuchFieldException e) {
            throw new NotGrammarException("Field not found: " + fieldName);
        } catch (IllegalAccessException e) {
            throw new CannotBeChanged("Cannot access field: " + fieldName, e);
        }
    }

    private Object handleMethodCall(String className, String methodName, String[] args) throws Exception {
        Class<?> clazz = findClass(className);
        Object[] evaluatedArgs = evaluateArguments(args);
        Method method = findBestMethod(clazz, methodName, evaluatedArgs);
        if (method == null) {
            throw NonExistentObject.methodNotFound(className, methodName);
        }
        return method.invoke(null, evaluatedArgs);
    }

    private Class<?> findClass(String className) throws Exception {
        Class<?> clazz = importedClasses.get(className);
        if (clazz != null) return clazz;
        clazz = VolcanoVM.BUILTIN_CLASSES.get(className);
        if (clazz != null) return clazz;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw NonExistentExternalLibraryException.forLibrary(className, e);
        }
    }

    private Method findBestMethod(Class<?> clazz, String methodName, Object[] args) {
        Method[] methods = clazz.getMethods();
        Method bestMatch = null;
        int bestScore = Integer.MAX_VALUE;

        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == args.length || (method.isVarArgs() && paramTypes.length - 1 <= args.length)) {
                    int score = calculateMatchScore(paramTypes, args, method.isVarArgs());
                    if (score < bestScore) {
                        bestScore = score;
                        bestMatch = method;
                    }
                }
            }
        }
        return bestMatch;
    }

    private int calculateMatchScore(Class<?>[] paramTypes, Object[] args, boolean isVarArgs) {
        int score = 0;
        int paramLength = paramTypes.length;
        if (isVarArgs) paramLength--;
        for (int i = 0; i < paramLength; i++) {
            score += getTypeMatchScore(paramTypes[i], args[i].getClass());
        }
        if (isVarArgs) {
            Class<?> varArgType = paramTypes[paramTypes.length - 1].getComponentType();
            for (int i = paramLength; i < args.length; i++) {
                score += getTypeMatchScore(varArgType, args[i].getClass());
            }
        }
        return score;
    }

    private int getTypeMatchScore(Class<?> paramType, Class<?> argType) {
        if (paramType.equals(argType)) return 0;
        if (paramType.isAssignableFrom(argType)) return 1;
        if (paramType.isPrimitive() && argType.equals(getWrapperClass(paramType))) return 2;
        return Integer.MAX_VALUE;
    }

    private Class<?> getWrapperClass(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == double.class) return Double.class;
        if (primitive == boolean.class) return Boolean.class;
        // ... other primitives
        return primitive;
    }

    private boolean isVarArgsCompatible(Method method, Object[] args) {
        if (!method.isVarArgs()) return false;
        Class<?>[] paramTypes = method.getParameterTypes();
        if (args.length < paramTypes.length - 1) return false;
        Class<?> componentType = paramTypes[paramTypes.length - 1].getComponentType();

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

    private Object[] evaluateArguments(String[] args) throws Exception {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = evaluateExpression(args[i]);
        }
        return result;
    }

    // 支持带行号的 evaluateExpression
    /**
     * 对表达式求值，委托给VolcanoRuntime。
     * 向后兼容的单参数版本委托给带未知行（-1）的双参数版本。
     */
    private Object evaluateExpression(String expr) throws Exception {
        return evaluateExpression(expr, -1);
    }

    /**
     * 接受行号并转发到运行时的新重载。evaluateExpression (expr lineNumber)
     */
    private Object evaluateExpression(String expr, int lineNumber) throws Exception {
        return runtime.evaluateExpression(expr, lineNumber);
    }


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
