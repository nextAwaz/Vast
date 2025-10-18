package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.expressions.VariableExpression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * Give 语句
 */
public class GiveStatement extends Statement {
    private final VariableExpression target;
    private final List<Expression> variables;

    public GiveStatement(VariableExpression target, List<Expression> variables,
                         int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.target = target;
        this.variables = variables;
    }

    public VariableExpression getTarget() { return target; }
    public List<Expression> getVariables() { return variables; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitGiveStatement(this);
    }

    @Override
    public String toString() {
        return "give(" + target + ")(" + String.join(", ",
                variables.stream().map(Object::toString).toArray(String[]::new)) + ")";
    }
}