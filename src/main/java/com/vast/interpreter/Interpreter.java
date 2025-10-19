package com.vast.interpreter;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.internal.Debugger;
import com.vast.registry.VastLibraryLoader;
import com.vast.vm.VastVM;
import com.vast.internal.exception.VastExceptions;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

// 解释器类，负责执行AST节点
public class Interpreter implements ASTVisitor<Void> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> variableTypes = new HashMap<>();//变量存储类型
    private Object lastResult = null;
    private final Map<String, Class<?>> importedClasses = new HashMap<>();
    private final VastVM vm;
    private final Debugger debugger;

    public Interpreter(VastVM vm) {
        this.vm = vm;
        this.debugger = vm.getDebugger();

        // 复制已导入的类
        if (vm != null && vm.getImportedClasses() != null) {
            this.importedClasses.putAll(vm.getImportedClasses());
        }

        debugger.logTypeCheck("Type checking system INITIALIZED - Strict mode enabled");
    }

    public void interpret(Program program) {
        try {
            program.accept(this);
        } catch (VastExceptions.VastRuntimeException error) {
            debugger.logError("Runtime error: " + error.getUserFriendlyMessage());
            throw error; // 重新抛出异常
        }
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

        debugger.logDetail("Assignment expression: " + varName + " = " + value);

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
    public Void visitTypeCastExpression(TypeCastExpression expr) {
        // 类型转换表达式应该在 ExpressionEvaluator 中处理
        debugger.logDetail("Type cast expression: " + expr);
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
            debugger.logBasic("Method call result: " + result);
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

        debugger.logVariable("Variable declaration: " + varName +
                ", typeHint: " + typeHint + ", initial value: " + value);

        // 如果有类型提示，记录变量类型并严格验证初始值
        if (typeHint != null) {
            variableTypes.put(varName, typeHint);
            debugger.logTypeCheck("Registered type constraint: " + varName + " -> " + typeHint);

            // 严格验证初始值的类型
            if (value != null) {
                validateTypeCompatibility(typeHint, value, varName,
                        stmt.getLineNumber(), stmt.getColumnNumber());
            } else {
                debugger.logVariable("Null initial value for typed variable " + varName);
            }
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.logBasic("Var declared: " + varName + " = " + value +
                (typeHint != null ? " (type: " + typeHint + ")" : ""));
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatement stmt) {
        Object value = evaluate(stmt.getValue());
        String varName = stmt.getVariableName();

        debugger.logDetail("Assignment: " + varName + " = " + value + " (type: " + getValueType(value) + ")");

        // 严格的类型检查
        if (variableTypes.containsKey(varName)) {
            String expectedType = variableTypes.get(varName);
            debugger.logTypeCheck("Expected type: " + expectedType);

            // 调用类型检查
            validateTypeCompatibility(expectedType, value, varName,
                    stmt.getLineNumber(), stmt.getColumnNumber());
            debugger.logTypeCheck("Type check PASSED");
        } else {
            debugger.logTypeCheck("No type constraint for " + varName);
        }

        variables.put(varName, value);
        this.lastResult = value;
        debugger.logBasic("Var assigned: " + varName + " = " + value);
        return null;
    }

    /**
     * 检查类型兼容性，不兼容时抛出异常
     */
    private void validateTypeCompatibility(String expectedType, Object value, String varName, int lineNumber, int columnNumber) {
        if (value == null) {
            debugger.logTypeCheck("Null value allowed for any type");
            return; // null 可以赋值给任何类型
        }

        String actualType = getValueType(value);
        debugger.logTypeCheck("Validating: " + expectedType + " <- " + actualType);

        if (!isTypeCompatible(expectedType, value)) {
            String errorMsg = "Type mismatch: cannot assign " + actualType +
                    " to variable '" + varName + "' of type " + expectedType;
            debugger.logTypeCheck(errorMsg);
            throw new VastExceptions.NotGrammarException(
                    errorMsg, lineNumber, columnNumber
            );
        }
        debugger.logTypeCheck("Type compatibility OK");
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        Object result = evaluate(stmt.getExpression());
        this.lastResult = result;
        if (result != null) {
            debugger.logBasic("Expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        String importPath = stmt.getClassName();
        debugger.logBasic("Import: " + importPath);

        try {
            // 首先尝试作为外置库导入
            VastLibraryLoader loader = VastLibraryLoader.getInstance();
            boolean libraryLoaded = loader.loadLibraryFromImport(importPath, this.vm);

            if (libraryLoaded) {
                debugger.logBasic("External library loaded: " + importPath);
                return null;
            }

            // 如果外置库加载失败，尝试作为普通类导入
            Class<?> clazz = Class.forName(importPath);
            importedClasses.put(importPath, clazz);

            // 同时更新 VM 中的导入类
            if (vm != null) {
                vm.getImportedClasses().put(importPath, clazz);
            }

            debugger.logBasic("Class imported: " + importPath);

        } catch (ClassNotFoundException e) {
            // 静默处理类未找到异常，不抛出错误
            debugger.logBasic("Class not found: " + importPath);
        } catch (Exception e) {
            // 静默处理其他异常
            debugger.logBasic("Import failed: " + importPath);
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(LoopStatement stmt) {
        Object condition = evaluate(stmt.getCondition());

        debugger.logDetail("Loop condition: " + condition + " (type: " +
                (condition != null ? condition.getClass().getSimpleName() : "null") + ")");

        // 处理数字类型的循环条件（如 loop(10):）
        if (condition instanceof Number) {
            int count = ((Number) condition).intValue();

            debugger.logDetail("Loop count: " + count);

            for (int i = 0; i < count; i++) {
                debugger.logDetail("Loop iteration: " + (i + 1) + "/" + count);

                // 执行循环体中的所有语句
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            }
        }
        // 处理布尔类型的循环条件（如 loop(true): 或 loop(a > b):）
        else if (condition instanceof Boolean) {
            if ((Boolean) condition) {
                debugger.logDetail("Boolean condition is true, executing loop body");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.logDetail("Boolean condition is false, skipping loop");
            }
        }
        else {
            // 默认情况下，如果条件不是数字或布尔值，当作真值处理并执行一次
            if (condition != null) {
                debugger.logDetail("Non-boolean condition, executing loop body once");
                for (Statement bodyStmt : stmt.getBody()) {
                    bodyStmt.accept(this);
                }
            } else {
                debugger.logDetail("Null condition, skipping loop");
            }
        }
        return null;
    }

    @Override
    public Void visitGiveStatement(GiveStatement stmt) {
        String className = stmt.getTarget().getName();
        debugger.logBasic("Give: " + className + " with " + stmt.getVariables().size() + " variables");

        for (Expression varExpr : stmt.getVariables()) {
            if (varExpr instanceof VariableExpression) {
                String varName = ((VariableExpression) varExpr).getName();
                if (variables.containsKey(varName)) {
                    Object value = variables.get(varName);
                    debugger.logBasic("  Variable " + varName + ": " + value);
                } else {
                    throw VastExceptions.NonExistentObject.variableNotFound(varName);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitDoStatement(DoStatement stmt) {
        String className = stmt.getClassName().getName();
        String methodName = stmt.getMethodName().getName();

        debugger.logBasic("Do: " + className + "." + methodName + "() with " + stmt.getArguments().size() + " arguments");

        Object[] args = new Object[stmt.getArguments().size()];
        for (int i = 0; i < stmt.getArguments().size(); i++) {
            args[i] = evaluate(stmt.getArguments().get(i));
            debugger.logBasic("  Arg " + i + ": " + args[i]);
        }

        Object result = callInternalMethod(className, methodName, args);
        this.lastResult = result;

        if (result != null) {
            debugger.logBasic("Do result: " + result);
        }

        return null;
    }

    @Override
    public Void visitSwapStatement(SwapStatement stmt) {
        String varA = stmt.getVarA().getName();
        String varB = stmt.getVarB().getName();

        debugger.logBasic("Swap: " + varA + " <-> " + varB);

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

        debugger.logBasic("Swapped: " + varA + " = " + variables.get(varA) + ", " + varB + " = " + variables.get(varB));
        return null;
    }

    /**
     * 调用内部库方法
     */
    private Object callInternalMethod(String className, String methodName, Object[] args) {
        try {
            Class<?> clazz = findClass(className);
            if (clazz == null) {
                throw VastExceptions.NonExistentObject.classNotFound(className);
            }

            Method method = findBestMethod(clazz, methodName, args);
            if (method == null) {
                throw VastExceptions.NonExistentObject.methodNotFound(className, methodName);
            }

            return method.invoke(null, args);

        } catch (VastExceptions.VastRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new VastExceptions.UnknownVastException("Failed to call method " + className + "." + methodName, e);
        }
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

        for (Method method : methods) {
            if (method.getName().equals(methodName) && isMethodCompatible(method, args)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 检查方法兼容性
     */
    private boolean isMethodCompatible(Method method, Object[] args) {
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
     * 检查类型兼容性
     */
    private boolean isTypeCompatible(Class<?> paramType, Object arg) {
        if (arg == null) return !paramType.isPrimitive();

        if (paramType.isPrimitive()) {
            return (paramType == int.class && arg instanceof Integer) ||
                    (paramType == boolean.class && arg instanceof Boolean) ||
                    (paramType == double.class && arg instanceof Double) ||
                    (paramType == long.class && arg instanceof Long);
        }

        return paramType.isInstance(arg);
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

    /**
     * 表达式求值器
     */
    private class ExpressionEvaluator implements ASTVisitor<Object> {
        @Override
        public Object visitLiteralExpression(LiteralExpression expr) {
            return expr.getValue();
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

            // 最后检查变量
            if (!variables.containsKey(name)) {
                throw VastExceptions.NonExistentObject.variableNotFound(name);
            }
            return variables.get(name);
        }

        @Override
        public Object visitMemberAccessExpression(MemberAccessExpression expr) {
            Object left = evaluate(expr.getObject());
            String memberName = expr.getMemberName();

            // 如果是变量访问类静态成员（如 Sys.print）
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
        public Object visitTypeCastExpression(TypeCastExpression expr) {
            Object value = evaluate(expr.getExpression());
            String targetType = expr.getTargetType();

            debugger.logDetail("Casting " + value + " (" + getValueType(value) +
                    ") to " + targetType);

            Object result = Interpreter.this.performTypeCast(value, targetType,
                    expr.getLineNumber(), expr.getColumnNumber(),
                    expr.isExplicit());

            debugger.logDetail("Result: " + result + " (" + getValueType(result) + ")");

            return result;
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

            debugger.logDetail("Assignment expression: " + varName + " = " + value +
                    " (value type: " + getValueType(value) + ")");

            // 严格的类型检查
            if (Interpreter.this.variableTypes.containsKey(varName)) {
                String expectedType = Interpreter.this.variableTypes.get(varName);
                debugger.logDetail("Variable '" + varName + "' has type constraint: " + expectedType);

                Interpreter.this.validateTypeCompatibility(expectedType, value, varName,
                        expr.getLineNumber(), expr.getColumnNumber());
                debugger.logDetail("Type check PASSED for " + varName);
            } else {
                debugger.logDetail("No type constraint for " + varName + ", allowing assignment");
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
        public Void visitGiveStatement(GiveStatement stmt) { return null; }
        @Override
        public Void visitDoStatement(DoStatement stmt) { return null; }
        @Override
        public Void visitSwapStatement(SwapStatement stmt) { return null; }

        // 其他辅助方法保持不变...
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
                        debugger.logWarning("Unknown target type: " + targetType);
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
                        debugger.logWarning("Losing precision in implicit cast: " + str + " -> " + (int)d);
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