package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.ASTVisitor;

/**
 * 导入语句
 */
public class ImportStatement extends Statement {
    private final String className;

    public ImportStatement(String className, int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.className = className;
    }

    public String getClassName() { return className; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitImportStatement(this);
    }

    @Override
    public String toString() {
        return "imp " + className;
    }
}