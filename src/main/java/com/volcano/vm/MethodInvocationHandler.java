package com.volcano.vm;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import com.volcano.internal.Sys;
import com.volcano.internal.exception.*;

/**
 * 方法调用处理器 - 专门处理所有方法调用逻辑
 */
public class MethodInvocationHandler {

    private final VolcanoRuntime runtime;
    private final Map<String, Class<?>> importedClasses;

    public MethodInvocationHandler(VolcanoRuntime runtime, Map<String, Class<?>> importedClasses) {
        this.runtime = runtime;
        this.importedClasses = importedClasses;
    }

    /**
     * 执行方法调用
     */
    public Object invokeMethod(String className, String methodName, String argsStr) throws Exception {
        // 验证基本格式
        validateMethodCallFormat(className, methodName, argsStr);

        // 查找类
        Class<?> clazz = findClass(className);

        // 解析参数
        Object[] args = parseArguments(argsStr);

        // 查找并调用方法
        return invokeClassMethod(clazz, methodName, args);
    }

    /**
     * 执行系统输入调用（特殊处理）
     */
    public Object invokeSysInput(String argsStr) throws Exception {
        // 使用反射调用 Sys.input 方法
        Object[] args = parseArguments(argsStr);
        return invokeClassMethod(Sys.class, "input", args);
    }

    /**
     * 验证方法调用格式
     */
    private void validateMethodCallFormat(String className, String methodName, String argsStr) {
        if (className == null || className.isEmpty()) {
            throw new NotGrammarException("Class name cannot be empty in method call");
        }

        if (methodName == null || methodName.isEmpty()) {
            throw new NotGrammarException("Method name cannot be empty in method call");
        }

        // 验证类名格式
        if (!isValidIdentifier(className)) {
            throw new NotGrammarException("Invalid class name: " + className);
        }

        // 验证方法名格式
        if (!isValidIdentifier(methodName)) {
            throw new NotGrammarException("Invalid method name: " + methodName);
        }
    }

