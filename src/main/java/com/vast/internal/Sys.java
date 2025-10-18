package com.vast.internal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

// 常用类
public class Sys {

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private static final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    private static final PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);

    public static void print(Object obj) {
        if (obj == null) {
            out.println("null");
        } else {
            out.println(obj.toString());
        }
    }

    public static void error(Object obj) {
        if (obj == null) {
            err.println("[ERROR] null");
        } else {
            err.println("[ERROR] " + obj.toString());
        }
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static long time() {
        return System.currentTimeMillis();
    }

    public static void exit(int code) {
        System.exit(code);
    }

    public static String input() {
        try {
            return reader.readLine();
        } catch (Exception e) {
            error("Input error: " + e.getMessage());
            return "";
        }
    }

    public static String input(String prompt) {
        try {
            out.print(prompt);
            out.flush();
            return reader.readLine();
        } catch (Exception e) {
            error("Input error: " + e.getMessage());
            return "";
        }
    }

    public static String[] multiValueInput(String prompt, int valueCount) {
        try {
            out.print(prompt);
            out.flush();
            String line = reader.readLine();

            if (line == null || line.trim().isEmpty()) {
                String[] empty = new String[valueCount];
                for (int i = 0; i < valueCount; i++) {
                    empty[i] = "";
                }
                return empty;
            }

            // 按空格分割，但保留引号内的内容
            String[] parts = splitRespectingQuotes(line, valueCount);
            String[] result = new String[valueCount];

            for (int i = 0; i < valueCount; i++) {
                if (i < parts.length) {
                    result[i] = parts[i].trim();
                } else {
                    result[i] = "";
                }
            }

            return result;

        } catch (Exception e) {
            error("Multi-value input error: " + e.getMessage());
            String[] result = new String[valueCount];
            for (int i = 0; i < valueCount; i++) {
                result[i] = "";
            }
            return result;
        }
    }

    private static String[] splitRespectingQuotes(String line, int maxParts) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean inEscape = false;

        for (int i = 0; i < line.length() && parts.size() < maxParts - 1; i++) {
            char c = line.charAt(i);

            if (inEscape) {
                current.append(c);
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        // 添加最后一个部分
        if (current.length() > 0 || parts.size() < maxParts) {
            // 如果还有剩余字符，或者还需要更多部分
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }
}