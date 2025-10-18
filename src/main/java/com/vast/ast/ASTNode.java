package com.vast.ast;

import java.util.List;

/**
 * AST 节点的基类
 */
public abstract class ASTNode {
    private int lineNumber;
    private int columnNumber;

    public ASTNode(int lineNumber, int columnNumber) {
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }

    /**
     * 接受访问者模式的访问
     */
    public abstract <T> T accept(ASTVisitor<T> visitor);

    @Override
    public abstract String toString();
}