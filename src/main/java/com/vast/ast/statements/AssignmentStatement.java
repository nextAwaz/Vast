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
    private final String typeHint; // 类型提示，null表示自由类型

    public AssignmentStatement(String variableName, Expression value,
                               String typeHint, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.value = value;
        this.typeHint = typeHint;
    }

    public String getVariableName() { return variableName; }
    public Expression getValue() { return value; }
    public String getTypeHint() { return typeHint; }
    public boolean isStrongTyped() { return typeHint != null; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitAssignmentStatement(this);
    }

    @Override
    public String toString() {
        if (typeHint != null) {
            return typeHint + " " + variableName + " = " + value;
        } else {
            return variableName + " = " + value;
        }
    }
}