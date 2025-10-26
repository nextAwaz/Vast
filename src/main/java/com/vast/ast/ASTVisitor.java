package com.vast.ast;

import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;

/**
 * AST 访问者接口
 * 用于实现不同的 AST 处理逻辑（类型检查、代码生成、解释执行等）
 */
public interface ASTVisitor<T> {
    // 表达式访问方法
    T visitLiteralExpression(LiteralExpression expr);
    T visitVariableExpression(VariableExpression expr);
    T visitBinaryExpression(BinaryExpression expr);
    T visitUnaryExpression(UnaryExpression expr);
    T visitAssignmentExpression(AssignmentExpression expr);
    T visitMemberAccessExpression(MemberAccessExpression expr);
    T visitFunctionCallExpression(FunctionCallExpression expr);
    T visitMethodCallExpression(MethodCallExpression expr);
    T visitTypeCastExpression(TypeCastExpression expr);

    T visitFractionExpression(FractionExpression expr);
    T visitBitwiseExpression(BitwiseExpression expr);//按位运算

    // 语句访问方法
    T visitVariableDeclaration(VariableDeclaration stmt);
    T visitAssignmentStatement(AssignmentStatement stmt);
    T visitExpressionStatement(ExpressionStatement stmt);
    T visitImportStatement(ImportStatement stmt);
    T visitLoopStatement(LoopStatement stmt);
    T visitUseStatement(UseStatement stmt);
    T visitSwapStatement(SwapStatement stmt);

    //处理未知节点类型
    default T visit(ASTNode node) {
        return node.accept(this);
    }
}