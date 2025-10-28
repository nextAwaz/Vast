package com.vast.internal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 常用类
public class Sys {

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private static final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    private static final PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);

    // 不换行打印 - 支持格式化
    public static void print(Object obj, Object... args) {
        if (obj == null) {
            out.print("null");
            return;
        }

        String text = obj.toString();

        // 如果有格式化参数，进行格式化
        if (args != null && args.length > 0) {
            text = formatText(text, args);
        }

        out.print(text);
    }

    // 换行打印 - 支持格式化
    public static void printl(Object obj, Object... args) {
        if (obj == null) {
            out.println("null");
            return;
        }

        String text = obj.toString();

        // 如果有格式化参数，进行格式化
        if (args != null && args.length > 0) {
            text = formatText(text, args);
        }

        out.println(text);
    }

    // 打印空行
    public static void printl() {
        out.println();
    }

    /**
     * 格式化文本，支持C#风格的索引：{0} {1} {2}
     */
    private static String formatText(String format, Object[] args) {
        if (format == null || args == null || args.length == 0) {
            return format;
        }

        try {
            // 处理参数中的 Fraction 对象
            Object[] processedArgs = processFractionArgs(args);

            // 使用 MessageFormat 进行 C# 风格格式化
            return MessageFormat.format(format, processedArgs);

        } catch (Exception e) {
            // 如果格式化失败，回退到简单拼接
            return fallbackFormat(format, args);
        }
    }

    /**
     * 处理参数中的 Fraction 对象，转换为合适的显示格式
     */
    private static Object[] processFractionArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }

        Object[] processed = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            processed[i] = processFractionArg(args[i]);
        }
        return processed;
    }

    /**
     * 处理单个 Fraction 参数
     */
    private static Object processFractionArg(Object arg) {
        if (arg instanceof Fraction) {
            Fraction fraction = (Fraction) arg;
            return fraction.toString();
        }
        return arg;
    }

    /**
     * 格式化失败时的回退方案
     */
    private static String fallbackFormat(String format, Object[] args) {
        StringBuilder result = new StringBuilder(format);

        // 简单的 {0} {1} 替换
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String replacement = formatObject(args[i]);

            int index = result.indexOf(placeholder);
            while (index != -1) {
                result.replace(index, index + placeholder.length(), replacement);
                index = result.indexOf(placeholder, index + replacement.length());
            }
        }

        return result.toString();
    }

    /**
     * 格式化对象为字符串，支持 Fraction
     */
    private static String formatObject(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof Fraction) {
            return ((Fraction) obj).toString();
        } else {
            return obj.toString();
        }
    }

    // 错误输出 - 支持格式化
    public static void error(Object obj, Object... args) {
        if (obj == null) {
            err.println("[ERROR] null");
            return;
        }

        String text = obj.toString();

        // 如果有格式化参数，进行格式化
        if (args != null && args.length > 0) {
            text = formatText(text, args);
        }

        err.println("[ERROR] " + text);
    }

    // 其他方法保持不变...
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
            error("Input error: {0}", e.getMessage());
            return "";
        }
    }

    public static String input(String prompt) {
        try {
            out.print(prompt);
            out.flush();
            return reader.readLine();
        } catch (Exception e) {
            error("Input error: {0}", e.getMessage());
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
            error("Multi-value input error: {0}", e.getMessage());
            String[] result = new String[valueCount];
            for (int i = 0; i < valueCount; i++) {
                result[i] = "";
            }
            return result;
        }
    }

    private static String[] splitRespectingQuotes(String line, int maxParts) {
        List<String> parts = new ArrayList<>();
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
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    // Fraction 相关的方法保持不变...
    public static Fraction parseFraction(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new Fraction(0, 1);
        }

        try {
            // 处理分数格式 "a/b"
            if (str.contains("/")) {
                String[] parts = str.split("/");
                if (parts.length == 2) {
                    int numerator = Integer.parseInt(parts[0].trim());
                    int denominator = Integer.parseInt(parts[1].trim());
                    return new Fraction(numerator, denominator);
                }
            }

            // 处理整数
            return new Fraction(Integer.parseInt(str.trim()), 1);
        } catch (NumberFormatException e) {
            // 处理小数
            try {
                double value = Double.parseDouble(str.trim());
                return Fraction.fromNumber(value);
            } catch (NumberFormatException e2) {
                throw new IllegalArgumentException("Cannot parse fraction from: " + str);
            }
        }
    }

    public static boolean isFraction(Object obj) {
        return obj instanceof Fraction;
    }

    public static int getFractionNumerator(Fraction fraction) {
        return fraction.getNumerator();
    }

    public static int getFractionDenominator(Fraction fraction) {
        return fraction.getDenominator();
    }

    public static double fractionToDouble(Fraction fraction) {
        return fraction.toDouble();
    }

    public static int fractionToInt(Fraction fraction) {
        return (int) Math.round(fraction.toDouble());
    }

    public static Fraction simplifyFraction(int numerator, int denominator) {
        return new Fraction(numerator, denominator);
    }

    public static Fraction createFraction(int numerator, int denominator) {
        return new Fraction(numerator, denominator);
    }

    public static Fraction fractionAdd(Fraction a, Fraction b) {
        int numerator = a.getNumerator() * b.getDenominator() + b.getNumerator() * a.getDenominator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    public static Fraction fractionSubtract(Fraction a, Fraction b) {
        int numerator = a.getNumerator() * b.getDenominator() - b.getNumerator() * a.getDenominator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    public static Fraction fractionMultiply(Fraction a, Fraction b) {
        int numerator = a.getNumerator() * b.getNumerator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    public static Fraction fractionDivide(Fraction a, Fraction b) {
        int numerator = a.getNumerator() * b.getDenominator();
        int denominator = a.getDenominator() * b.getNumerator();
        return new Fraction(numerator, denominator);
    }

    public static int fractionCompare(Fraction a, Fraction b) {
        double valueA = a.toDouble();
        double valueB = b.toDouble();
        return Double.compare(valueA, valueB);
    }
}