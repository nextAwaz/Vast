package com.volcano.vm;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import com.volcano.internal.Sys;
import com.volcano.internal.exception.*;

public class MethodInvocationHandler {

    private final VolcanoRuntime runtime;
    private final Map<String, Class<?>> importedClasses;

    public MethodInvocationHandler(VolcanoRuntime runtime, Map<String, Class<?>> importedClasses) {
        this.runtime = runtime;
        this.importedClasses = importedClasses;
    }

    /**
     * 调用类的静态方法
     * argsStr 形式保留为源码形式 (包含引号等) — 内部会解析并求值为 Object[]
     */
    public Object invokeMethod(String className, String methodName, String argsStr) throws Exception {
        // 验证基本格式
        validateMethodCallFormat(className, methodName, argsStr);

        // 查找类
        Class<?> clazz = findClass(className);

        // 解析参数（求值为 Java 对象）
        Object[] args = parseArgumentsAsObjects(argsStr);

        // 查找并调用方法
        return invokeClassMethod(clazz, methodName, args);
    }

    /**
     * 为 VolcanoRuntime 的 Sys.input 调用提供入口
     * VolcanoRuntime 会调用 methodInvocationHandler.invokeSysInput("\"prompt\"") 或 invokeSysInput("")
     * 这里解析并调用 Sys.input(...) 静态方法并返回其结果（String）
     */
    public Object invokeSysInput(String argsStr) throws Exception {
        // 解析并求值参数为 Object[]（字符串字面量会被解析为不含引号的 String）
        Object[] args = parseArgumentsAsObjects(argsStr);
        return invokeClassMethod(Sys.class, "input", args);
    }

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

    private Object invokeClassMethod(Class<?> clazz, String methodName, Object[] args) throws Exception {
        Method method = findBestMethod(clazz, methodName, args);
        if (method == null) {
            throw NonExistentObject.methodNotFound(clazz.getSimpleName(), methodName);
        }

        try {
            // 调用静态方法（所有调用都假定为静态方法）
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
        if (arg == null) return !paramType.isPrimitive();

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

    private String[] splitArguments(String argsStr) {
        if (argsStr == null) return new String[0];
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean inEscape = false;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
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
     * 把参数字符串解析为 Object[]（会对每个 part 调用 runtime.evaluateExpression）
     * 例如: "\"Hello\"" -> Java String Hello (无双引号)
     */
    private Object[] parseArgumentsAsObjects(String argsStr) throws Exception {
        if (argsStr == null || argsStr.trim().isEmpty()) return new Object[0];

        String[] parts = splitArguments(argsStr);
        Object[] args = new Object[parts.length];

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            // 使用 runtime 的求值（兼容带/不带行号的重载）
            args[i] = evaluateExpression(part, -1);
        }
        return args;
    }

    // ========= 修改点：增加两个重载 evaluateExpression 方法，兼容新版 runtime =========
    /**
     * 兼容旧调用：单参数版本委托到带行号版本（-1 表示未知行号）
     */
    private Object evaluateExpression(String expr) throws Exception {
        return evaluateExpression(expr, -1);
    }

    /**
     * 将行号传递到 runtime（新版 VolcanoRuntime 要求行号参数）
     */
    private Object evaluateExpression(String expr, int lineNumber) throws Exception {
        return runtime.evaluateExpression(expr, lineNumber);
    }
    // ========= 修改点结束 =========

    // 新增：标识符校验（与其它处理类一致）
    private boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        char firstChar = name.charAt(0);
        if (!Character.isJavaIdentifierStart(firstChar)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // 新增：精确类型匹配检查，用于 isExactMatch
    private boolean isExactTypeMatch(Class<?> paramType, Object arg) {
        // null 只能匹配非原始类型参数
        if (arg == null) {
            return !paramType.isPrimitive();
        }

        // 如果参数类型本身能接受该对象，则直接匹配
        if (paramType.isInstance(arg)) {
            return true;
        }

        // 如果 paramType 是原始类型，检查包装类型是否匹配
        if (paramType.isPrimitive()) {
            Class<?> wrapper = getWrapperClass(paramType);
            if (wrapper != null && wrapper.isInstance(arg)) {
                return true;
            }
        }

        return false;
    }

    private Class<?> getWrapperClass(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == double.class) return Double.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == char.class) return Character.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        return primitive;
    }
}