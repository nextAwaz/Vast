package com.vast.ast.statements;

import com.vast.ast.ASTVisitor;
import com.vast.ast.Expression;
import com.vast.ast.Statement;

public class VariableDeclaration extends Statement {
    private final String variableName;
    private final String typeHint; // 存储类型信息
    private final Expression initialValue;
    private final boolean hasExplicitCast;

    public VariableDeclaration(String variableName, String typeHint,
                               Expression initialValue, boolean hasExplicitCast,
                               int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.typeHint = typeHint;
        this.initialValue = initialValue;
        this.hasExplicitCast = hasExplicitCast;
    }

    // 旧构造函数，保持兼容性
    public VariableDeclaration(String variableName, String typeHint,
                               Expression initialValue, int lineNumber, int columnNumber) {
        this(variableName, typeHint, initialValue, false, lineNumber, columnNumber);
    }

    public String getVariableName() { return variableName; }
    public String getTypeHint() { return typeHint; }
    public Expression getInitialValue() { return initialValue; }
    public boolean hasExplicitCast() { return hasExplicitCast; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableDeclaration(this);
    }

    @Override
    public String toString() {
        if (typeHint != null) {
            return typeHint + " " + variableName +
                    (initialValue != null ? " = " + initialValue : "");
        } else {
            return "var " + variableName +
                    (initialValue != null ? " = " + initialValue : "");
        }
    }
}