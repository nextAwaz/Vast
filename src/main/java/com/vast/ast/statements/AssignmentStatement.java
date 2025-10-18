package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 赋值语句
 */
public class AssignmentStatement extends Statement {
    private final String variableName;
    private final Expression value;

    public AssignmentStatement(String variableName, Expression value,
                               int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.value = value;
    }

    public String getVariableName() { return variableName; }
    public Expression getValue() { return value; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitAssignmentStatement(this);
    }

    @Override
    public String toString() {
        return variableName + " = " + value;
    }
}