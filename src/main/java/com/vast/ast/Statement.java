package com.vast.ast;

/**
 * 语句基类
 */
public abstract class Statement extends ASTNode {
    public Statement(int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
    }
}