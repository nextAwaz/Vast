package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 变量引用表达式
 */
public class VariableExpression extends Expression {
    private final String name;

    public VariableExpression(String name, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.name = name;
    }

    public String getName() { return name; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableExpression(this);
    }

    @Override
    public String toString() {
        return name;
    }
}