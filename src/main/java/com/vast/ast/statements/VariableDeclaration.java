package com.vast.ast.statements;

import com.vast.ast.ASTVisitor;
import com.vast.ast.Expression;
import com.vast.ast.Statement;

public class VariableDeclaration extends Statement {
    private final String variableName;
    private final String typeHint; // 存储类型信息
    private final Expression initialValue;
    private final boolean isTypeCast; // 是否是类型转换声明

    public VariableDeclaration(String variableName, String typeHint,
                               Expression initialValue, boolean isTypeCast,
                               int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.typeHint = typeHint;
        this.initialValue = initialValue;
        this.isTypeCast = isTypeCast;
    }

    public String getVariableName() { return variableName; }
    public String getTypeHint() { return typeHint; }
    public Expression getInitialValue() { return initialValue; }
    public boolean isTypeCast() { return isTypeCast; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableDeclaration(this);
    }

    @Override
    public String toString() {
        if (isTypeCast) {
            return typeHint + " " + variableName + " = " + typeHint + "(" + initialValue + ")";
        } else {
            return typeHint + " " + variableName +
                    (initialValue != null ? " = " + initialValue : "");
        }
    }
}