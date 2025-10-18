package com.vast.ast;

import java.util.List;

/**
 * 程序根节点
 */
public class Program extends ASTNode {
    private final List<Statement> statements;

    public Program(List<Statement> statements) {
        super(0, 0); // 程序没有具体行列号
        this.statements = statements;
    }

    public List<Statement> getStatements() { return statements; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // 程序节点的访问逻辑：依次访问所有语句
        for (Statement stmt : statements) {
            visitor.visit(stmt);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Statement stmt : statements) {
            sb.append(stmt.toString()).append("\n");
        }
        return sb.toString();
    }
}