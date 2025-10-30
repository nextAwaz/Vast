package com.vast.parser;

import com.vast.internal.Debugger;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

//词法分析器
public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;

    // 关键字映射
    private static final Map<String, String> KEYWORDS = new HashMap<>();

    private final Debugger debugger = Debugger.getInstance();

    static {
        KEYWORDS.put("imp", "IMPORT");
        KEYWORDS.put("loop", "LOOP");
        KEYWORDS.put("use", "USE");
        KEYWORDS.put("swap", "SWAP");
        KEYWORDS.put("true", "TRUE");
        KEYWORDS.put("false", "FALSE");
        KEYWORDS.put("if", "IF");
        KEYWORDS.put("else", "ELSE");
        KEYWORDS.put("while", "WHILE");
        KEYWORDS.put("for", "FOR");
    }

    public Lexer(String source) {
        this.source = source;
    }

    /**
     * 扫描所有词法单元
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        // 添加文件结束标记
        tokens.add(new Token("EOF", "", line, column));
        return tokens;
    }


    private void scanToken() {
        char c = advance();

        switch (c) {
            case '(': addToken("LEFT_PAREN"); break;
            case ')': addToken("RIGHT_PAREN"); break;
            case '{': addToken("LEFT_BRACE"); break;
            case '}': addToken("RIGHT_BRACE"); break;
            case ',': addToken("COMMA"); break;
            case '.': addToken("DOT"); break;
            case ':': addToken("COLON");break;
            case '-':
                if (match('=')) {
                    addToken("MINUS_EQUAL"); // -=
                } else if (match('-')) {
                    addToken("MINUS_MINUS"); // --
                } else {
                    addToken("MINUS");
                }
                break;
            case '+':
                if (match('=')) {
                    addToken("PLUS_EQUAL");  // +=
                } else if (match('+')) {
                    addToken("PLUS_PLUS");   // ++
                } else {
                    addToken("PLUS");
                }
                break;
            case '*':
                if (match('=')) {
                    addToken("STAR_EQUAL");  // *=
                } else if (match('*')) {
                    addToken("STAR_STAR");   // **
                } else {
                    addToken("STAR");
                }
                break;
            case '/':
                if (match('=')) {
                    addToken("SLASH_EQUAL"); // /=
                } else if (match('/')) {
                    if (match('=')) {
                        addToken("SLASH_SLASH_EQUAL"); // //=
                    } else {
                        addToken("SLASH_SLASH"); // //
                    }
                } else {
                    addToken("SLASH");
                }
                break;
            case '%':
                if (match('=')) {
                    addToken("PERCENT_EQUAL"); // %=
                } else {
                    addToken("PERCENT");
                }
                break;
            case '=':
                if (match('=')) {
                    addToken("EQUAL_EQUAL");
                } else {
                    addToken("EQUAL");
                }
                break;
            case '!':
                if (match('=')) {
                    addToken("BANG_EQUAL");
                } else {
                    addToken("BANG");
                }
                break;
            case '<':
                if (match('=')) {
                    addToken("LESS_EQUAL");
                } else {
                    addToken("LESS");
                }
                break;
            case '>':
                if (match('=')) {
                    addToken("GREATER_EQUAL");
                } else {
                    addToken("GREATER");
                }
                break;
            case '|':
                if (match('|')) {
                    addToken("OR");  // 逻辑或
                } else {
                    addToken("BITWISE_OR");  // 按位或
                }
                break;

            case '&':
                if (match('&')) {
                    addToken("AND");  // 逻辑与
                } else {
                    addToken("BITWISE_AND");  // 按位与
                }
                break;

            case '^': addToken("BITWISE_XOR"); break;  // 按位异或
            case '~': addToken("BITWISE_NOT"); break;  // 按位取反

            case '$':
                if (match('$')) {
                    addToken("DOUBLE_DOLLAR");  // 永久分数修饰符
                } else {
                    addToken("DOLLAR");  // 单步分数修饰符
                }
                break;
            case '`':
                addToken("BACKQUOTE");//开方运算（由于键盘上面没有开方符号，所以使用反引号代替）
                break;

            case ' ':
            case '\r':
            case '\t':
                // 忽略空白字符
                break;

            case '\n':
                addToken("NEWLINE");
                line++;
                column = 1;
                break;

            case '#':
                // 单行注释
                if (match('#')) {
                    // 检查是否是多行注释
                    if (match('#')) {
                        skipMultiLineComment();
                    } else {
                        skipLineComment();
                    }
                } else {
                    skipLineComment();
                }
                break;

            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    error("Unexpected character: '" + c + "'");
                }
                break;
        }
    }

    private void skipLineComment() {
        // 跳过直到行尾
        while (peek() != '\n' && !isAtEnd()) {
            advance();
        }
    }

    private void skipMultiLineComment() {
        // 跳过直到遇到 ###
        while (!isAtEnd()) {
            if (peek() == '#' && peekNext() == '#' && peekNextNext() == '#') {
                // 跳过 ###
                advance();
                advance();
                advance();
                break;
            }
            advance();
        }
    }

    private void string() {
        StringBuilder value = new StringBuilder();
        value.append('"');

        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                line++;
                column = 1;
            }

            if (peek() == '\\') {
                // 处理转义字符
                advance(); // 跳过 \
                char escapeChar = advance();
                switch (escapeChar) {
                    case 'n': value.append("\\n"); break;
                    case 't': value.append("\\t"); break;
                    case 'r': value.append("\\r"); break;
                    case 'b': value.append("\\b"); break;
                    case 'f': value.append("\\f"); break;
                    case '"': value.append("\\\""); break;
                    case '\\': value.append("\\\\"); break;
                    default: value.append("\\").append(escapeChar); break;
                }
            } else {
                value.append(advance());
            }
        }

        if (isAtEnd()) {
            error("Unterminated string");
            return;
        }

        // 闭合引号
        advance();
        value.append('"');

        addToken("STRING", value.toString());
    }

    private void number() {
        while (isDigit(peek())) advance();

        // 查找小数部分
        if (peek() == '.' && isDigit(peekNext())) {
            // 消耗小数点
            advance();

            while (isDigit(peek())) advance();
        }

        String numberText = source.substring(start, current);
        addToken("NUMBER", numberText);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        String type = KEYWORDS.get(text);
        if (type == null) {
            type = "IDENTIFIER";
        }
        addToken(type, text);
    }

    // 辅助方法
    private char advance() {
        current++;
        column++;
        return source.charAt(current - 1);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        column++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private char peekNextNext() {
        if (current + 2 >= source.length()) return '\0';
        return source.charAt(current + 2);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void addToken(String type) {
        addToken(type, null);
    }

    private void addToken(String type, String lexeme) {
        String text = lexeme != null ? lexeme : source.substring(start, current);
        tokens.add(new Token(type, text, line, column - text.length()));
    }

    private void error(String message) {
        debugger.error("Line " + line + ", Column " + column + ": " + message);
    }
}