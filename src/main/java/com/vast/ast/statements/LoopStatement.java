package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;
import java.util.List;

/**
 * 循环语句
 */
public class LoopStatement extends Statement {
    private final Expression condition;
    private final List<Statement> body;

    public LoopStatement(Expression condition, List<Statement> body,
                         int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.condition = condition;
        this.body = body;
    }

    public Expression getCondition() { return condition; }
    public List<Statement> getBody() { return body; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLoopStatement(this);
    }

    @Override
    public String toString() {
        return "loop(" + condition + "): ...";
    }
}