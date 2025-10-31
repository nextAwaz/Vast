package com.vast.interpreter;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.internal.Debugger;
import com.vast.internal.Fraction;
import com.vast.internal.SmartErrorSuggestor;
import com.vast.internal.Sys;
import com.vast.registry.VastLibraryLoader;
import com.vast.vm.VastVM;
import com.vast.internal.exception.VastExceptions;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

// 解释器类，负责执行AST节点
public class Interpreter implements ASTVisitor<Void> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> variableTypes = new HashMap<>();//变量存储类型
    private Object lastResult = null;
    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final VastVM vm;
    private final Debugger debugger;
    private final Map<String, String> staticMethodToClass = new HashMap<>();//静态方法名到类名的映射
    private final Map<String, Set<String>> methodConflicts = new HashMap<>();//方法名冲突记录

    private final SmartErrorSuggestor errorSuggestor;

    public Interpreter(VastVM vm) {
        this.vm = vm;
        this.debugger = vm.getDebugger();
        this.errorSuggestor = vm.getErrorSuggestor(); // 初始化错误提示器

        // 初始化日志
        if (debugger.isShowStackTrace()) {
            debugger.debug("Type checking system initialized");
            debugger.debug("Static method mapping initialized");
        }

        // 复制已导入的类
        if (vm != null && vm.getImportedClasses() != null) {
            this.importedClasses.putAll(vm.getImportedClasses());
        }

        initializeStaticMethodMapping();//初始化静态方法映射
    }

    /**
     * 初始化静态方法映射，检查方法名冲突
     */
    private void initializeStaticMethodMapping() {
        // 如果已经初始化过，跳过
        if (!staticMethodToClass.isEmpty() || !methodConflicts.isEmpty()) {
            debugger.debug("Static method mapping already initialized, skipping");
            return;
        }

        // 收集所有类的静态方法
        Map<String, Set<String>> methodToClasses = new HashMap<>();

        // 1. 内置类的静态方法
        for (Map.Entry<String, Class<?>> entry : VastVM.getBuiltinClasses().entrySet()) {
            collectStaticMethods(entry.getKey(), entry.getValue(), methodToClasses);
        }

        // 2. 已导入类的静态方法
        for (Map.Entry<String, Class<?>> entry : importedClasses.entrySet()) {
            collectStaticMethods(entry.getKey(), entry.getValue(), methodToClasses);
        }

        // 构建唯一方法名映射并记录冲突
        for (Map.Entry<String, Set<String>> entry : methodToClasses.entrySet()) {
            String methodName = entry.getKey();
            Set<String> classes = entry.getValue();

            if (classes.size() == 1) {
                // 方法名唯一，添加到映射
                staticMethodToClass.put(methodName, classes.iterator().next());
                debugger.debug("Static method '" + methodName + "' uniquely mapped to class: " + classes.iterator().next());
            } else {
                // 方法名冲突，记录冲突信息
                methodConflicts.put(methodName, classes);
                debugger.warning("Method name conflict: '" + methodName + "' exists in multiple classes: " + classes);
            }
        }

        debugger.debug("Static method mapping initialized with " + staticMethodToClass.size() + " unique methods");
    }

    /**
     * 收集类的静态方法
     */
    private void collectStaticMethods(String className, Class<?> clazz, Map<String, Set<String>> methodToClasses) {
        try {
            java.lang.reflect.Method[] methods = clazz.getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    String methodName = method.getName();
                    methodToClasses.computeIfAbsent(methodName, k -> new HashSet<>()).add(className);
                }
            }
        } catch (Exception e) {
            debugger.warning("Failed to collect static methods from class: " + className);
        }
    }

    /**
     * 检查方法名是否唯一
     */
    private String resolveClassNameForMethod(String methodName, int lineNumber, int columnNumber) {
        if (staticMethodToClass.containsKey(methodName)) {
            String className = staticMethodToClass.get(methodName);
            debugger.debug("Resolved method '" + methodName + "' to class: " + className); // 改为 debug
            return className;
        }

        if (methodConflicts.containsKey(methodName)) {
            throw VastExceptions.AmbiguousReferenceException.forMethod(
                    methodName, methodConflicts.get(methodName), lineNumber, columnNumber);
        }

        debugger.debug("Method '" + methodName + "' not found in static method mapping"); // 改为 debug
        return null; // 方法不存在
    }

    public void interpret(Program program) {
        try {
            debugger.debug("Starting program interpretation");

            // 初始化静态方法映射（如果需要）
            initializeStaticMethodMapping();

            program.accept(this);
            debugger.debug("Program interpretation completed");
        } catch (VastExceptions.VastRuntimeException error) {
            debugger.error("Runtime error: " + error.getUserFriendlyMessage());
            throw error;
        }
    }

    @Override
    public Void visitRootExpression(RootExpression expr) {
        Object result = evaluate(expr);
        this.lastResult = result;
        if (result != null) {
            debugger.debug("Root expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitInlineTypeCastStatement(InlineTypeCastStatement stmt) {
        TypeCastExpression castExpr = stmt.getTypeCastExpression();
        Expression sourceExpr = castExpr.getExpression();

        // 确保源表达式是变量表达式
        if (!(sourceExpr instanceof VariableExpression)) {
            throw new VastExceptions.NotGrammarException(
                    "Inline type cast can only be applied to variables",
                    stmt.getLineNumber(), stmt.getColumnNumber()
            );
        }

        String varName = ((VariableExpression) sourceExpr).getName();

        // 检查变量是否存在
        if (!variables.containsKey(varName)) {
            throw VastExceptions.NonExistentObject.variableNotFound(varName);
        }

        Object currentValue = variables.get(varName);
        String targetType = castExpr.getTargetType();

        // 执行类型转换
        Object newValue = performTypeCast(currentValue, targetType,
                stmt.getLineNumber(), stmt.getColumnNumber(), true);

        // 更新变量值和类型
        variables.put(varName, newValue);
        variableTypes.put(varName, targetType); // 更新为强类型

        debugger.debug("Inline type cast: " + varName + " -> " + targetType + " = " + newValue);

        return null;
    }

    // 表达式访问方法
    @Override
    public Void visitLiteralExpression(LiteralExpression expr) {
        return null;
    }

    @Override
    public Void visitVariableExpression(VariableExpression expr) {
        return null;
    }

    @Override
    public Void visitBinaryExpression(BinaryExpression expr) {
        return null;
    }

    @Override
    public Void visitUnaryExpression(UnaryExpression expr) {
        return null;
    }

    @Override
    public Void visitAssignmentExpression(AssignmentExpression expr) {
        Object value = evaluate(expr.getValue());
        String varName = expr.getVariableName();

        debugger.debug("Assignment expression: " + varName + " = " + value);

        // 严格的类型检查
        if (variableTypes.containsKey(varName)) {
            String expectedType = variableTypes.get(varName);
            validateTypeCompatibility(expectedType, value, varName,
                    expr.getLineNumber(), expr.getColumnNumber());
        }

        variables.put(varName, value);
        return null;
    }

    @Override
    public Void visitBitwiseExpression(BitwiseExpression expr) {
        // 按位表达式应该在 ExpressionEvaluator 中处理
        debugger.debug("Bitwise expression: " + expr);
        return null;
    }

    @Override
    public Void visitFractionExpression(FractionExpression expr) {
        Object result = evaluate(expr);
        this.lastResult = result;
        if (result != null) {
            debugger.debug("Fraction expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitMemberAccessExpression(MemberAccessExpression expr) {
        return null;
    }

    @Override
    public Void visitFunctionCallExpression(FunctionCallExpression expr) {
        return null;
    }

    @Override
    public Void visitMethodCallExpression(MethodCallExpression expr) {
        Object result = evaluate(expr);
        this.lastResult = result;
        if (result != null) {
            debugger.debug("Method call result: " + result);
        }
        return null;
    }

    // 语句访问方法
    @Override
    public Void visitVariableDeclaration(VariableDeclaration stmt) {
        Object value = null;
        if (stmt.getInitialValue() != null) {
            value = evaluate(stmt.getInitialValue());
        }

        String varName = stmt.getVariableName();
        String typeHint = stmt.getTypeHint();

        debugger.debug("Variable declaration: " + varName +
                ", type: " + typeHint + ", initial value: " + value +
                (stmt.isTypeCast() ? " (type cast)" : ""));

        // 强类型变量声明
        if (typeHint != null) {
            variableTypes.put(varName, typeHint);
            debugger.debug("Registered type constraint: " + varName + " -> " + typeHint);

            // 严格验证初始值的类型，支持隐式转换
            if (value != null) {
                if (!isTypeCompatible(typeHint, value)) {
                    String errorMsg = "Type mismatch: cannot assign " + getValueType(value) +
                            " to variable '" + varName + "' of type " + typeHint;
                    debugger.log(errorMsg);
                    throw new VastExceptions.NotGrammarException(
                            errorMsg, stmt.getLineNumber(), stmt.getColumnNumber()
                    );
                }

                // 如果类型兼容但需要转换，进行自动类型转换
                value = performAutoConversion(value, typeHint,
                        stmt.getLineNumber(), stmt.getColumnNumber());
            }
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.debug("Var declared: " + varName + " = " + value +
                (typeHint != null ? " (type: " + typeHint + ")" : ""));
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatement stmt) {
        Object value = evaluate(stmt.getValue());
        String varName = stmt.getVariableName();
        String typeHint = stmt.getTypeHint();

        debugger.debug("Assignment: " + varName + " = " + value +
                (typeHint != null ? " (strong type: " + typeHint + ")" : " (free type)"));

        if (typeHint != null) {
            // 强类型赋值 - 严格类型检查，支持隐式转换
            if (!isTypeCompatible(typeHint, value)) {
                String errorMsg = "Type mismatch: cannot assign " + getValueType(value) +
                        " to variable '" + varName + "' of type " + typeHint;
                debugger.log(errorMsg);
                throw new VastExceptions.NotGrammarException(
                        errorMsg, stmt.getLineNumber(), stmt.getColumnNumber()
                );
            }

            // 如果类型兼容但需要转换，进行自动类型转换
            value = performAutoConversion(value, typeHint,
                    stmt.getLineNumber(), stmt.getColumnNumber());

            variableTypes.put(varName, typeHint);
            debugger.debug("Strong type assignment PASSED");
        } else {
            // 自由类型赋值 - 不进行类型检查
            if (variableTypes.containsKey(varName)) {
                debugger.warning("Warning: free type assignment to strongly typed variable " + varName);
                // 自由类型赋值会覆盖原有的强类型，变为自由类型
                variableTypes.remove(varName);
            }
            debugger.debug("Free type assignment - no type constraints");
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.debug("Var assigned: " + varName + " = " + value);
        return null;
    }


    /**
     * 检查类型兼容性，不兼容时抛出异常
     */
    private void validateTypeCompatibility(String expectedType, Object value, String varName, int lineNumber, int columnNumber) {
        if (value == null) {
            debugger.debug("Null value allowed for any type");
            return; // null 可以赋值给任何类型
        }

        String actualType = getValueType(value);
        debugger.debug("Validating: " + expectedType + " <- " + actualType);

        if (!isTypeCompatible(expectedType, value)) {
            String errorMsg = "Type mismatch: cannot assign " + actualType +
                    " to variable '" + varName + "' of type " + expectedType;
            debugger.log(errorMsg);
            throw new VastExceptions.NotGrammarException(
                    errorMsg, lineNumber, columnNumber
            );
        }
        debugger.debug("Type compatibility OK");
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        Object result = evaluate(stmt.getExpression());
        this.lastResult = result;
        if (result != null) {
            debugger.debug("Expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        String importPath = stmt.getClassName();
        debugger.debug("Import: " + importPath);

        try {
            // 首先尝试作为外置库导入
            VastLibraryLoader loader = VastLibraryLoader.getInstance();
            boolean libraryLoaded = loader.loadLibraryFromImport(importPath, this.vm);

            if (libraryLoaded) {
                debugger.debug("External library loaded: " + importPath);
                return null;
            }

            // 如果外置库加载失败，尝试作为普通类导入
            Class<?> clazz = Class.forName(importPath);
            importedClasses.put(importPath, clazz);

            // 同时更新 VM 中的导入类
            if (vm != null) {
                vm.getImportedClasses().put(importPath, clazz);
            }

            debugger.debug("Class imported: " + importPath);

        } catch (ClassNotFoundException e) {
            // 静默处理类未找到异常，不抛出错误
            debugger.debug("Class not found: " + importPath);
        } catch (Exception e) {
            // 静默处理其他异常
            debugger.debug("Import failed: " + importPath);
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(LoopStatement stmt) {
        Object condition = evaluate(stmt.getCondition());

        debugger.debug("Loop condition: " + condition + " (type: " +
                (condition != null ? condition.getClass().getSimpleName() : "null") + ")");

        // 处理数字类型的循环条件（如 loop(10):）
        if (condition instanceof Number) {
            int count = ((Number) condition).intValue();

            debugger.debug("Loop count: " + count);

            for (int i = 0; i < count; i++) {
                debugger.debug("Loop iteration: " + (i + 1) + "/" + count);

                // 执行循环体中的所有语句
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            }
        }
        // 处理布尔类型的循环条件（如 loop(true): 或 loop(a > b):）
        else if (condition instanceof Boolean) {
            if ((Boolean) condition) {
                debugger.debug("Boolean condition is true, executing loop body");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.debug("Boolean condition is false, skipping loop");
            }
        }
        else {
            // 默认情况下，如果条件不是数字或布尔值，当作真值处理并执行一次
            if (condition != null) {
                debugger.debug("Non-boolean condition, executing loop body once");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.debug("Null condition, skipping loop");
            }
        }
        return null;
    }

    @Override
    public Void visitUseStatement(UseStatement stmt) {
        Expression methodCall = stmt.getMethodCall();

        debugger.debug("Use statement executing method call");

        // 直接执行方法调用表达式
        Object result = evaluate(methodCall);
        this.lastResult = result;

        if (result != null) {
            debugger.debug("Use statement result: " + result);
        }

        return null;
    }

    @Override
    public Void visitSwapStatement(SwapStatement stmt) {
        String varA = stmt.getVarA().getName();
        String varB = stmt.getVarB().getName();

        debugger.debug("Swap: " + varA + ", " + varB);

        if (!variables.containsKey(varA)) {
            throw VastExceptions.NonExistentObject.variableNotFound(varA);
        }
        if (!variables.containsKey(varB)) {
            throw VastExceptions.NonExistentObject.variableNotFound(varB);
        }

        Object valueA = variables.get(varA);
        Object valueB = variables.get(varB);

        variables.put(varA, valueB);
        variables.put(varB, valueA);

        debugger.debug("Swapped: " + varA + " = " + variables.get(varA) + ", " + varB + " = " + variables.get(varB));
        return null;
    }

    /**
     * 调用内部库方法
     */
    private Object callInternalMethod(String className, String methodName, Object[] args) {
        try {
            debugger.debug("Calling internal method: " + className + "." + methodName + // 改为 debug
                    " with " + args.length + " arguments");

            Class<?> clazz = findClass(className);
            if (clazz == null) {
                throw VastExceptions.NonExistentObject.classNotFound(className);
            }

            Method method = findBestMethod(clazz, methodName, args);
            if (method == null) {
                String suggestion = errorSuggestor.suggestForUnknownMethod(methodName, className);
                throw new VastExceptions.NonExistentObject(suggestion);
            }

            // 处理可变参数
            Object[] convertedArgs = convertArgumentsForMethod(method, args);

            Object result = method.invoke(null, convertedArgs);
            debugger.debug("Method call result: " + result);
            return result;

        } catch (VastExceptions.VastRuntimeException e) {
            throw e;
        } catch (Exception e) {
            debugger.error("Failed to call method " + className + "." + methodName + ": " + e.getMessage());
            if (debugger.isShowStackTrace()) {
                e.printStackTrace();
            }
            throw new VastExceptions.UnknownVastException("Failed to call method " + className + "." + methodName, e);
        }
    }

    /**
     * 转换参数以匹配方法签名
     */
    private Object[] convertArgumentsForMethod(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        boolean isVarArgs = method.isVarArgs();

        if (!isVarArgs) {
            // 非可变参数方法，直接转换每个参数
            Object[] converted = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                converted[i] = convertArgument(args[i], paramTypes[i]);
            }
            return converted;
        } else {
            // 可变参数方法
            // 固定参数个数
            int fixedParams = paramTypes.length - 1;
            // 检查参数个数是否足够
            if (args.length < fixedParams) {
                throw new VastExceptions.NotGrammarException("Insufficient arguments for method: " + method.getName());
            }

            Object[] converted = new Object[paramTypes.length];
            // 转换固定参数
            for (int i = 0; i < fixedParams; i++) {
                converted[i] = convertArgument(args[i], paramTypes[i]);
            }

            // 处理可变参数
            Class<?> varArgType = paramTypes[fixedParams].getComponentType();
            int varArgCount = args.length - fixedParams;
            Object varArgsArray = Array.newInstance(varArgType, varArgCount);
            for (int i = 0; i < varArgCount; i++) {
                Array.set(varArgsArray, i, convertArgument(args[fixedParams + i], varArgType));
            }
            converted[fixedParams] = varArgsArray;

            return converted;
        }
    }

    /**
     * 转换单个参数
     */
    private Object convertArgument(Object arg, Class<?> targetType) {
        if (arg == null) {
            return null;
        }

        if (targetType.isInstance(arg)) {
            return arg;
        }

        // 处理常见的类型转换
        if (targetType == String.class) {
            return arg.toString();
        }

        if (targetType == Integer.class || targetType == int.class) {
            if (arg instanceof Number) {
                return ((Number) arg).intValue();
            } else if (arg instanceof String) {
                return Integer.parseInt((String) arg);
            }
        }

        if (targetType == Double.class || targetType == double.class) {
            if (arg instanceof Number) {
                return ((Number) arg).doubleValue();
            } else if (arg instanceof String) {
                return Double.parseDouble((String) arg);
            }
        }

        // 如果无法转换，返回原值（让反射处理）
        return arg;
    }

    /**
     * 查找类
     */
    private Class<?> findClass(String className) {
        // 首先检查内置类
        Map<String, Class<?>> builtinClasses = VastVM.getBuiltinClasses();
        if (builtinClasses.containsKey(className)) {
            return builtinClasses.get(className);
        }

        // 然后检查导入的类
        if (importedClasses.containsKey(className)) {
            return importedClasses.get(className);
        }

        // 最后尝试动态加载
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            String suggestion = errorSuggestor.suggestForUnknownClass(className);
            throw new VastExceptions.NonExistentObject(suggestion);// 类不存在则优雅地抛出异常
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

        // 第二步：寻找最匹配的方法
        Method bestMethod = null;
        int bestScore = Integer.MAX_VALUE;

        for (Method method : candidateMethods) {
            int score = calculateMethodMatchScore(method, args);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestMethod = method;
            }
        }

        return bestMethod;
    }

    private int calculateMethodMatchScore(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        boolean isVarArgs = method.isVarArgs();

        // 如果不是可变参数方法，参数个数必须完全匹配
        if (!isVarArgs) {
            if (paramTypes.length != args.length) {
                return -1;
            }
        } else {
            // 可变参数方法：参数个数必须至少是固定参数个数
            if (args.length < paramTypes.length - 1) {
                return -1;
            }
        }

        int score = 0;
        // 匹配固定参数
        int i = 0;
        for (; i < paramTypes.length - (isVarArgs ? 1 : 0); i++) {
            if (!isTypeCompatible(paramTypes[i], args[i])) {
                return -1;
            }
            // 计算类型转换代价（简单起见，这里我们只计算是否需要转换，0表示完全匹配，1表示需要转换）
            if (args[i] != null && !paramTypes[i].equals(args[i].getClass())) {
                score += 1;
            }
        }

        // 匹配可变参数
        if (isVarArgs) {
            Class<?> varArgType = paramTypes[paramTypes.length - 1].getComponentType();
            for (; i < args.length; i++) {
                if (!isTypeCompatible(varArgType, args[i])) {
                    return -1;
                }
                if (args[i] != null && !varArgType.equals(args[i].getClass())) {
                    score += 1;
                }
            }
        }

        return score;
    }

    /**
     * 检查类型兼容性
     */
    private boolean isTypeCompatible(Class<?> paramType, Object arg) {
        if (arg == null) return !paramType.isPrimitive();

        // 对于 Object 类型，接受任何参数
        if (paramType == Object.class) {
            return true;
        }

        // 对于可变参数的元素类型，同样宽松处理
        if (paramType.isArray() && arg != null) {
            // 简化处理：如果参数是数组且可变参数元素类型是 Object，接受任何数组
            Class<?> componentType = paramType.getComponentType();
            if (componentType == Object.class) {
                return true;
            }
        }

        if (paramType.isPrimitive()) {
            return (paramType == int.class && arg instanceof Integer) ||
                    (paramType == boolean.class && arg instanceof Boolean) ||
                    (paramType == double.class && arg instanceof Double) ||
                    (paramType == long.class && arg instanceof Long) ||
                    (paramType == float.class && arg instanceof Float) ||
                    (paramType == char.class && arg instanceof Character) ||
                    (paramType == byte.class && arg instanceof Byte) ||
                    (paramType == short.class && arg instanceof Short);
        }

        return paramType.isInstance(arg) ||
                (paramType == String.class && arg instanceof String) ||
                (Number.class.isAssignableFrom(paramType) && arg instanceof Number);
    }

    /**
     * 检查值是否与声明的类型兼容
     */
    private boolean isTypeCompatible(String expectedType, Object value) {
        if (value == null) return true; // null 可以赋值给任何类型

        switch (expectedType) {
            case "int":
            case "int32":
                return value instanceof Integer ||
                        (value instanceof String && canParseAsInt((String) value));
            case "int8":
            case "byte":
                if (value instanceof Byte) return true;
                if (value instanceof Integer) {
                    int intValue = (Integer) value;
                    return intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE;
                }
                if (value instanceof String) {
                    return canParseAsByte((String) value);
                }
                return false;
            case "int16":
            case "short":
                if (value instanceof Short) return true;
                if (value instanceof Integer) {
                    int intValue = (Integer) value;
                    return intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE;
                }
                if (value instanceof String) {
                    return canParseAsShort((String) value);
                }
                return false;
            case "int64":
            case "long":
                return value instanceof Long || value instanceof Integer ||
                        (value instanceof String && canParseAsLong((String) value));
            case "double":
                return value instanceof Double || value instanceof Integer ||
                        (value instanceof String && canParseAsDouble((String) value));
            case "float":
                return value instanceof Float || value instanceof Integer ||
                        value instanceof Double || (value instanceof String && canParseAsFloat((String) value));
            case "bool":
            case "boolean":
                return value instanceof Boolean ||
                        (value instanceof String && canParseAsBoolean((String) value));
            case "string":
                return true; // 任何类型都可以隐式转换为字符串
            case "char":
                return value instanceof Character ||
                        (value instanceof String && ((String) value).length() == 1) ||
                        (value instanceof Integer && isValidCharCode((Integer) value));
            case "large":
                // large 可以接受任何数值类型或可解析为数字的字符串
                return value instanceof Integer || value instanceof Long ||
                        value instanceof java.math.BigInteger ||
                        (value instanceof String && canParseAsBigInteger((String) value));
            default:
                // 未知类型，允许任何赋值
                return true;
        }
    }

    /**
     * 获取值的类型描述
     */
    private String getValueType(Object value) {
        if (value == null) return "null";
        if (value instanceof Integer) return "int";
        if (value instanceof Double) return "double";
        if (value instanceof Float) return "float";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        if (value instanceof Character) return "char";
        if (value instanceof Byte) return "byte";
        if (value instanceof Short) return "short";
        if (value instanceof Long) return "long";
        if (value instanceof java.math.BigInteger) return "large";
        return value.getClass().getSimpleName();
    }

    /**
     * 计算表达式的值
     */
    private Object evaluate(Expression expr) {
        return expr.accept(new ExpressionEvaluator());
    }

    @Override
    public Void visitTypeCastExpression(TypeCastExpression expr) {
        Object result = evaluate(expr);
        this.lastResult = result;
        if (result != null) {
            debugger.debug("Type cast expression result: " + result);
        }
        return null;
    }

    /**
     * 表达式求值器
     */
    private class ExpressionEvaluator implements ASTVisitor<Object> {
        @Override
        public Object visitLiteralExpression(LiteralExpression expr) {
            return expr.getValue();
        }

        @Override
        public Object visitTypeCastExpression(TypeCastExpression expr) {
            Object value = evaluate(expr.getExpression());
            String targetType = expr.getTargetType();

            debugger.debug("Casting " + value + " (" + getValueType(value) +
                    ") to " + targetType);

            Object result = Interpreter.this.performTypeCast(value, targetType,
                    expr.getLineNumber(), expr.getColumnNumber(),
                    expr.isExplicit());

            debugger.debug("Result: " + result + " (" + getValueType(result) + ")");

            return result;
        }

        @Override
        public Object visitRootExpression(RootExpression expr) {
            Object value = evaluate(expr.getExpression());

            debugger.debug("Root expression: " + expr + ", value: " + value);

            // 创建平方根表达式
            return performRootOperation(value,
                    expr.getLineNumber(), expr.getColumnNumber());
        }

        /**
         * 执行开方运算
         */
        private Object performRootOperation(Object value, int lineNumber, int columnNumber) {
            double numericValue = toDouble(value);

            if (numericValue < 0) {
                throw VastExceptions.MathError.invalidSquareRoot(numericValue, lineNumber, columnNumber);
            }

            double result = Math.sqrt(numericValue);

            // 尝试简化结果
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return (int) result;  // 整数结果
            }

            // 尝试表示为分数
            Fraction fraction = Fraction.fromNumber(result);
            if (isExactFraction(fraction, result)) {
                return fraction;  // 精确的分数表示
            }

            return result;  // 浮点数结果
        }

        @Override
        public Object visitInlineTypeCastStatement(InlineTypeCastStatement stmt) {
            // 表达式求值器不应该处理语句，这里应该不会被执行到
            throw new VastExceptions.NotGrammarException(
                    "Inline type cast statements should not be evaluated as expressions",
                    stmt.getLineNumber(), stmt.getColumnNumber()
            );
        }

        @Override
        public Object visitVariableExpression(VariableExpression expr) {
            String name = expr.getName();

            // 首先检查是否是内置类名
            Map<String, Class<?>> builtinClasses = VastVM.getBuiltinClasses();
            if (builtinClasses.containsKey(name)) {
                // 返回类名字符串，这样在成员访问时能识别为类
                return name;
            }

            // 然后检查是否是导入的类名
            if (importedClasses.containsKey(name)) {
                return name;
            }

            // 检查是否是唯一的静态方法名
            String className = resolveClassNameForMethod(name, expr.getLineNumber(), expr.getColumnNumber());
            if (className != null) {
                debugger.debug("Variable expression resolved as static method: " + name + " -> " + className); // 改为 debug
                return name; // 返回方法名字符串，在函数调用中处理
            }

            // 最后检查变量
            if (!variables.containsKey(name)) {
                String suggestion = errorSuggestor.suggestForUnknownVariable(name);
                throw new VastExceptions.NonExistentObject(suggestion);
            }
            return variables.get(name);
        }

        @Override
        public Object visitBitwiseExpression(BitwiseExpression expr) {
            Object left = evaluate(expr.getLeft());
            Object right = evaluate(expr.getRight());

            switch (expr.getOperator()) {
                case "&": return performBitwiseAnd(left, right);
                case "|": return performBitwiseOr(left, right);
                case "^": return performBitwiseXor(left, right);
                default:
                    throw new VastExceptions.NotGrammarException(
                            "Unknown bitwise operator: " + expr.getOperator(),
                            expr.getLineNumber(), expr.getColumnNumber()
                    );
            }
        }

        @Override
        public Object visitFractionExpression(FractionExpression expr) {
            Object value = evaluate(expr.getExpression());
            boolean isPermanent = expr.isPermanent();

            debugger.debug("Fraction expression: " + expr + ", value: " + value +
                    ", permanent: " + isPermanent);

            // 创建分数对象
            Fraction fraction = createFraction(value,
                    expr.getLineNumber(), expr.getColumnNumber());
            fraction.setPermanent(isPermanent);

            return fraction;
        }

        @Override
        public Object visitMemberAccessExpression(MemberAccessExpression expr) {
            Object left = evaluate(expr.getObject());
            String memberName = expr.getMemberName();

            // 如果是变量访问类静态成员（如 Sys.printl）
            if (left instanceof String) {
                String className = (String) left;
                // 返回一个包装对象，包含类名和成员名
                return new StaticMethodReference(className, memberName);
            }

            // 其他情况处理...
            throw new VastExceptions.NotGrammarException(
                    "Unsupported member access: " + expr,
                    expr.getLineNumber(),
                    expr.getColumnNumber()
            );
        }

        @Override
        public Object visitFunctionCallExpression(FunctionCallExpression expr) {
            Object callee = evaluate(expr.getCallee());

            // 计算所有参数
            Object[] args = new Object[expr.getArguments().size()];
            for (int i = 0; i < expr.getArguments().size(); i++) {
                args[i] = evaluate(expr.getArguments().get(i));
            }

            // 处理静态方法调用（如 Sys.print）
            if (callee instanceof StaticMethodReference) {
                StaticMethodReference methodRef = (StaticMethodReference) callee;
                return callInternalMethod(methodRef.getClassName(), methodRef.getMethodName(), args);
            }

            // 处理省略类名的静态方法调用（如 printl()）
            if (callee instanceof String) {
                String methodName = (String) callee;
                String className = resolveClassNameForMethod(methodName,
                        expr.getLineNumber(), expr.getColumnNumber());

                if (className != null) {
                    debugger.debug("Resolved method '" + methodName + "' to class: " + className); // 改为 debug
                    return callInternalMethod(className, methodName, args);
                }
            }

            // 其他类型的函数调用...
            throw new VastExceptions.NotGrammarException(
                    "Unsupported function call: " + expr,
                    expr.getLineNumber(),
                    expr.getColumnNumber()
            );
        }

        @Override
        public Object visitMethodCallExpression(MethodCallExpression expr) {
            String className = expr.getClassName().getName();
            String methodName = expr.getMethodName();

            // 计算所有参数
            Object[] args = new Object[expr.getArguments().size()];
            for (int i = 0; i < expr.getArguments().size(); i++) {
                args[i] = evaluate(expr.getArguments().get(i));
            }

            // 调用内部方法
            return Interpreter.this.callInternalMethod(className, methodName, args);
        }

        @Override
        public Object visitBinaryExpression(BinaryExpression expr) {
            Object left = evaluate(expr.getLeft());
            Object right = evaluate(expr.getRight());

            switch (expr.getOperator()) {
                case "+":
                    return performAddition(left, right);
                case "-":
                    return performSubtraction(left, right);
                case "*":
                    return performMultiplication(left, right);
                case "/":
                    return performDivision(left, right);
                case "**":
                    return performPower(left, right);
                case "//":
                    return performIntegerDivision(left, right);  // 地板除
                case "%":
                    return performModulo(left, right);
                case "++":
                    return performNumberConcatenation(left, right);
                case "==":
                    return left.equals(right);
                case "!=":
                    return !left.equals(right);
                case ">":
                    return compareValues(left, right) > 0;
                case "<":
                    return compareValues(left, right) < 0;
                case ">=":
                    return compareValues(left, right) >= 0;
                case "<=":
                    return compareValues(left, right) <= 0;
                case "&&":
                    return toBoolean(left) && toBoolean(right);
                case "||":
                    return toBoolean(left) || toBoolean(right);
                default:
                    throw new VastExceptions.NotGrammarException(
                            "Unknown operator: " + expr.getOperator(),
                            expr.getLineNumber(),
                            expr.getColumnNumber()
                    );
            }
        }

        @Override
        public Object visitUnaryExpression(UnaryExpression expr) {
            Object right = evaluate(expr.getRight());

            switch (expr.getOperator()) {
                case "++":  // 前缀自增
                    return handlePrefixIncrement(expr.getRight(), 1);
                case "--":  // 前缀自减
                    return handlePrefixIncrement(expr.getRight(), -1);
                case "++_POSTFIX":  // 后缀自增
                    return handlePostfixIncrement(expr.getRight(), 1);
                case "--_POSTFIX":  // 后缀自减
                    return handlePostfixIncrement(expr.getRight(), -1);
                case "-":
                    if (right instanceof Integer) return -(Integer) right;
                    if (right instanceof Double) return -(Double) right;
                    throw new VastExceptions.MathError(
                            "Unary - requires numeric operand",
                            "Operand type: " + (right != null ? right.getClass().getSimpleName() : "null")
                    );
                case "!":
                    if (right instanceof Boolean) return !(Boolean) right;
                    throw new VastExceptions.NotGrammarException(
                            "Unary ! requires boolean operand",
                            "Operand type: " + (right != null ? right.getClass().getSimpleName() : "null"),
                            expr.getLineNumber(),
                            expr.getColumnNumber()
                    );
                case "~":  // 按位取反
                    if (right instanceof Integer) {
                        return ~(Integer) right;
                    }
                    throw new VastExceptions.MathError(
                            "Unary ~ requires integer operand",
                            "Operand type: " + (right != null ? right.getClass().getSimpleName() : "null")
                    );
                default:
                    throw new VastExceptions.NotGrammarException(
                            "Unknown unary operator: " + expr.getOperator(),
                            expr.getLineNumber(),
                            expr.getColumnNumber()
                    );
            }
        }

        /**
         * 处理前缀自增/自减
         */
        private Object handlePrefixIncrement(Expression expr, int increment) {
            if (!(expr instanceof VariableExpression)) {
                throw new VastExceptions.NotGrammarException(
                        "Increment/decrement operand must be a variable",
                        expr.getLineNumber(), expr.getColumnNumber()
                );
            }

            String varName = ((VariableExpression) expr).getName();
            Object currentValue = variables.get(varName);

            if (currentValue == null) {
                throw VastExceptions.NonExistentObject.variableNotFound(varName);
            }

            Object newValue = performIncrement(currentValue, increment);
            variables.put(varName, newValue);

            return newValue;
        }

        /**
         * 处理后缀自增/自减
         */
        private Object handlePostfixIncrement(Expression expr, int increment) {
            if (!(expr instanceof VariableExpression)) {
                throw new VastExceptions.NotGrammarException(
                        "Increment/decrement operand must be a variable",
                        expr.getLineNumber(), expr.getColumnNumber()
                );
            }

            String varName = ((VariableExpression) expr).getName();
            Object currentValue = variables.get(varName);

            if (currentValue == null) {
                throw VastExceptions.NonExistentObject.variableNotFound(varName);
            }

            Object newValue = performIncrement(currentValue, increment);
            variables.put(varName, newValue);

            // 后缀运算符返回原始值
            return currentValue;
        }

        /**
         * 执行实际的增量操作
         */
        private Object performIncrement(Object value, int increment) {
            if (value instanceof Integer) {
                return (Integer) value + increment;
            }
            if (value instanceof Double) {
                return (Double) value + increment;
            }
            if (value instanceof Fraction) {
                Fraction fraction = (Fraction) value;
                return new Fraction(fraction.getNumerator() + increment * fraction.getDenominator(),
                        fraction.getDenominator());
            }

            throw new VastExceptions.MathError(
                    "Cannot increment/decrement non-numeric value: " + value,
                    "Increment operation"
            );
        }

        @Override
        public Object visitAssignmentExpression(AssignmentExpression expr) {
            Object value = evaluate(expr.getValue());
            String varName = expr.getVariableName();

            debugger.debug("Assignment expression: " + varName + " = " + value +
                    " (value type: " + getValueType(value) + ")");

            // 严格的类型检查
            if (Interpreter.this.variableTypes.containsKey(varName)) {
                String expectedType = Interpreter.this.variableTypes.get(varName);
                debugger.debug("Variable '" + varName + "' has type constraint: " + expectedType);

                Interpreter.this.validateTypeCompatibility(expectedType, value, varName,
                        expr.getLineNumber(), expr.getColumnNumber());
                debugger.debug("Type check PASSED for " + varName);
            } else {
                debugger.debug("No type constraint for " + varName + ", allowing assignment");
            }

            variables.put(varName, value);
            return value;
        }

        // 其他访问方法不需要实现
        @Override
        public Void visitVariableDeclaration(VariableDeclaration stmt) { return null; }
        @Override
        public Void visitAssignmentStatement(AssignmentStatement stmt) { return null; }
        @Override
        public Void visitExpressionStatement(ExpressionStatement stmt) { return null; }
        @Override
        public Void visitImportStatement(ImportStatement stmt) { return null; }
        @Override
        public Void visitLoopStatement(LoopStatement stmt) { return null; }
        @Override
        public Void visitUseStatement(UseStatement stmt) { return null; }
        @Override
        public Void visitSwapStatement(SwapStatement stmt) { return null; }


        //====================辅助方法=====================

        private Fraction createFraction(Object value, int lineNumber, int columnNumber) {
            try {
                if (value instanceof Integer) {
                    return new Fraction((Integer) value, 1);
                } else if (value instanceof Double) {
                    return Fraction.fromNumber(value);
                } else if (value instanceof Fraction) {
                    return (Fraction) value;  // 已经是分数，直接返回
                } else {
                    throw new VastExceptions.MathError(
                            "Cannot create fraction from " + getValueType(value),
                            lineNumber, columnNumber
                    );
                }
            } catch (Exception e) {
                throw new VastExceptions.MathError(
                        "Failed to create fraction: " + e.getMessage(),
                        lineNumber, columnNumber
                );
            }
        }

        private Object performBitwiseAnd(Object left, Object right) {
            checkIntegerOperands(left, right, "bitwise AND");
            int leftInt = toInt(left);
            int rightInt = toInt(right);
            return leftInt & rightInt;
        }

        private Object performBitwiseOr(Object left, Object right) {
            checkIntegerOperands(left, right, "bitwise OR");
            int leftInt = toInt(left);
            int rightInt = toInt(right);
            return leftInt | rightInt;
        }

        private Object performBitwiseXor(Object left, Object right) {
            checkIntegerOperands(left, right, "bitwise XOR");
            int leftInt = toInt(left);
            int rightInt = toInt(right);
            return leftInt ^ rightInt;
        }

        private void checkIntegerOperands(Object left, Object right, String operation) {
            if (!(left instanceof Integer) || !(right instanceof Integer)) {
                throw new VastExceptions.MathError(
                        "Operands must be integers for " + operation,
                        "Got: " + getValueType(left) + " and " + getValueType(right)
                );
            }
        }

        private Object performPower(Object left, Object right) {
            // 处理分数指数（开方运算）
            if (right instanceof Fraction) {
                return performFractionPower(left, (Fraction) right);
            }

            checkNumberOperands(left, right);
            double base = toDouble(left);
            double exponent = toDouble(right);
            double result = Math.pow(base, exponent);

            if (left instanceof Integer && right instanceof Integer && exponent >= 0) {
                return (int) result;
            }
            return result;
        }

        /**
         * 处理分数指数的幂运算（开方运算）
         */
        private Object performFractionPower(Object base, Fraction exponent) {
            double baseValue = toDouble(base);

            // 检查指数是否为 1/n 形式（开n次方）
            if (exponent.getNumerator() == 1) {
                int root = exponent.getDenominator();

                if (root == 2) {
                    // 平方根
                    if (baseValue < 0) {
                        throw VastExceptions.MathError.invalidSquareRoot(baseValue);
                    }
                    double result = Math.sqrt(baseValue);
                    return simplifyRootResult(result);
                } else if (root == 3) {
                    // 立方根
                    double result = Math.cbrt(baseValue);
                    return simplifyRootResult(result);
                } else {
                    // n次方根
                    if (baseValue < 0 && root % 2 == 0) {
                        throw VastExceptions.MathError.invalidSquareRoot(baseValue);
                    }
                    double result = Math.pow(baseValue, 1.0 / root);
                    return simplifyRootResult(result);
                }
            } else {
                // 一般的分数指数：a^(m/n) = (a^m)的n次方根
                double numeratorPower = Math.pow(baseValue, exponent.getNumerator());
                return performFractionPower(numeratorPower,
                        new Fraction(1, exponent.getDenominator()));
            }
        }

        /**
         * 简化根式结果：如果结果是整数则返回整数，否则返回分数或浮点数
         */
        private Object simplifyRootResult(double result) {
            // 检查是否为整数
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return (int) result;
            }

            // 尝试表示为分数
            Fraction fraction = Fraction.fromNumber(result);
            if (isExactFraction(fraction, result)) {
                return fraction;
            }

            return result;
        }

        /**
         * 检查分数是否精确表示原值
         */
        private boolean isExactFraction(Fraction fraction, double original) {
            double fractionValue = fraction.toDouble();
            return Math.abs(fractionValue - original) < 1e-10;
        }

        private Object performIntegerDivision(Object left, Object right) {
            checkNumberOperands(left, right);

            // 处理分数的情况
            if (left instanceof Fraction || right instanceof Fraction) {
                Fraction leftFraction = toFraction(left);
                Fraction rightFraction = toFraction(right);

                if (rightFraction.getNumerator() == 0) {
                    throw VastExceptions.MathError.divisionByZero();
                }

                // 分数地板除：转换为浮点数除法后取整
                double result = leftFraction.toDouble() / rightFraction.toDouble();
                return (int) Math.floor(result);
            }

            // 常规地板除
            if (toDouble(right) == 0) {
                throw VastExceptions.MathError.divisionByZero();
            }

            double result = toDouble(left) / toDouble(right);
            return (int) Math.floor(result);
        }

        private Object performNumberConcatenation(Object left, Object right) {
            if (right == null || (right instanceof String && ((String) right).isEmpty())) {
                if (left instanceof Integer) {
                    return (Integer) left + 1;
                } else if (left instanceof Double) {
                    return (Double) left + 1.0;
                } else {
                    throw new VastExceptions.MathError(
                            "Cannot increment non-numeric value: " + left,
                            "Increment operation"
                    );
                }
            }

            if (left == null || (left instanceof String && ((String) left).isEmpty())) {
                if (right instanceof Integer) {
                    return (Integer) right + 1;
                } else if (right instanceof Double) {
                    return (Double) right + 1.0;
                } else {
                    throw new VastExceptions.MathError(
                            "Cannot increment non-numeric value: " + right,
                            "Increment operation"
                    );
                }
            }

            String leftStr = String.valueOf(left).replaceAll("\\.0*$", "").replaceAll("\\.", "");
            String rightStr = String.valueOf(right).replaceAll("\\.0*$", "").replaceAll("\\.", "");

            try {
                return Integer.parseInt(leftStr + rightStr);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(leftStr + rightStr);
                } catch (NumberFormatException e2) {
                    throw new VastExceptions.MathError(
                            "Number concatenation result is too large: " + leftStr + rightStr,
                            "Concatenation operation"
                    );
                }
            }
        }

        private Object performModulo(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw VastExceptions.MathError.divisionByZero();
            }
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) % toDouble(right);
            }
            return toInt(left) % toInt(right);
        }

        private Object performStringMultiplication(Object left, Object right) {
            if (left instanceof String && right instanceof Number) {
                String str = (String) left;
                int count = toInt(right);
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    result.append(str);
                }
                return result.toString();
            }

            if (left instanceof Number && right instanceof String) {
                return performStringMultiplication(right, left);
            }

            throw new VastExceptions.NotGrammarException(
                    "String repetition operation",
                    "requires string and number operands, but got: " + left + " * " + right
            );
        }

        private Object performAddition(Object left, Object right) {
            // 处理 Fraction 对象的加法
            if (left instanceof Fraction || right instanceof Fraction) {
                return performFractionAddition(left, right);
            }

            if (left instanceof Double && right instanceof Double) {
                return (Double) left + (Double) right;
            }
            if (left instanceof Integer && right instanceof Integer) {
                return (Integer) left + (Integer) right;
            }
            if (left instanceof String || right instanceof String) {
                return stringify(left) + stringify(right);
            }
            throw new VastExceptions.MathError(
                    "Operands must be two numbers or two strings",
                    "Addition operation with operands: " + left + " + " + right
            );
        }

        /**
         * 处理 Fraction 对象的加法运算
         */
        private Object performFractionAddition(Object left, Object right) {
            Fraction leftFraction = toFraction(left);
            Fraction rightFraction = toFraction(right);

            // 使用 Sys 类中的分数加法方法
            return Sys.fractionAdd(leftFraction, rightFraction);
        }

        /**
         * 将对象转换为 Fraction
         */
        private Fraction toFraction(Object obj) {
            if (obj instanceof Fraction) {
                return (Fraction) obj;
            }
            if (obj instanceof Integer) {
                return new Fraction((Integer) obj, 1);
            }
            if (obj instanceof Double) {
                return Fraction.fromNumber(obj);
            }
            if (obj instanceof String) {
                return Sys.parseFraction((String) obj);
            }
            throw new VastExceptions.MathError(
                    "Cannot convert to fraction: " + obj,
                    "Type conversion"
            );
        }

        private Object performSubtraction(Object left, Object right) {
            // 处理 Fraction 对象的减法
            if (left instanceof Fraction || right instanceof Fraction) {
                Fraction leftFraction = toFraction(left);
                Fraction rightFraction = toFraction(right);
                return Sys.fractionSubtract(leftFraction, rightFraction);
            }

            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) - toDouble(right);
            }
            return toInt(left) - toInt(right);
        }

        private Object performMultiplication(Object left, Object right) {
            // 处理字符串重复运算
            if ((left instanceof String && right instanceof Number) ||
                    (left instanceof Number && right instanceof String)) {
                return performStringMultiplication(left, right);
            }

            // 处理 Fraction 对象的乘法
            if (left instanceof Fraction || right instanceof Fraction) {
                Fraction leftFraction = toFraction(left);
                Fraction rightFraction = toFraction(right);
                return Sys.fractionMultiply(leftFraction, rightFraction);
            }

            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) * toDouble(right);
            }
            return toInt(left) * toInt(right);
        }

        private Object performDivision(Object left, Object right) {
            // 处理 Fraction 对象的除法
            if (left instanceof Fraction || right instanceof Fraction) {
                Fraction leftFraction = toFraction(left);
                Fraction rightFraction = toFraction(right);
                if (rightFraction.getNumerator() == 0) {
                    throw VastExceptions.MathError.divisionByZero();
                }
                return Sys.fractionDivide(leftFraction, rightFraction);
            }

            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw VastExceptions.MathError.divisionByZero();
            }
            return toDouble(left) / toDouble(right);
        }

        private int compareValues(Object left, Object right) {
            // 处理 Fraction 对象的比较
            if (left instanceof Fraction || right instanceof Fraction) {
                Fraction leftFraction = toFraction(left);
                Fraction rightFraction = toFraction(right);
                return Sys.fractionCompare(leftFraction, rightFraction);
            }

            if (left instanceof Double && right instanceof Double) {
                return Double.compare((Double) left, (Double) right);
            }
            if (left instanceof Integer && right instanceof Integer) {
                return Integer.compare((Integer) left, (Integer) right);
            }
            if (left instanceof String && right instanceof String) {
                return ((String) left).compareTo((String) right);
            }
            throw new VastExceptions.NotGrammarException(
                    "Comparison operation",
                    "Cannot compare values of different types: " + left + " and " + right
            );
        }

        private void checkNumberOperands(Object left, Object right) {
            if (!(left instanceof Number && right instanceof Number)) {
                throw new VastExceptions.MathError(
                        "Operands must be numbers",
                        "Operation with operands: " + left + " and " + right
                );
            }
        }

        private double toDouble(Object obj) {
            if (obj instanceof Integer) return ((Integer) obj).doubleValue();
            if (obj instanceof Double) return (Double) obj;
            throw new VastExceptions.MathError(
                    "Cannot convert to double: " + obj,
                    "Type conversion"
            );
        }

        private int toInt(Object obj) {
            if (obj instanceof Integer) return (Integer) obj;
            if (obj instanceof Double) return ((Double) obj).intValue();
            throw new VastExceptions.MathError(
                    "Cannot convert to int: " + obj,
                    "Type conversion"
            );
        }

        private String stringify(Object object) {
            if (object == null) return "null";
            return object.toString();
        }

        private boolean toBoolean(Object obj) {
            if (obj instanceof Boolean) return (Boolean) obj;
            if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
            if (obj instanceof String) return !((String) obj).isEmpty();
            return obj != null;
        }
    }

    private boolean canParseAsInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsByte(String str) {
        try {
            Byte.parseByte(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsShort(String str) {
        try {
            Short.parseShort(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsLong(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsFloat(String str) {
        try {
            Float.parseFloat(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean canParseAsBoolean(String str) {
        String lower = str.toLowerCase();
        return lower.equals("true") || lower.equals("false") ||
                lower.equals("1") || lower.equals("0");
    }

    private boolean canParseAsBigInteger(String str) {
        try {
            new java.math.BigInteger(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidCharCode(int code) {
        return code >= Character.MIN_VALUE && code <= Character.MAX_VALUE;
    }

    /**
     * 执行类型转换
     */
    private Object performTypeCast(Object value, String targetType,
                                   int lineNumber, int columnNumber, boolean isExplicit) {
        if (value == null) {
            return null;
        }

        try {
            switch (targetType) {
                case "int":
                case "int32":
                    return castToInt(value, isExplicit, lineNumber, columnNumber);

                case "bool":
                case "boolean":
                    return castToBoolean(value, isExplicit, lineNumber, columnNumber);

                case "string":
                    return castToString(value);

                case "double":
                    return castToDouble(value, isExplicit, lineNumber, columnNumber);

                case "char":
                    return castToChar(value, isExplicit, lineNumber, columnNumber);

                default:
                    // 未知类型，返回原值
                    if (!isExplicit) {
                        debugger.warning("Unknown target type: " + targetType);
                    }
                    return value;
            }
        } catch (VastExceptions.VastRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new VastExceptions.MathError(
                    "Type cast failed: cannot convert " + getValueType(value) + " to " + targetType,
                    lineNumber, columnNumber
            );
        }
    }

    private Object performAutoConversion(Object value, String targetType,
                                         int lineNumber, int columnNumber) {
        if (value == null) return null;

        String actualType = getValueType(value);
        if (actualType.equals(targetType)) {
            return value; // 类型相同，无需转换
        }

        // 处理常见的自动类型转换
        switch (targetType) {
            case "int":
            case "int32":
                if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        // 这里不会发生，因为已经在 isTypeCompatible 中检查过
                        return 0;
                    }
                }
                break;
            case "double":
                if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
                if (value instanceof Integer) {
                    return ((Integer) value).doubleValue();
                }
                break;
            case "bool":
            case "boolean":
                if (value instanceof String) {
                    String str = ((String) value).toLowerCase();
                    return str.equals("true") || str.equals("1");
                }
                if (value instanceof Integer) {
                    return ((Integer) value) != 0;
                }
                break;
            // 可以添加更多自动转换规则...
        }

        return value; // 无法自动转换，返回原值
    }

    /**
     * 转换为整数
     */
    private Object castToInt(Object value, boolean isExplicit, int lineNumber, int columnNumber) {
        if (value instanceof Integer) return value;
        if (value instanceof Double) return ((Double) value).intValue();
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        if (value instanceof String) {
            String str = (String) value;
            try {
                // 尝试解析为整数
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                try {
                    // 尝试解析为浮点数然后取整
                    double d = Double.parseDouble(str);
                    if (!isExplicit) {
                        debugger.warning("Losing precision in implicit cast: " + str + " -> " + (int)d);
                    }
                    return (int) d;
                } catch (NumberFormatException e2) {
                    throw new VastExceptions.MathError(
                            "Cannot convert string to int: '" + str + "' contains non-numeric characters",
                            lineNumber, columnNumber
                    );
                }
            }
        }
        if (value instanceof Character) {
            return (int) (Character) value;
        }
        throw new VastExceptions.MathError(
                "Cannot convert " + getValueType(value) + " to int",
                lineNumber, columnNumber
        );
    }

    /**
     * 转换为布尔值
     */
    private Object castToBoolean(Object value, boolean isExplicit, int lineNumber, int columnNumber) {
        if (value instanceof Boolean) return value;
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            if (intValue == 0 || intValue == 1) {
                return intValue == 1;
            } else {
                throw new VastExceptions.MathError(
                        "Cannot convert integer to boolean: only 0 and 1 are allowed, got " + intValue,
                        lineNumber, columnNumber
                );
            }
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            if (str.equals("true") || str.equals("1")) return true;
            if (str.equals("false") || str.equals("0")) return false;
            throw new VastExceptions.MathError(
                    "Cannot convert string to boolean: only 'true', 'false', '1', '0' are allowed",
                    lineNumber, columnNumber
            );
        }
        throw new VastExceptions.MathError(
                "Cannot convert " + getValueType(value) + " to boolean",
                lineNumber, columnNumber
        );
    }

    /**
     * 转换为字符串
     */
    private Object castToString(Object value) {
        return value != null ? value.toString() : "null";
    }

    /**
     * 转换为双精度浮点数
     */
    private Object castToDouble(Object value, boolean isExplicit, int lineNumber, int columnNumber) {
        if (value instanceof Double) return value;
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new VastExceptions.MathError(
                        "Cannot convert string to double: '" + value + "' contains non-numeric characters",
                        lineNumber, columnNumber
                );
            }
        }
        throw new VastExceptions.MathError(
                "Cannot convert " + getValueType(value) + " to double",
                lineNumber, columnNumber
        );
    }

    /**
     * 转换为字符
     */
    private Object castToChar(Object value, boolean isExplicit, int lineNumber, int columnNumber) {
        if (value instanceof Character) return value;
        if (value instanceof Integer) {
            int code = (Integer) value;
            if (code >= Character.MIN_VALUE && code <= Character.MAX_VALUE) {
                return (char) code;
            } else {
                throw new VastExceptions.MathError(
                        "Integer value " + code + " is out of char range",
                        lineNumber, columnNumber
                );
            }
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > 0) {
                return str.charAt(0);
            } else {
                throw new VastExceptions.MathError(
                        "Cannot convert empty string to char",
                        lineNumber, columnNumber
                );
            }
        }
        throw new VastExceptions.MathError(
                "Cannot convert " + getValueType(value) + " to char",
                lineNumber, columnNumber
        );
    }

    private boolean isVarArgsMethod(Method method) {
        return method.isVarArgs();
    }

    // 静态方法引用辅助类
    private static class StaticMethodReference {
        private final String className;
        private final String methodName;

        public StaticMethodReference(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }

        @Override
        public String toString() {
            return className + "." + methodName;
        }
    }



    public Object getLastResult() {
        return lastResult;
    }
}