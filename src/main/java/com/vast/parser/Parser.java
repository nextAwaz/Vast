package com.vast.parser;

import com.vast.ast.*;
import com.vast.ast.expressions.*;
import com.vast.ast.statements.*;
import com.vast.internal.Debugger;

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

    private final Debugger debugger = Debugger.getInstance();

    /**
     * 解析整个程序
     */
    public Program parseProgram() {
        List<Statement> statements = new ArrayList<>();
        debugger.debug("Parsing program");

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

        debugger.debug("Program parsed with " + statements.size() + " statements");
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

        // 检查是否是强类型声明（如 int x, string name 等）
        if (check("IDENTIFIER") && isTypeName(peek().getLexeme())) {
            return parseTypedVariableDeclaration();
        }

        if (match("LOOP")) {
            return parseLoopStatement();
        }
        if (match("USE")) {
            return parseUseStatement();
        }
        if (match("SWAP")) {
            return parseSwapStatement();
        }

        // 自由类型赋值或表达式语句
        return parseExpressionOrAssignment();
    }

    private Statement parseImportStatement() {
        Token importToken = previous();
        consume("IDENTIFIER", "Expect library or class name after 'imp'");
        String className = previous().getLexeme();

        return new ImportStatement(className,
                importToken.getLine(), importToken.getColumn());
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

    private Statement parseUseStatement() {
        Token useToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'use'");

        // 解析方法调用表达式（如：ClassName.methodName(args)）
        Expression methodCall = parseExpression();

        consume("RIGHT_PAREN", "Expect ')' after method call");

        // 使用新的构造函数
        return new UseStatement(methodCall, useToken.getLine(), useToken.getColumn());
    }

    private Statement parseSwapStatement() {
        Token swapToken = previous();
        consume("LEFT_PAREN", "Expect '(' after 'swap'");

        Expression varA = parseExpression();
        if (!(varA instanceof VariableExpression)) {
            throw error(peek(), "Swap operand must be a variable");
        }

        consume("COMMA", "Expect ',' between swap variables");

        Expression varB = parseExpression();
        if (!(varB instanceof VariableExpression)) {
            throw error(peek(), "Swap operand must be a variable");
        }

        consume("RIGHT_PAREN", "Expect ')' after swap variables");

        return new SwapStatement((VariableExpression) varA, (VariableExpression) varB,
                swapToken.getLine(), swapToken.getColumn());
    }

    private Statement parseExpressionOrAssignment() {
        Expression expr = parseExpression();

        // 检查是否是内联类型转换：type(expression)
        if (expr instanceof TypeCastExpression) {
            TypeCastExpression castExpr = (TypeCastExpression) expr;
            // 如果类型转换表达式后面没有赋值，就是内联类型转换
            if (!check("EQUAL")) {
                return new InlineTypeCastStatement(castExpr,
                        castExpr.getLineNumber(), castExpr.getColumnNumber());
            }
        }

        // 检查是否是方法调用
        if (expr instanceof VariableExpression && match("DOT")) {
            if (match("IDENTIFIER")) {
                String methodName = previous().getLexeme();

                if (match("LEFT_PAREN")) {
                    List<Expression> arguments = parseExpressionList();
                    consume("RIGHT_PAREN", "Expect ')' after arguments");

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

        // 检查是否是自由类型赋值
        if (expr instanceof VariableExpression && match("EQUAL")) {
            Expression value = parseExpression();

            // 自由类型赋值
            String varName = ((VariableExpression) expr).getName();
            return new AssignmentStatement(varName, value, null,
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

        // 检查是否是类型转换语法：newType newName = newType(oldName)
        if (check("LEFT_PAREN")) {
            return parseTypeCastAssignment(typeToken);
        }

        String name = consume("IDENTIFIER", "Expect variable name after type").getLexeme();

        Expression initializer = null;
        if (match("EQUAL")) {
            initializer = parseExpression();
        }

        return new VariableDeclaration(name, typeName, initializer, false,
                typeToken.getLine(), typeToken.getColumn());
    }

    /**
     * 解析类型转换赋值：newType newName = newType(oldName)
     */
    private VariableDeclaration parseTypeCastAssignment(Token typeToken) {
        String targetType = typeToken.getLexeme();

        consume("LEFT_PAREN", "Expect '(' after type for type cast assignment");

        Expression sourceExpr = parseExpression();

        consume("RIGHT_PAREN", "Expect ')' after expression in type cast");

        String name = consume("IDENTIFIER", "Expect variable name after type cast").getLexeme();

        consume("EQUAL", "Expect '=' in type cast assignment");

        // 解析右侧的类型转换表达式
        Expression typeCastExpr = parseExpression();

        // 验证右侧确实是类型转换表达式
        if (!(typeCastExpr instanceof TypeCastExpression)) {
            throw error(peek(), "Right side of type cast assignment must be a type cast expression");
        }

        TypeCastExpression castExpr = (TypeCastExpression) typeCastExpr;

        // 创建新的类型转换表达式，使用指定的变量名
        TypeCastExpression finalCastExpr = new TypeCastExpression(
                castExpr.getExpression(),
                targetType,
                true,
                typeToken.getLine(),
                typeToken.getColumn()
        );

        return new VariableDeclaration(name, targetType, finalCastExpr, true,
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

    //按位或运算
    private Expression parseBitwiseOr() {
        Expression expr = parseBitwiseXor();

        while (match("BITWISE_OR")) {
            Token operator = previous();
            Expression right = parseBitwiseXor();
            expr = new BitwiseExpression(expr, "|", right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }
    //按位异或运算
    private Expression parseBitwiseXor() {
        Expression expr = parseBitwiseAnd();

        while (match("BITWISE_XOR")) {
            Token operator = previous();
            Expression right = parseBitwiseAnd();
            expr = new BitwiseExpression(expr, "^", right,
                    operator.getLine(), operator.getColumn());
        }

        return expr;
    }
    //按位与运算
    private Expression parseBitwiseAnd() {
        Expression expr = parseEquality();

        while (match("BITWISE_AND")) {
            Token operator = previous();
            Expression right = parseEquality();
            expr = new BitwiseExpression(expr, "&", right,
                    operator.getLine(), operator.getColumn());
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
        // 处理分数修饰符
        if (match("BACKQUOTE")) {
            Token operator = previous();

            // 必须紧跟左括号
            if (!match("LEFT_PAREN")) {
                throw error(peek(), "Root modifier must be followed by parenthesized expression");
            }

            Expression expr = parseExpression();
            consume("RIGHT_PAREN", "Expect ')' after root expression");

            return new RootExpression(expr, operator.getLine(), operator.getColumn());
        }

        // 处理类型转换表达式：(type) expression
        if (match("LEFT_PAREN")) {
            // 检查是否是类型转换
            if (check("IDENTIFIER") && isTypeName(peek().getLexeme())) {
                Token typeToken = advance(); // 消耗类型名
                String targetType = typeToken.getLexeme();

                consume("RIGHT_PAREN", "Expect ')' after type in type cast");

                Expression expression = parseUnary();

                return new TypeCastExpression(expression, targetType, true,
                        typeToken.getLine(), typeToken.getColumn());
            } else {
                // 普通括号表达式
                Expression expr = parseExpression();
                consume("RIGHT_PAREN", "Expect ')' after expression");
                return expr;
            }
        }

        // 处理按位取反和其他一元运算符
        if (match("BANG", "MINUS", "PLUS_PLUS", "BITWISE_NOT")) {
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