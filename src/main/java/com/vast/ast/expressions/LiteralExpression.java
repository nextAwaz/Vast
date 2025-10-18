package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 字面量表达式
 */
public class LiteralExpression extends Expression {
    private final Object value;

    public LiteralExpression(Object value, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.value = value;
    }

    public Object getValue() { return value; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLiteralExpression(this);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}