    /**
     * 查找类
     */
    private Class<?> findClass(String className) throws Exception {
        // 首先检查导入的类
        Class<?> clazz = importedClasses.get(className);
        if (clazz != null) {
            return clazz;
        }

        // 检查内置类
        clazz = VolcanoVM.BUILTIN_CLASSES.get(className);
        if (clazz != null) {
            return clazz;
        }

        // 尝试动态加载
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw NonExistentExternalLibraryException.forLibrary(className, e);
        }
    }

    /**
     * 调用类方法
     */
    private Object invokeClassMethod(Class<?> clazz, String methodName, Object[] args) throws Exception {
        Method method = findBestMethod(clazz, methodName, args);
        if (method == null) {
            throw NonExistentObject.methodNotFound(clazz.getSimpleName(), methodName);
        }

        try {
            // 调用静态方法
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new CannotBeChanged("Cannot access method: " + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof VolcanoRuntimeException) {
                throw (VolcanoRuntimeException) targetException;
            }
            throw new UnknownVolcanoException(targetException);
        } catch (IllegalArgumentException e) {
            throw new PassParameterException("Invalid arguments for method: " + methodName, e);
        }
    }

    /**
     * 查找最佳匹配方法
     */
    private Method findBestMethod(Class<?> clazz, String methodName, Object[] args) {
        Method[] methods = clazz.getMethods();
        List<Method> candidateMethods = new ArrayList<>();

        // 第一步：收集所有同名方法
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                candidateMethods.add(method);
            }
        }

        if (candidateMethods.isEmpty()) {
            return null;
        }

        // 第二步：寻找精确匹配的方法
        for (Method method : candidateMethods) {
            if (isExactMatch(method, args)) {
                return method;
            }
        }

        // 第三步：寻找兼容匹配的方法
        for (Method method : candidateMethods) {
            if (isCompatibleMatch(method, args)) {
                return method;
            }
        }

        // 第四步：尝试可变参数匹配
        for (Method method : candidateMethods) {
            if (isVarArgsCompatible(method, args)) {
                return method;
            }
        }

        return null;
    }

    /**
     * 检查精确匹配
     */
    private boolean isExactMatch(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();

        if (paramTypes.length != args.length) {
            return false;
        }

        for (int i = 0; i < paramTypes.length; i++) {
            if (!isExactTypeMatch(paramTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查兼容匹配
     */
    private boolean isCompatibleMatch(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();

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
     * 精确类型匹配检查
     */
    private boolean isExactTypeMatch(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive(); // 原始类型不能为null
        }

        if (paramType.isPrimitive()) {
            // 原始类型匹配
            return (paramType == int.class && arg instanceof Integer) ||
                    (paramType == boolean.class && arg instanceof Boolean) ||
                    (paramType == double.class && arg instanceof Double) ||
                    (paramType == long.class && arg instanceof Long) ||
                    (paramType == float.class && arg instanceof Float);
        }

        // 引用类型精确匹配
        return paramType.equals(arg.getClass());
    }

    /**
     * 兼容类型匹配检查
     */
    private boolean isTypeCompatible(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive(); // 原始类型不能为null
        }

        if (paramType.isPrimitive()) {
            // 原始类型兼容性
            return (paramType == int.class && canConvertToInt(arg)) ||
                    (paramType == boolean.class && canConvertToBoolean(arg)) ||
                    (paramType == double.class && canConvertToDouble(arg)) ||
                    (paramType == long.class && canConvertToLong(arg)) ||
                    (paramType == float.class && canConvertToFloat(arg));
        }

        // 处理 Object 类型参数（接受任何类型）
        if (paramType == Object.class) {
            return true;
        }

        // 处理数组类型
        if (paramType.isArray()) {
            return arg.getClass().isArray() ||
                    paramType.getComponentType() == Object.class;
        }

        // 处理字符串和其他引用类型
        return paramType.isInstance(arg) || canConvertToType(paramType, arg);
    }

    /**
     * 检查是否可以转换为整数
     */
    private boolean canConvertToInt(Object arg) {
        return arg instanceof Number ||
                arg instanceof Boolean ||
                (arg instanceof String && isNumeric((String) arg));
    }

    /**
     * 检查是否可以转换为布尔值
     */
    private boolean canConvertToBoolean(Object arg) {
        return arg instanceof Boolean ||
                arg instanceof Number ||
                arg instanceof String;
    }

    /**
     * 检查是否可以转换为双精度浮点数
     */
    private boolean canConvertToDouble(Object arg) {
        return arg instanceof Number ||
                (arg instanceof String && isNumeric((String) arg));
    }

    /**
     * 检查是否可以转换为长整型
     */
    private boolean canConvertToLong(Object arg) {
        return arg instanceof Number ||
                (arg instanceof String && isNumeric((String) arg));
    }

    /**
     * 检查是否可以转换为单精度浮点数
     */
    private boolean canConvertToFloat(Object arg) {
        return arg instanceof Number ||
                (arg instanceof String && isNumeric((String) arg));
    }

    /**
     * 检查是否可以转换为指定类型
     */
    private boolean canConvertToType(Class<?> targetType, Object arg) {
        // 字符串转换检查
        if (targetType == String.class) {
            return true; // 任何对象都可以转换为字符串
        }

        // 数字类型之间的转换
        if (targetType == Integer.class && arg instanceof Number) {
            return true;
        }
        if (targetType == Double.class && arg instanceof Number) {
            return true;
        }

        return false;
    }

    /**
     * 检查字符串是否为数字
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查是否为有效标识符
     */
    private boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // 检查第一个字符
        char firstChar = name.charAt(0);
        if (!Character.isJavaIdentifierStart(firstChar)) {
            return false;
        }

        // 检查剩余字符
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 解析参数
     */
    private Object[] parseArguments(String argsStr) throws Exception {
        if (argsStr == null || argsStr.trim().isEmpty()) {
            return new Object[0];
        }

        String[] argStrings = splitArguments(argsStr);
        Object[] args = new Object[argStrings.length];

        for (int i = 0; i < argStrings.length; i++) {
            args[i] = evaluateExpression(argStrings[i].trim());
        }

        return args;
    }

    /**
     * 分割参数字符串
     */
    private String[] splitArguments(String argsStr) {
        List<String> parts = new ArrayList<>();
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
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }

        return parts.toArray(new String[0]);
    }

    /**
     * 计算表达式
     */
    private Object evaluateExpression(String expr) throws Exception {
        // 委托给runtime的表达式求值功能
        return runtime.evaluateExpression(expr);
    }

    /**
     * 获取方法参数信息（用于调试和错误信息）
     */
    public String getMethodSignature(Class<?> clazz, String methodName) {
        Method[] methods = clazz.getMethods();
        List<String> signatures = new ArrayList<>();

        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();
                StringBuilder signature = new StringBuilder();
                signature.append(methodName).append("(");

                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0) signature.append(", ");
                    signature.append(getTypeName(paramTypes[i]));
                }

                if (method.isVarArgs()) {
                    signature.append("...");
                }

                signature.append(")");
                signatures.add(signature.toString());
            }
        }

        return String.join(" or ", signatures);
    }

    /**
     * 获取类型名称
     */
    private String getTypeName(Class<?> type) {
        if (type.isArray()) {
            return getTypeName(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }
}