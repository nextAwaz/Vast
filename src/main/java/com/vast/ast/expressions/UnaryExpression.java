package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 一元表达式
 */
public class UnaryExpression extends Expression {
    private final String operator;
    private final Expression right;

    public UnaryExpression(String operator, Expression right,
                           int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.operator = operator;
        this.right = right;
    }

    public String getOperator() { return operator; }
    public Expression getRight() { return right; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnaryExpression(this);
    }

    @Override
    public String toString() {
        return operator + right;
    }
}