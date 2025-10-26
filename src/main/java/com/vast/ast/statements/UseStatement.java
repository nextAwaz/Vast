package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.expressions.VariableExpression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * Use 语句
 */
public class UseStatement extends Statement {
    private final VariableExpression className;
    private final VariableExpression methodName;
    private final List<Expression> arguments;
    private final Expression methodCall; // 新增字段

    public UseStatement(VariableExpression className, VariableExpression methodName,
                        List<Expression> arguments, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.className = className;
        this.methodName = methodName;
        this.arguments = arguments;
        this.methodCall = null; // 保持兼容性
    }

    // 新增构造函数，接受方法调用表达式
    public UseStatement(Expression methodCall, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.methodCall = methodCall;
        this.className = null;
        this.methodName = null;
        this.arguments = null;
    }

    public VariableExpression getClassName() { return className; }
    public VariableExpression getMethodName() { return methodName; }
    public List<Expression> getArguments() { return arguments; }
    public Expression getMethodCall() { return methodCall; } // 新增getter

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUseStatement(this); // 修正方法名
    }

    @Override
    public String toString() {
        if (methodCall != null) {
            return "use(" + methodCall + ")";
        } else {
            return "use(" + className + "." + methodName + "(" +
                    String.join(", ", arguments.stream().map(Object::toString).toArray(String[]::new)) +
                    "))";
        }
    }
}