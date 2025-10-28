package com.vast.ast.statements;

import com.vast.ast.Statement;
import com.vast.ast.expressions.TypeCastExpression;
import com.vast.ast.ASTVisitor;

/**
 * 内联类型转换语句：type(variableName)
 * 直接将变量转换为新类型并保持原名
 */
public class InlineTypeCastStatement extends Statement {
    private final TypeCastExpression typeCastExpression;

    public InlineTypeCastStatement(TypeCastExpression typeCastExpression,
                                   int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.typeCastExpression = typeCastExpression;
    }

    public TypeCastExpression getTypeCastExpression() { return typeCastExpression; }
    public String getTargetType() { return typeCastExpression.getTargetType(); }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitInlineTypeCastStatement(this);
    }

    @Override
    public String toString() {
        return typeCastExpression.getTargetType() + "(" +
                typeCastExpression.getExpression() + ")";
    }
}