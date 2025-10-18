package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.expressions.VariableExpression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * Do 语句
 */
public class DoStatement extends Statement {
    private final VariableExpression className;
    private final VariableExpression methodName;
    private final List<Expression> arguments;

    public DoStatement(VariableExpression className, VariableExpression methodName,
                       List<Expression> arguments, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.className = className;
        this.methodName = methodName;
        this.arguments = arguments;
    }

    public VariableExpression getClassName() { return className; }
    public VariableExpression getMethodName() { return methodName; }
    public List<Expression> getArguments() { return arguments; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitDoStatement(this);
    }

    @Override
    public String toString() {
        return "do(" + className + ")(" + methodName + ")(" +
                String.join(", ", arguments.stream().map(Object::toString).toArray(String[]::new)) + ")";
    }
}