package com.vast.interpreter;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.internal.Debugger;
import com.vast.internal.Fraction;
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

    public Interpreter(VastVM vm) {
        this.vm = vm;
        this.debugger = vm.getDebugger();

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
            debugger.debug("Starting program parsing"); // 改为 debug
            program.accept(this);
            debugger.debug("Program parsed successfully with " + program.getStatements().size() + " statements"); // 改为 debug
        } catch (VastExceptions.VastRuntimeException error) {
            debugger.error("Runtime error: " + error.getUserFriendlyMessage());
            throw error;
        }
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

        debugger.log("Inline type cast: " + varName + " -> " + targetType + " = " + newValue);

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

        debugger.log("Assignment expression: " + varName + " = " + value);

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
        debugger.log("Bitwise expression: " + expr);
        return null;
    }

    @Override
    public Void visitFractionExpression(FractionExpression expr) {
        Object result = evaluate(expr);
        this.lastResult = result;
        if (result != null) {
            debugger.log("Fraction expression result: " + result);
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
            debugger.log("Method call result: " + result);
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

        debugger.log("Variable declaration: " + varName +
                ", type: " + typeHint + ", initial value: " + value +
                (stmt.isTypeCast() ? " (type cast)" : ""));

        // 强类型变量声明
        if (typeHint != null) {
            variableTypes.put(varName, typeHint);
            debugger.log("Registered type constraint: " + varName + " -> " + typeHint);

            // 严格验证初始值的类型
            if (value != null) {
                validateTypeCompatibility(typeHint, value, varName,
                        stmt.getLineNumber(), stmt.getColumnNumber());
            }
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.log("Var declared: " + varName + " = " + value +
                (typeHint != null ? " (type: " + typeHint + ")" : ""));
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatement stmt) {
        Object value = evaluate(stmt.getValue());
        String varName = stmt.getVariableName();
        String typeHint = stmt.getTypeHint();

        debugger.log("Assignment: " + varName + " = " + value +
                (typeHint != null ? " (strong type: " + typeHint + ")" : " (free type)"));

        if (typeHint != null) {
            // 强类型赋值 - 严格类型检查
            validateTypeCompatibility(typeHint, value, varName,
                    stmt.getLineNumber(), stmt.getColumnNumber());
            variableTypes.put(varName, typeHint);
            debugger.log("Strong type assignment PASSED");
        } else {
            // 自由类型赋值 - 不进行类型检查
            if (variableTypes.containsKey(varName)) {
                debugger.log("Warning: free type assignment to strongly typed variable " + varName);
                // 自由类型赋值会覆盖原有的强类型，变为自由类型
                variableTypes.remove(varName);
            }
            debugger.log("Free type assignment - no type constraints");
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.log("Var assigned: " + varName + " = " + value);
        return null;
    }


    /**
     * 检查类型兼容性，不兼容时抛出异常
     */
    private void validateTypeCompatibility(String expectedType, Object value, String varName, int lineNumber, int columnNumber) {
        if (value == null) {
            debugger.log("Null value allowed for any type");
            return; // null 可以赋值给任何类型
        }

        String actualType = getValueType(value);
        debugger.log("Validating: " + expectedType + " <- " + actualType);

        if (!isTypeCompatible(expectedType, value)) {
            String errorMsg = "Type mismatch: cannot assign " + actualType +
                    " to variable '" + varName + "' of type " + expectedType;
            debugger.log(errorMsg);
            throw new VastExceptions.NotGrammarException(
                    errorMsg, lineNumber, columnNumber
            );
        }
        debugger.log("Type compatibility OK");
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        Object result = evaluate(stmt.getExpression());
        this.lastResult = result;
        if (result != null) {
            debugger.log("Expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        String importPath = stmt.getClassName();
        debugger.log("Import: " + importPath);

        try {
            // 首先尝试作为外置库导入
            VastLibraryLoader loader = VastLibraryLoader.getInstance();
            boolean libraryLoaded = loader.loadLibraryFromImport(importPath, this.vm);

            if (libraryLoaded) {
                debugger.log("External library loaded: " + importPath);
                return null;
            }

            // 如果外置库加载失败，尝试作为普通类导入
            Class<?> clazz = Class.forName(importPath);
            importedClasses.put(importPath, clazz);

            // 同时更新 VM 中的导入类
            if (vm != null) {
                vm.getImportedClasses().put(importPath, clazz);
            }

            debugger.log("Class imported: " + importPath);

        } catch (ClassNotFoundException e) {
            // 静默处理类未找到异常，不抛出错误
            debugger.log("Class not found: " + importPath);
        } catch (Exception e) {
            // 静默处理其他异常
            debugger.log("Import failed: " + importPath);
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(LoopStatement stmt) {
        Object condition = evaluate(stmt.getCondition());

        debugger.log("Loop condition: " + condition + " (type: " +
                (condition != null ? condition.getClass().getSimpleName() : "null") + ")");

        // 处理数字类型的循环条件（如 loop(10):）
        if (condition instanceof Number) {
            int count = ((Number) condition).intValue();

            debugger.log("Loop count: " + count);

            for (int i = 0; i < count; i++) {
                debugger.log("Loop iteration: " + (i + 1) + "/" + count);

                // 执行循环体中的所有语句
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            }
        }
        // 处理布尔类型的循环条件（如 loop(true): 或 loop(a > b):）
        else if (condition instanceof Boolean) {
            if ((Boolean) condition) {
                debugger.log("Boolean condition is true, executing loop body");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.log("Boolean condition is false, skipping loop");
            }
        }
        else {
            // 默认情况下，如果条件不是数字或布尔值，当作真值处理并执行一次
            if (condition != null) {
                debugger.log("Non-boolean condition, executing loop body once");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.log("Null condition, skipping loop");
            }
        }
        return null;
    }

    @Override
    public Void visitUseStatement(UseStatement stmt) {
        Expression methodCall = stmt.getMethodCall();

        debugger.log("Use statement executing method call");

        // 直接执行方法调用表达式
        Object result = evaluate(methodCall);
        this.lastResult = result;

        if (result != null) {
            debugger.log("Use statement result: " + result);
        }

        return null;
    }

    @Override
    public Void visitSwapStatement(SwapStatement stmt) {
        String varA = stmt.getVarA().getName();
        String varB = stmt.getVarB().getName();

        debugger.log("Swap: " + varA + ", " + varB);

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

        debugger.log("Swapped: " + varA + " = " + variables.get(varA) + ", " + varB + " = " + variables.get(varB));
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
                // 提供更详细的错误信息
                debugger.error("Method not found: " + className + "." + methodName +
                        " with " + args.length + " arguments");
                debugger.error("Argument types: " + Arrays.toString(
                        Arrays.stream(args).map(arg -> arg != null ? arg.getClass().getSimpleName() : "null").toArray()
                ));

                // 列出所有可用的方法
                Method[] allMethods = clazz.getMethods();
                List<String> availableMethods = new ArrayList<>();
                for (Method m : allMethods) {
                    if (m.getName().equals(methodName)) {
                        availableMethods.add(methodName + Arrays.toString(m.getParameterTypes()));
                    }
                }
                if (!availableMethods.isEmpty()) {
                    debugger.error("Available overloads: " + availableMethods);
                }

                throw VastExceptions.NonExistentObject.methodNotFound(className, methodName);
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
            return null;
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
                return value instanceof Integer;
            case "int8":
            case "byte":
                if (value instanceof Byte) return true;
                if (value instanceof Integer) {
                    int intValue = (Integer) value;
                    return intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE;
                }
                return false;
            case "int16":
            case "short":
                if (value instanceof Short) return true;
                if (value instanceof Integer) {
                    int intValue = (Integer) value;
                    return intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE;
                }
                return false;
            case "int64":
            case "long":
                return value instanceof Long || value instanceof Integer;
            case "double":
                return value instanceof Double || value instanceof Integer;
            case "float":
                return value instanceof Float || value instanceof Integer || value instanceof Double;
            case "bool":
            case "boolean":
                return value instanceof Boolean;
            case "string":
                return value instanceof String;
            case "char":
                return value instanceof Character ||
                        (value instanceof String && ((String) value).length() == 1);
            case "large":
                // large 可以接受任何数值类型
                return value instanceof Integer || value instanceof Long ||
                        value instanceof java.math.BigInteger;
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
            debugger.log("Type cast expression result: " + result);
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

            debugger.log("Casting " + value + " (" + getValueType(value) +
                    ") to " + targetType);

            Object result = Interpreter.this.performTypeCast(value, targetType,
                    expr.getLineNumber(), expr.getColumnNumber(),
                    expr.isExplicit());

            debugger.log("Result: " + result + " (" + getValueType(result) + ")");

            return result;
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
                throw VastExceptions.NonExistentObject.variableNotFound(name);
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

            debugger.log("Fraction expression: " + expr + ", value: " + value +
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
                    return performIntegerDivision(left, right);
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
                case "++":
                    if (right instanceof Integer) return (Integer) right + 1;
                    if (right instanceof Double) return (Double) right + 1.0;
                    throw new VastExceptions.MathError(
                            "Unary ++ requires numeric operand",
                            "Operand type: " + (right != null ? right.getClass().getSimpleName() : "null")
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

        @Override
        public Object visitAssignmentExpression(AssignmentExpression expr) {
            Object value = evaluate(expr.getValue());
            String varName = expr.getVariableName();

            debugger.log("Assignment expression: " + varName + " = " + value +
                    " (value type: " + getValueType(value) + ")");

            // 严格的类型检查
            if (Interpreter.this.variableTypes.containsKey(varName)) {
                String expectedType = Interpreter.this.variableTypes.get(varName);
                debugger.log("Variable '" + varName + "' has type constraint: " + expectedType);

                Interpreter.this.validateTypeCompatibility(expectedType, value, varName,
                        expr.getLineNumber(), expr.getColumnNumber());
                debugger.log("Type check PASSED for " + varName);
            } else {
                debugger.log("No type constraint for " + varName + ", allowing assignment");
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
            checkNumberOperands(left, right);
            double base = toDouble(left);
            double exponent = toDouble(right);
            double result = Math.pow(base, exponent);

            if (left instanceof Integer && right instanceof Integer && exponent >= 0) {
                return (int) result;
            }
            return result;
        }

        private Object performIntegerDivision(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw VastExceptions.MathError.divisionByZero();
            }
            return toInt(left) / toInt(right);
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

        private Object performSubtraction(Object left, Object right) {
            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) - toDouble(right);
            }
            return toInt(left) - toInt(right);
        }

        private Object performMultiplication(Object left, Object right) {
            if ((left instanceof String && right instanceof Number) ||
                    (left instanceof Number && right instanceof String)) {
                return performStringMultiplication(left, right);
            }

            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) * toDouble(right);
            }
            return toInt(left) * toInt(right);
        }

        private Object performDivision(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw VastExceptions.MathError.divisionByZero();
            }
            return toDouble(left) / toDouble(right);
        }



        private int compareValues(Object left, Object right) {
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