package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 类型转换表达式
 */
public class TypeCastExpression extends Expression {
    private final Expression expression;
    private final String targetType;
    private final boolean isExplicit;

    public TypeCastExpression(Expression expression, String targetType,
                              int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.expression = expression;
        this.targetType = targetType;
        this.isExplicit = true;
    }

    public TypeCastExpression(Expression expression, String targetType,
                              boolean isExplicit, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.expression = expression;
        this.targetType = targetType;
        this.isExplicit = isExplicit;
    }

    public Expression getExpression() { return expression; }
    public String getTargetType() { return targetType; }
    public boolean isExplicit() { return isExplicit; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitTypeCastExpression(this);
    }

    @Override
    public String toString() {
        if (isExplicit) {
            return "(" + targetType + ") " + expression;
        } else {
            return expression + " -> " + targetType;
        }
    }
}