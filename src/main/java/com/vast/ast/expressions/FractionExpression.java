package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 分数表达式：$(expression) 或 $$(expression)
 */
public class FractionExpression extends Expression {
    private final Expression expression;
    private final boolean isPermanent;

    public FractionExpression(Expression expression, boolean isPermanent,
                              int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.expression = expression;
        this.isPermanent = isPermanent;
    }

    public Expression getExpression() { return expression; }
    public boolean isPermanent() { return isPermanent; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitFractionExpression(this);
    }

    @Override
    public String toString() {
        String symbol = isPermanent ? "$$" : "$";
        return symbol + "(" + expression + ")";
    }
}