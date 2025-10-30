package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 根式表达式：`(expression) 表示 √(expression)
 */
public class RootExpression extends Expression {
    private final Expression expression;

    public RootExpression(Expression expression, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.expression = expression;
    }

    public Expression getExpression() { return expression; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitRootExpression(this);
    }

    @Override
    public String toString() {
        return "`(" + expression + ")";
    }
}