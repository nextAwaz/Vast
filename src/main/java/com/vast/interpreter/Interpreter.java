package com.vast.interpreter;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.vm.VastVM;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// 解释器类，负责执行AST节点
public class Interpreter implements ASTVisitor<Void> {
    private final Map<String, Object> variables = new HashMap<>();
    private Object lastResult = null;
    private final Map<String, Class<?>> importedClasses = new HashMap<>();

    public Object getLastResult() {
        return lastResult;
    }

    public void interpret(Program program) {
        try {
            program.accept(this);
        } catch (RuntimeError error) {
            System.err.println("Runtime error: " + error.getMessage());
        }
    }

    // 表达式访问方法
    @Override
    public Void visitLiteralExpression(LiteralExpression expr) {
        // 字面量表达式在 evaluate 中直接返回值，这里不需要处理
        return null;
    }

    @Override
    public Void visitVariableExpression(VariableExpression expr) {
        // 变量表达式在 evaluate 中处理，这里不需要处理
        return null;
    }

    @Override
    public Void visitBinaryExpression(BinaryExpression expr) {
        // 二元表达式在 evaluate 中处理，这里不需要处理
        return null;
    }

    @Override
    public Void visitUnaryExpression(UnaryExpression expr) {
        // 一元表达式在 evaluate 中处理，这里不需要处理
        return null;
    }

    @Override
    public Void visitAssignmentExpression(AssignmentExpression expr) {
        // 赋值表达式在 evaluate 中处理，这里不需要处理
        return null;
    }

