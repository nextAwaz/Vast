package com.vast.parser;

//语法单元类
public class Token {
    private final String type;
    private final String lexeme;
    private final int line;
    private final int column;

    public Token(String type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    public String getType() { return type; }
    public String getLexeme() { return lexeme; }
    public int getLine() { return line; }
    public int getColumn() { return column; }

    @Override
    public String toString() {
        return type + " '" + lexeme + "' at " + line + ":" + column;
    }
}