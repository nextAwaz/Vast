package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.expressions.VariableExpression;
import com.vast.ast.ASTVisitor;

/**
 * Swap 语句
 */
public class SwapStatement extends Statement {
    private final VariableExpression varA;
    private final VariableExpression varB;

    public SwapStatement(VariableExpression varA, VariableExpression varB,
                         int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.varA = varA;
        this.varB = varB;
    }

    public VariableExpression getVarA() { return varA; }
    public VariableExpression getVarB() { return varB; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitSwapStatement(this);
    }

    @Override
    public String toString() {
        return "swap(" + varA + ")(" + varB + ")";
    }
}