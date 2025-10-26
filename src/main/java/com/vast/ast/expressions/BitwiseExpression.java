package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 按位运算表达式
 */
public class BitwiseExpression extends Expression {
    private final Expression left;
    private final String operator;
    private final Expression right;

    public BitwiseExpression(Expression left, String operator, Expression right,
                             int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    public Expression getLeft() { return left; }
    public String getOperator() { return operator; }
    public Expression getRight() { return right; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBitwiseExpression(this);
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}