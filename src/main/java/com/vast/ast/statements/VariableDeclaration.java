package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 变量声明语句
 */
public class VariableDeclaration extends Statement {
    private final String variableName;
    private final String typeHint; // 可选的类型提示
    private final Expression initialValue;

    public VariableDeclaration(String variableName, String typeHint,
                               Expression initialValue, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.variableName = variableName;
        this.typeHint = typeHint;
        this.initialValue = initialValue;
    }

    public String getVariableName() { return variableName; }
    public String getTypeHint() { return typeHint; }
    public Expression getInitialValue() { return initialValue; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitVariableDeclaration(this);
    }

    @Override
    public String toString() {
        if (typeHint != null) {
            return "var(" + typeHint + ") " + variableName + " = " + initialValue;
        } else {
            return "var " + variableName + " = " + initialValue;
        }
    }
}