    // 语句访问方法
    @Override
    public Void visitVariableDeclaration(VariableDeclaration stmt) {
        Object value = null;
        if (stmt.getInitialValue() != null) {
            value = evaluate(stmt.getInitialValue());
        }

        variables.put(stmt.getVariableName(), value);
        this.lastResult = value; // 设置最后结果
        System.out.println("@ Var declared: " + stmt.getVariableName() + " = " + value);
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatement stmt) {
        Object value = evaluate(stmt.getValue());
        variables.put(stmt.getVariableName(), value);
        this.lastResult = value; // 设置最后结果
        System.out.println("@ Var assigned: " + stmt.getVariableName() + " = " + value);
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        Object result = evaluate(stmt.getExpression());
        this.lastResult = result; // 设置最后结果
        if (result != null) {
            System.out.println("@ Expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        System.out.println("@ Import: " + stmt.getClassName());
        // TODO: 实现导入逻辑
        try {
            Class<?> clazz = Class.forName(stmt.getClassName());
            importedClasses.put(stmt.getClassName(), clazz);
        } catch (ClassNotFoundException e) {
            System.err.println("Warning: Class not found: " + stmt.getClassName());
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(LoopStatement stmt) {
        System.out.println("@ Loop: " + stmt.getCondition());
        Object condition = evaluate(stmt.getCondition());
        if (condition instanceof Boolean && (Boolean) condition) {
            // 执行循环体
            for (Statement bodyStmt : stmt.getBody()) {
                bodyStmt.accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitGiveStatement(GiveStatement stmt) {
        String className = stmt.getTarget().getName();
        System.out.println("@ Give: " + className + " with " + stmt.getVariables().size() + " variables");

        // 收集变量值
        for (Expression varExpr : stmt.getVariables()) {
            if (varExpr instanceof VariableExpression) {
                String varName = ((VariableExpression) varExpr).getName();
                if (variables.containsKey(varName)) {
                    Object value = variables.get(varName);
                    System.out.println("  Variable " + varName + ": " + value);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitDoStatement(DoStatement stmt) {
        String className = stmt.getClassName().getName();
        String methodName = stmt.getMethodName().getName();

        System.out.println("@ Do: " + className + "." + methodName + "() with " + stmt.getArguments().size() + " arguments");

        // 计算参数值
        Object[] args = new Object[stmt.getArguments().size()];
        for (int i = 0; i < stmt.getArguments().size(); i++) {
            args[i] = evaluate(stmt.getArguments().get(i));
            System.out.println("  Arg " + i + ": " + args[i]);
        }

        // 调用方法
        Object result = callInternalMethod(className, methodName, args);
        this.lastResult = result;

        if (result != null) {
            System.out.println("@ Do result: " + result);
        }

        return null;
    }

    @Override
    public Void visitSwapStatement(SwapStatement stmt) {
        String varA = stmt.getVarA().getName();
        String varB = stmt.getVarB().getName();

        System.out.println("@ Swap: " + varA + " <-> " + varB);

        if (!variables.containsKey(varA)) {
            throw new RuntimeError("Undefined variable '" + varA + "'");
        }
        if (!variables.containsKey(varB)) {
            throw new RuntimeError("Undefined variable '" + varB + "'");
        }

        Object valueA = variables.get(varA);
        Object valueB = variables.get(varB);

        // 执行交换
        variables.put(varA, valueB);
        variables.put(varB, valueA);

        System.out.println("@ Swapped: " + varA + " = " + variables.get(varA) + ", " + varB + " = " + variables.get(varB));
        return null;
    }

    /**
     * 调用内部库方法
     */
    private Object callInternalMethod(String className, String methodName, Object[] args) {
        try {
            // 查找类
            Class<?> clazz = findClass(className);
            if (clazz == null) {
                throw new RuntimeError("Class not found: " + className);
            }

            // 查找方法
            Method method = findBestMethod(clazz, methodName, args);
            if (method == null) {
                throw new RuntimeError("Method not found: " + methodName + " in class " + className);
            }

            // 调用静态方法
            return method.invoke(null, args);

        } catch (Exception e) {
            throw new RuntimeError("Failed to call method " + className + "." + methodName + ": " + e.getMessage());
        }
    }

    /**
     * 查找类
     */
    private Class<?> findClass(String className) {
        // 首先检查VastVM的注册类
        Map<String, Class<?>> builtinClasses = VastVM.getBuiltinClasses();
        if (builtinClasses.containsKey(className)) {
            return builtinClasses.get(className);
        }

        // 检查运行时导入的类
        if (importedClasses.containsKey(className)) {
            return importedClasses.get(className);
        }

        // 尝试动态加载
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
            if (!variables.containsKey(expr.getName())) {
                throw new RuntimeError("Undefined variable '" + expr.getName() + "'");
            }
            return variables.get(expr.getName());
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
                    throw new RuntimeError("Unknown operator: " + expr.getOperator());
            }
        }

        @Override
        public Object visitUnaryExpression(UnaryExpression expr) {
            Object right = evaluate(expr.getRight());

            switch (expr.getOperator()) {
                case "-":
                    if (right instanceof Integer) return -(Integer) right;
                    if (right instanceof Double) return -(Double) right;
                    throw new RuntimeError("Unary - requires numeric operand");
                case "!":
                    if (right instanceof Boolean) return !(Boolean) right;
                    throw new RuntimeError("Unary ! requires boolean operand");
                case "++":
                    // 前缀自增
                    if (right instanceof Integer) return (Integer) right + 1;
                    if (right instanceof Double) return (Double) right + 1.0;
                    throw new RuntimeError("Unary ++ requires numeric operand");
                default:
                    throw new RuntimeError("Unknown unary operator: " + expr.getOperator());
            }
        }

        @Override
        public Object visitAssignmentExpression(AssignmentExpression expr) {
            Object value = evaluate(expr.getValue());
            variables.put(expr.getVariableName(), value);
            return value;
        }

        // 其他访问方法不需要实现，因为不会在表达式求值中调用
        @Override
        public Object visitVariableDeclaration(VariableDeclaration stmt) { return null; }
        @Override
        public Object visitAssignmentStatement(AssignmentStatement stmt) { return null; }
        @Override
        public Object visitExpressionStatement(ExpressionStatement stmt) { return null; }
        @Override
        public Object visitImportStatement(ImportStatement stmt) { return null; }
        @Override
        public Object visitLoopStatement(LoopStatement stmt) { return null; }
        @Override
        public Object visitGiveStatement(GiveStatement stmt) { return null; }
        @Override
        public Object visitDoStatement(DoStatement stmt) { return null; }
        @Override
        public Object visitSwapStatement(SwapStatement stmt) { return null; }

        /**
         * 幂运算
         */
        private Object performPower(Object left, Object right) {
            checkNumberOperands(left, right);
            double base = toDouble(left);
            double exponent = toDouble(right);
            double result = Math.pow(base, exponent);

            // 如果两个操作数都是整数且指数是非负整数，返回整数
            if (left instanceof Integer && right instanceof Integer && exponent >= 0) {
                return (int) result;
            }
            return result;
        }

        /**
         * 整数除法
         */
        private Object performIntegerDivision(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw new RuntimeError("Division by zero");
            }
            return toInt(left) / toInt(right);
        }

        /**
         * 数字拼接（Volcano的++运算符）
         */
        private Object performNumberConcatenation(Object left, Object right) {
            // 处理自增情况：10++ -> 11
            if (right == null || (right instanceof String && ((String) right).isEmpty())) {
                if (left instanceof Integer) {
                    return (Integer) left + 1;
                } else if (left instanceof Double) {
                    return (Double) left + 1.0;
                } else {
                    throw new RuntimeError("Cannot increment non-numeric value: " + left);
                }
            }

            // 处理自增情况：++10 -> 11（前缀自增）
            if (left == null || (left instanceof String && ((String) left).isEmpty())) {
                if (right instanceof Integer) {
                    return (Integer) right + 1;
                } else if (right instanceof Double) {
                    return (Double) right + 1.0;
                } else {
                    throw new RuntimeError("Cannot increment non-numeric value: " + right);
                }
            }

            // 正常数字拼接
            String leftStr = String.valueOf(left).replaceAll("\\.0*$", "").replaceAll("\\.", "");
            String rightStr = String.valueOf(right).replaceAll("\\.0*$", "").replaceAll("\\.", "");

            try {
                return Integer.parseInt(leftStr + rightStr);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(leftStr + rightStr);
                } catch (NumberFormatException e2) {
                    throw new RuntimeError("Number concatenation result is too large: " + leftStr + rightStr);
                }
            }
        }

        /**
         * 取模运算
         */
        private Object performModulo(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw new RuntimeError("Division by zero");
            }
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) % toDouble(right);
            }
            return toInt(left) % toInt(right);
        }

        /**
         * 字符串重复（Volcano的字符串*数字）
         */
        private Object performStringMultiplication(Object left, Object right) {
            // 字符串重复：字符串 * 数字
            if (left instanceof String && right instanceof Number) {
                String str = (String) left;
                int count = toInt(right);
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    result.append(str);
                }
                return result.toString();
            }

            // 数字重复：数字 * 字符串
            if (left instanceof Number && right instanceof String) {
                return performStringMultiplication(right, left);
            }

            throw new RuntimeError("String repetition requires string and number operands");
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
            throw new RuntimeError("Operands must be two numbers or two strings");
        }

        private Object performSubtraction(Object left, Object right) {
            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) - toDouble(right);
            }
            return toInt(left) - toInt(right);
        }

        private Object performMultiplication(Object left, Object right) {
            // 先检查字符串重复
            if ((left instanceof String && right instanceof Number) ||
                    (left instanceof Number && right instanceof String)) {
                return performStringMultiplication(left, right);
            }

            // 数字乘法
            checkNumberOperands(left, right);
            if (left instanceof Double || right instanceof Double) {
                return toDouble(left) * toDouble(right);
            }
            return toInt(left) * toInt(right);
        }

        private Object performDivision(Object left, Object right) {
            checkNumberOperands(left, right);
            if (toDouble(right) == 0) {
                throw new RuntimeError("Division by zero");
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
            throw new RuntimeError("Cannot compare values of different types");
        }

        private void checkNumberOperands(Object left, Object right) {
            if (!(left instanceof Number && right instanceof Number)) {
                throw new RuntimeError("Operands must be numbers");
            }
        }

        private double toDouble(Object obj) {
            if (obj instanceof Integer) return ((Integer) obj).doubleValue();
            if (obj instanceof Double) return (Double) obj;
            throw new RuntimeError("Cannot convert to double: " + obj);
        }

        private int toInt(Object obj) {
            if (obj instanceof Integer) return (Integer) obj;
            if (obj instanceof Double) return ((Double) obj).intValue();
            throw new RuntimeError("Cannot convert to int: " + obj);
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

    public static class RuntimeError extends RuntimeException {
        public RuntimeError(String message) {
            super(message);
        }
    }
}