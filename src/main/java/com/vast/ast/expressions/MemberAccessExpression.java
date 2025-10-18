package com.vast.ast.expressions;

import com.vast.ast.Expression;
import com.vast.ast.ASTVisitor;

/**
 * 成员访问表达式：object.member
 */
public class MemberAccessExpression extends Expression {
    private final Expression object;
    private final String memberName;

    public MemberAccessExpression(Expression object, String memberName,
                                  int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.object = object;
        this.memberName = memberName;
    }

    public Expression getObject() { return object; }
    public String getMemberName() { return memberName; }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitMemberAccessExpression(this);
    }

    @Override
    public String toString() {
        return object + "." + memberName;
    }
}