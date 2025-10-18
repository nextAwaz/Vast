package com.vast.interpreter;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.vm.VastVM;
import com.vast.internal.exception.VastExceptions;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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
        } catch (VastExceptions.VastRuntimeException error) {
            System.err.println("Runtime error: " + error.getUserFriendlyMessage());
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
        this.lastResult = value;
        System.out.println("@ Var declared: " + stmt.getVariableName() + " = " + value);
        return null;
    }

    @Override
    public Void visitAssignmentStatement(AssignmentStatement stmt) {
        Object value = evaluate(stmt.getValue());
        variables.put(stmt.getVariableName(), value);
        this.lastResult = value;
        System.out.println("@ Var assigned: " + stmt.getVariableName() + " = " + value);
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        Object result = evaluate(stmt.getExpression());
        this.lastResult = result;
        if (result != null) {
            System.out.println("@ Expression result: " + result);
        }
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        System.out.println("@ Import: " + stmt.getClassName());
        try {
            Class<?> clazz = Class.forName(stmt.getClassName());
            importedClasses.put(stmt.getClassName(), clazz);
        } catch (ClassNotFoundException e) {
            throw VastExceptions.NonExistentExternalLibraryException.forLibrary(stmt.getClassName(), e);
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(LoopStatement stmt) {
        System.out.println("@ Loop: " + stmt.getCondition());
        Object condition = evaluate(stmt.getCondition());
        if (condition instanceof Boolean && (Boolean) condition) {
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

        for (Expression varExpr : stmt.getVariables()) {
            if (varExpr instanceof VariableExpression) {
                String varName = ((VariableExpression) varExpr).getName();
                if (variables.containsKey(varName)) {
                    Object value = variables.get(varName);
                    System.out.println("  Variable " + varName + ": " + value);
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

        System.out.println("@ Do: " + className + "." + methodName + "() with " + stmt.getArguments().size() + " arguments");

        Object[] args = new Object[stmt.getArguments().size()];
        for (int i = 0; i < stmt.getArguments().size(); i++) {
            args[i] = evaluate(stmt.getArguments().get(i));
            System.out.println("  Arg " + i + ": " + args[i]);
        }

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
            throw VastExceptions.NonExistentObject.variableNotFound(varA);
        }
        if (!variables.containsKey(varB)) {
            throw VastExceptions.NonExistentObject.variableNotFound(varB);
        }

        Object valueA = variables.get(varA);
        Object valueB = variables.get(varB);

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
        Map<String, Class<?>> builtinClasses = VastVM.getBuiltinClasses();
        if (builtinClasses.containsKey(className)) {
            return builtinClasses.get(className);
        }

        if (importedClasses.containsKey(className)) {
            return importedClasses.get(className);
        }

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
                throw VastExceptions.NonExistentObject.variableNotFound(expr.getName());
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
            variables.put(expr.getVariableName(), value);
            return value;
        }

        // 其他访问方法不需要实现
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
                throw VastExceptions.MathError.divisionByZero();
            }
            return toInt(left) / toInt(right);
        }

        /**
         * 数字拼接
         */
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

        /**
         * 取模运算
         */
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

        /**
         * 字符串重复
         */
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
}