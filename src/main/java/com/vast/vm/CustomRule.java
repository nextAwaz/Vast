package com.vast.vm;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 自定义语法规则
 */
public class CustomRule {
    private final String name;
    private final String id;
    private final List<String> keywords;
    private final String pattern;
    private final boolean overlookSpaces;
    private final Set<Character> overlookChars;

    public CustomRule(String name, String id, List<String> keywords, String pattern,
                      boolean overlookSpaces, Set<Character> overlookChars) {
        this.name = name;
        this.id = id;
        this.keywords = Collections.unmodifiableList(keywords);
        this.pattern = pattern;
        this.overlookSpaces = overlookSpaces;
        this.overlookChars = Collections.unmodifiableSet(overlookChars);
    }

    // Getters
    public String getName() { return name; }
    public String getId() { return id; }
    public List<String> getKeywords() { return keywords; }
    public String getPattern() { return pattern; }
    public boolean isOverlookSpaces() { return overlookSpaces; }
    public Set<Character> getOverlookChars() { return overlookChars; }

    @Override
    public String toString() {
        return String.format("CustomRule{name='%s', id='%s', keywords=%s, pattern='%s'}",
                name, id, keywords, pattern);
    }
}