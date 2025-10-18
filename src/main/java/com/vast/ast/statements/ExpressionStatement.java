package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 表达式语句（方法调用等）
 */
public class ExpressionStatement extends Statement {
    private final Expression expression;

    public ExpressionStatement(Expression expression, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.expression = expression;
    }

    public Expression getExpression() { return expression; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitExpressionStatement(this);
    }

    @Override
    public String toString() {
        return expression.toString();
    }
}