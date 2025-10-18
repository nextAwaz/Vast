package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 赋值表达式
 */
public class AssignmentExpression extends Expression {
    private final String variableName;
    private final Expression value;

    public AssignmentExpression(String variableName, Expression value,
                                int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.value = value;
    }

    public String getVariableName() { return variableName; }
    public Expression getValue() { return value; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitAssignmentExpression(this);
    }

    @Override
    public String toString() {
        return variableName + " = " + value;
    }
}