package com.vast.parser;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

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
            // 跳过换行符
            while (match("NEWLINE")) {
                // 继续跳过
            }

            if (isAtEnd()) break;

            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }

            // 在语句后跳过换行符
            match("NEWLINE");
        }

        return new Program(statements);
    }


    private Statement parseStatement() {
        // 跳过语句前的换行符
        while (match("NEWLINE")) {
            // 继续跳过
        }

        if (isAtEnd()) return null;

        if (match("IMPORT")) {
            return parseImportStatement();
        }
        if (match("VAR")) {
            return parseVariableDeclaration();
        }

        // 新增：检查是否是类型声明（如 int x, string name 等）
        if (check("IDENTIFIER") && isTypeName(peek().getLexeme())) {
            return parseTypedVariableDeclaration();
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
        boolean isExplicitCast = false;
        String castType = null;

        // 检查是否有类型提示（新语法：类型名直接作为开始）
        if (check("IDENTIFIER") && isTypeName(peek().getLexeme())) {
            typeHint = advance().getLexeme();
        }

        String name = consume("IDENTIFIER", "Expect variable name").getLexeme();

        Expression initializer = null;
        if (match("EQUAL")) {
            // 检查是否有显式类型转换语法：newName = oldName(type)
            if (check("IDENTIFIER")) {
                Token identifierToken = peek();
                String identifierName = identifierToken.getLexeme();

                // 查看下一个token是否是左括号接类型名
                if (current + 1 < tokens.size() &&
                        tokens.get(current + 1).getType().equals("LEFT_PAREN") &&
                        current + 2 < tokens.size() &&
                        tokens.get(current + 2).getType().equals("IDENTIFIER") &&
                        isTypeName(tokens.get(current + 2).getLexeme()) &&
                        current + 3 < tokens.size() &&
                        tokens.get(current + 3).getType().equals("RIGHT_PAREN")) {

                    // 这是显式类型转换：name = oldName(type)
                    advance(); // 消耗标识符
                    advance(); // 消耗左括号
                    castType = advance().getLexeme(); // 消耗类型名
                    advance(); // 消耗右括号
                    isExplicitCast = true;

                    // 创建变量表达式作为被转换的值
                    Expression sourceExpr = new VariableExpression(identifierName,
                            identifierToken.getLine(), identifierToken.getColumn());

                    // 创建类型转换表达式
                    initializer = new TypeCastExpression(sourceExpr, castType,
                            identifierToken.getLine(), identifierToken.getColumn());
                } else {
                    // 普通表达式
                    initializer = parseExpression();
                }
            } else {
                // 普通表达式
                initializer = parseExpression();
            }
        }

        // 如果是隐式类型转换（强类型声明但初始值类型不同），生成警告
        if (typeHint != null && initializer != null && !isExplicitCast) {
            // 这里我们只记录需要警告，实际警告在解释器阶段生成
            System.out.println("@ [WARNING] Implicit type conversion for variable: " + name);
        }

        return new VariableDeclaration(name, typeHint, initializer, isExplicitCast,
                varToken.getLine(), varToken.getColumn());
    }

    private Statement parseLoopStatement() {
        Token loopToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'loop'");
        Expression condition = parseExpression();
        consume("RIGHT_PAREN", "Expect ')' after loop condition");
        consume("COLON", "Expect ':' after loop condition");

        // 跳过换行符（如果有）
        match("NEWLINE");

        // 解析所有缩进的语句作为循环体
        List<Statement> body = parseIndentedBlock();

        return new LoopStatement(condition, body,
                loopToken.getLine(), loopToken.getColumn());
    }

    /**
     * 解析缩进代码块
     */
    private List<Statement> parseIndentedBlock() {
        List<Statement> statements = new ArrayList<>();

        if (isAtEnd()) {
            return statements;
        }

        // 获取第一行的缩进级别
        int baseIndent = getCurrentIndent();
        int blockIndent = -1;

        // 跳过开头的空行
        while (match("NEWLINE")) {
            // 继续跳过
        }

        // 解析缩进块中的语句
        while (!isAtEnd()) {
            // 检查当前token的缩进
            Token currentToken = peek();
            int currentIndent = currentToken.getColumn();

            // 如果是文件开始或者没有缩进，结束块
            if (currentIndent <= baseIndent) {
                break;
            }

            // 如果是块的第一条语句，记录缩进级别
            if (blockIndent == -1) {
                blockIndent = currentIndent;
            }

            // 如果缩进级别匹配，解析语句
            if (currentIndent >= blockIndent) {
                Statement stmt = parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }

                // 跳过语句后的换行符
                while (match("NEWLINE")) {
                    // 继续跳过
                }
            } else {
                // 缩进减少，结束块
                break;
            }
        }

        return statements;
    }

    /**
     * 检查当前行是否缩进
     */
    private boolean isIndentedLine() {
        if (isAtEnd()) return false;

        Token token = peek();
        // 简化判断：如果列号大于1，则认为有缩进
        return token.getColumn() > 1;
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

        // 检查是否是方法调用：ClassName.method(args)
        if (expr instanceof VariableExpression && match("DOT")) {
            if (match("IDENTIFIER")) {
                String methodName = previous().getLexeme();

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

        // 简化处理：解析直到遇到非缩进行或文件结束
        // 这里我们假设缩进级别是固定的（4个空格）
        int currentIndent = getCurrentIndent();

        while (!isAtEnd()) {
            // 检查下一行的缩进级别
            int nextIndent = peekNextIndent();

            // 如果下一行的缩进小于当前缩进，则结束块
            if (nextIndent < currentIndent) {
                break;
            }

            // 如果下一行没有缩进，也结束块
            if (nextIndent == 0) {
                break;
            }

            // 解析语句
            Statement stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }

            // 跳过换行符
            if (match("NEWLINE")) {
                // 继续
            }
        }

        return statements;
    }

    /**
     *  获取当前token的缩进级别
     */
    private int getCurrentIndent() {
        if (isAtEnd()) return 0;

        Token token = peek();
        // 列号从1开始，所以缩进级别是列号-1
        return token.getColumn() - 1;
    }

    /**
     * 查看下一行的缩进级别
     */
    private int peekNextIndent() {
        // 保存当前状态
        int savedCurrent = current;

        // 跳过空白和换行符，找到下一个非空白字符
        while (!isAtEnd()) {
            Token token = tokens.get(current);
            if (token.getType().equals("NEWLINE")) {
                current++;
                continue;
            }

            // 计算缩进
            int indent = calculateIndent(token);
            current = savedCurrent; // 恢复状态
            return indent;
        }

        current = savedCurrent;
        return 0;
    }

    /**
     * 计算token的缩进级别
     */
    private int calculateIndent(Token token) {
        // 简化处理：假设每4个空格为一个缩进级别
        // 实际应该根据token的列号来计算
        return token.getColumn() / 4;
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


    /**
     * 检查标识符是否是类型名
     */
    private boolean isTypeName(String identifier) {
        Set<String> typeNames = new HashSet<>();
        typeNames.add("int");
        typeNames.add("int8");
        typeNames.add("byte");
        typeNames.add("int16");
        typeNames.add("short");
        typeNames.add("int32");
        typeNames.add("int64");
        typeNames.add("long");
        typeNames.add("double");
        typeNames.add("float");
        typeNames.add("bool");
        typeNames.add("boolean");
        typeNames.add("string");
        typeNames.add("char");
        typeNames.add("large");
        return typeNames.contains(identifier);
    }

    /**
     * 解析类型变量声明（如 int x, string name = "hello"）
     */
    private VariableDeclaration parseTypedVariableDeclaration() {
        Token typeToken = advance(); // 消耗类型名
        String typeName = typeToken.getLexeme();

        String name = consume("IDENTIFIER", "Expect variable name after type").getLexeme();

        Expression initializer = null;
        if (match("EQUAL")) {
            initializer = parseExpression();
        }

        return new VariableDeclaration(name, typeName, initializer,
                typeToken.getLine(), typeToken.getColumn());
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