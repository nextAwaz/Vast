package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * 函数调用表达式
 */
public class FunctionCallExpression extends Expression {
    private final Expression callee;
    private final List<Expression> arguments;

    public FunctionCallExpression(Expression callee, List<Expression> arguments,
                                  int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.callee = callee;
        this.arguments = arguments;
    }

    public Expression getCallee() { return callee; }
    public List<Expression> getArguments() { return arguments; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFunctionCallExpression(this);
    }

    @Override
    public String toString() {
        return callee + "(" +
                String.join(", ", arguments.stream().map(Object::toString).toArray(String[]::new)) +
                ")";
    }
}