package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * 方法调用表达式：ClassName.methodName(arguments)
 */
public class MethodCallExpression extends Expression {
    private final VariableExpression className;
    private final String methodName;
    private final List<Expression> arguments;

    public MethodCallExpression(VariableExpression className, String methodName,
                                List<Expression> arguments, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.className = className;
        this.methodName = methodName;
        this.arguments = arguments;
    }

    public VariableExpression getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public List<Expression> getArguments() { return arguments; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitMethodCallExpression(this);
    }

    @Override
    public String toString() {
        return className + "." + methodName + "(" +
                String.join(", ", arguments.stream().map(Object::toString).toArray(String[]::new)) +
                ")";
    }
}