package com.vast.ast;

/**
 * 表达式基类
 */
public abstract class Expression extends ASTNode {
    public Expression(int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
    }
}