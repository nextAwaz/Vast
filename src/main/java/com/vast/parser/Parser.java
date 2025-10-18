package com.vast.parser;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import java.util.List;
import java.util.ArrayList;

//语法分析器
public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * 解析整个程序
     */
    public Program parseProgram() {
        List<Statement> statements = new ArrayList<>();

        while (!isAtEnd()) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return new Program(statements);
    }

    private Statement parseStatement() {
        if (match("IMPORT")) {
            return parseImportStatement();
        }
        if (match("VAR")) {
            return parseVariableDeclaration();
        }
        if (match("LOOP")) {
            return parseLoopStatement();
        }
        if (match("GIVE")) {
            return parseGiveStatement();
        }
        if (match("DO")) {
            return parseDoStatement();
        }
        if (match("SWAP")) {
            return parseSwapStatement();
        }

        // 赋值语句或表达式语句
        return parseExpressionOrAssignment();
    }

    private Statement parseImportStatement() {
        Token importToken = previous();
        consume("IDENTIFIER", "Expect library or class name after 'imp'");
        String className = previous().getLexeme();

        return new ImportStatement(className,
                importToken.getLine(), importToken.getColumn());
    }

    private VariableDeclaration parseVariableDeclaration() {
        Token varToken = previous();
        String typeHint = null;

        // 检查是否有类型提示
        if (match("LEFT_PAREN")) {
            if (check("IDENTIFIER")) {
                typeHint = advance().getLexeme();
            }
            consume("RIGHT_PAREN", "Expect ')' after type hint");
        }

        String name = consume("IDENTIFIER", "Expect variable name").getLexeme();

        Expression initializer = null;
        if (match("EQUAL")) {
            // 支持类型转换语法：var (newType) name = (oldType) expression
            if (match("LEFT_PAREN")) {
                if (check("IDENTIFIER")) {
                    String sourceType = advance().getLexeme();
                    consume("RIGHT_PAREN", "Expect ')' after source type");

                    // 解析实际表达式
                    Expression expr = parseExpression();
                    // 这里可以创建类型转换表达式，简化处理直接使用原表达式
                    initializer = expr;
                }
            } else {
                initializer = parseExpression();
            }
        }

        return new VariableDeclaration(name, typeHint, initializer,
                varToken.getLine(), varToken.getColumn());
    }

    private Statement parseLoopStatement() {
        Token loopToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'loop'");
        Expression condition = parseExpression();
        consume("RIGHT_PAREN", "Expect ')' after loop condition");
        consume("COLON", "Expect ':' after loop condition");

        // 解析循环体（需要处理缩进块）
        List<Statement> body = parseBlock();

        return new LoopStatement(condition, body,
                loopToken.getLine(), loopToken.getColumn());
    }

    private Statement parseGiveStatement() {
        Token giveToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'give'");

        Expression target = parseExpression();
        if (!(target instanceof VariableExpression)) {
            throw error(peek(), "Give target must be a class name");
        }

        consume("RIGHT_PAREN", "Expect ')' after give target");
        consume("LEFT_PAREN", "Expect '(' for variables");

        List<Expression> variables = parseExpressionList();
        consume("RIGHT_PAREN", "Expect ')' after variables");

        return new GiveStatement((VariableExpression) target, variables,
                giveToken.getLine(), giveToken.getColumn());
    }

    private Statement parseDoStatement() {
        Token doToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'do'");

        Expression classNameExpr = parseExpression();
        if (!(classNameExpr instanceof VariableExpression)) {
            throw error(peek(), "Do class name must be an identifier");
        }

        consume("RIGHT_PAREN", "Expect ')' after class name");
        consume("LEFT_PAREN", "Expect '(' for method name");

        Expression methodNameExpr = parseExpression();
        if (!(methodNameExpr instanceof VariableExpression)) {
            throw error(peek(), "Do method name must be an identifier");
        }

        consume("RIGHT_PAREN", "Expect ')' after method name");

        List<Expression> arguments = new ArrayList<>();
        if (match("LEFT_PAREN")) {
            arguments = parseExpressionList();
            consume("RIGHT_PAREN", "Expect ')' after arguments");
        }

        return new DoStatement((VariableExpression) classNameExpr,
                (VariableExpression) methodNameExpr, arguments,
                doToken.getLine(), doToken.getColumn());
    }

    private Statement parseSwapStatement() {
        Token swapToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'swap'");

        Expression varA = parseExpression();
        if (!(varA instanceof VariableExpression)) {
            throw error(peek(), "Swap operand must be a variable");
        }

        consume("RIGHT_PAREN", "Expect ')' after first variable");
        consume("LEFT_PAREN", "Expect '(' for second variable");

        Expression varB = parseExpression();
        if (!(varB instanceof VariableExpression)) {
            throw error(peek(), "Swap operand must be a variable");
        }

        consume("RIGHT_PAREN", "Expect ')' after second variable");

        return new SwapStatement((VariableExpression) varA, (VariableExpression) varB,
                swapToken.getLine(), swapToken.getColumn());
    }

    private Statement parseExpressionOrAssignment() {
        Expression expr = parseExpression();

        // 检查是否是方法调用
        if (expr instanceof VariableExpression && match("DOT")) {
            // 处理类名.方法名 的情况
            if (match("IDENTIFIER")) {
                String methodName = previous().getLexeme();

                // 检查是否有参数列表
                if (match("LEFT_PAREN")) {
                    List<Expression> arguments = parseExpressionList();
                    consume("RIGHT_PAREN", "Expect ')' after arguments");

                    // 创建方法调用表达式
                    return new ExpressionStatement(
                            new MethodCallExpression(
                                    (VariableExpression) expr,
                                    methodName,
                                    arguments,
                                    expr.getLineNumber(),
                                    expr.getColumnNumber()
                            ),
                            expr.getLineNumber(),
                            expr.getColumnNumber()
                    );
                } else {
                    throw error(peek(), "Expect '(' after method name");
                }
            }
        }

        // 检查是否是赋值语句
        if (expr instanceof VariableExpression && match("EQUAL")) {
            Expression value = parseExpression();
            return new AssignmentStatement(((VariableExpression) expr).getName(), value,
                    expr.getLineNumber(), expr.getColumnNumber());
        }

        return new ExpressionStatement(expr,
                expr.getLineNumber(), expr.getColumnNumber());
    }

    private List<Statement> parseBlock() {
        List<Statement> statements = new ArrayList<>();

        // 简化处理：解析直到遇到非缩进行
        // 实际实现需要处理缩进级别
        while (!isAtEnd() && !check("DEDENT")) {
            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        return statements;
    }

    private List<Expression> parseExpressionList() {
        List<Expression> expressions = new ArrayList<>();

        if (!check("RIGHT_PAREN")) {
            do {
                expressions.add(parseExpression());
            } while (match("COMMA"));
        }

        return expressions;
    }

    // 表达式解析（运算符优先级处理）
    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseLogicalOr();

        if (match("EQUAL")) {
            Token equals = previous();
            Expression value = parseAssignment();

            if (expr instanceof VariableExpression) {
                String name = ((VariableExpression) expr).getName();
                return new AssignmentExpression(name, value,
                        expr.getLineNumber(), expr.getColumnNumber());
            }

            throw error(equals, "Invalid assignment target");
        }

        return expr;
    }

    private Expression parseLogicalOr() {
        Expression expr = parseLogicalAnd();

        while (match("OR")) {
            Token operator = previous();
            Expression right = parseLogicalAnd();
            expr = new BinaryExpression(expr, "||", right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseLogicalAnd() {
        Expression expr = parseEquality();

        while (match("AND")) {
            Token operator = previous();
            Expression right = parseEquality();
            expr = new BinaryExpression(expr, "&&", right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseComparison();

        while (match("EQUAL_EQUAL", "BANG_EQUAL")) {
            Token operator = previous();
            Expression right = parseComparison();
            expr = new BinaryExpression(expr, operator.getLexeme(), right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseComparison() {
        Expression expr = parseTerm();

        while (match("GREATER", "GREATER_EQUAL", "LESS", "LESS_EQUAL")) {
            Token operator = previous();
            Expression right = parseTerm();
            expr = new BinaryExpression(expr, operator.getLexeme(), right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseTerm() {
        Expression expr = parseFactor();

        while (match("PLUS", "MINUS")) {
            Token operator = previous();
            Expression right = parseFactor();
            expr = new BinaryExpression(expr, operator.getLexeme(), right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseFactor() {
        Expression expr = parsePower();

        while (match("STAR", "SLASH", "SLASH_SLASH", "PERCENT")) {
            Token operator = previous();
            Expression right = parsePower();
            expr = new BinaryExpression(expr, operator.getLexeme(), right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parsePower() {
        Expression expr = parseUnary();

        while (match("STAR_STAR")) {
            Token operator = previous();
            Expression right = parseUnary();
            expr = new BinaryExpression(expr, "**", right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }

    private Expression parseUnary() {
        if (match("BANG", "MINUS", "PLUS_PLUS")) {
            Token operator = previous();
            Expression right = parseUnary();
            return new UnaryExpression(operator.getLexeme(), right,
                    operator.getLine(), operator.getColumn());
        }

        return parsePrimary();
    }

    private Expression parsePrimary() {
        if (match("FALSE")) return new LiteralExpression(false, previous().getLine(), previous().getColumn());
        if (match("TRUE")) return new LiteralExpression(true, previous().getLine(), previous().getColumn());
        if (match("NUMBER")) {
            String numberText = previous().getLexeme();
            Object value;
            if (numberText.contains(".")) {
                value = Double.parseDouble(numberText);
            } else {
                value = Integer.parseInt(numberText);
            }
            return new LiteralExpression(value, previous().getLine(), previous().getColumn());
        }
        if (match("STRING")) {
            String stringText = previous().getLexeme();
            // 去掉引号并处理转义字符
            String value = parseStringLiteral(stringText);
            return new LiteralExpression(value, previous().getLine(), previous().getColumn());
        }
        if (match("IDENTIFIER")) {
            Token identifier = previous();
            Expression expr = new VariableExpression(identifier.getLexeme(),
                    identifier.getLine(), identifier.getColumn());

            // 处理成员访问链
            while (match("DOT")) {
                if (!match("IDENTIFIER")) {
                    throw error(peek(), "Expect property name after '.'");
                }
                Token property = previous();
                expr = new MemberAccessExpression(expr, property.getLexeme(),
                        identifier.getLine(), identifier.getColumn());
            }

            // 处理函数调用
            if (match("LEFT_PAREN")) {
                List<Expression> arguments = parseExpressionList();
                consume("RIGHT_PAREN", "Expect ')' after arguments");
                expr = new FunctionCallExpression(expr, arguments,
                        identifier.getLine(), identifier.getColumn());
            }

            return expr;
        }
        if (match("LEFT_PAREN")) {
            Expression expr = parseExpression();
            consume("RIGHT_PAREN", "Expect ')' after expression");
            return expr;
        }

        throw error(peek(), "Expect expression");
    }

    private String parseStringLiteral(String stringText) {
        // 去掉引号
        String content = stringText.substring(1, stringText.length() - 1);

        // 处理转义字符
        StringBuilder result = new StringBuilder();
        boolean inEscape = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inEscape) {
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 't': result.append('\t'); break;
                    case 'r': result.append('\r'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    default: result.append('\\').append(c); break;
                }
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    // 辅助方法
    private boolean match(String... types) {
        for (String type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(String type) {
        if (isAtEnd()) return false;
        return peek().getType().equals(type);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType().equals("EOF");
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(String type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseException error(Token token, String message) {
        return new ParseException(message, token.getLine(), token.getColumn());
    }

    public static class ParseException extends RuntimeException {
        private final int line;
        private final int column;

        public ParseException(String message, int line, int column) {
            super("Parse error at line " + line + ", column " + column + ": " + message);
            this.line = line;
            this.column = column;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }
    }
}