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

    // 不换行打印
    public static void print(Object obj) {
        if (obj == null) {
            out.print("null");
        } else if (obj instanceof Fraction) {
            Fraction fraction = (Fraction) obj;
            out.print(fraction.toString());
        } else {
            out.print(obj.toString());
        }
    }

    // 换行打印
    public static void printl(Object obj) {
        if (obj == null) {
            out.println("null");
        } else if (obj instanceof Fraction) {
            Fraction fraction = (Fraction) obj;
            out.println(fraction.toString());
        } else {
            out.println(obj.toString());
        }
    }

    // 打印空行
    public static void printl() {
        out.println();
    }

    // 格式化打印（不换行）
    public static void printf(String format, Object... args) {
        if (format == null) {
            out.print("null");
            return;
        }

        try {
            // 处理 Fraction 类型的参数
            Object[] processedArgs = processFractionArgs(args);
            String result = formatString(format, processedArgs);
            out.print(result);
        } catch (Exception e) {
            // 如果格式化失败，回退到简单拼接
            out.print("Format error: " + format);
            for (Object arg : args) {
                out.print(" " + formatObject(arg));
            }
        }
    }

    // 格式化并换行打印
    public static void printlf(String format, Object... args) {
        if (format == null) {
            out.println("null");
            return;
        }

        try {
            // 处理 Fraction 类型的参数
            Object[] processedArgs = processFractionArgs(args);
            String result = formatString(format, processedArgs);
            out.println(result);
        } catch (Exception e) {
            // 如果格式化失败，回退到简单拼接
            out.print("Format error: " + format);
            for (Object arg : args) {
                out.print(" " + formatObject(arg));
            }
            out.println();
        }
    }

    /**
     * 格式化字符串，支持两种语法：
     * 1. C# 风格：{0} {1} {2}
     * 2. Java 风格：%s %d %f（在花括号内使用）
     * 3. 转义花括号：\{ 和 \}
     */
    private static String formatString(String format, Object[] args) {
        if (args == null || args.length == 0) {
            return unescapeBraces(format);
        }

        // 先处理转义的花括号
        String unescapedFormat = unescapeBraces(format);

        // 尝试使用 C# 风格的 {0} {1} 格式化
        try {
            return MessageFormat.format(unescapedFormat, args);
        } catch (IllegalArgumentException e) {
            // 如果 MessageFormat 失败，尝试混合格式
            return mixedFormat(unescapedFormat, args);
        }
    }

    /**
     * 处理转义的花括号：将 \{ 和 \} 转换为真实的花括号
     */
    private static String unescapeBraces(String format) {
        if (format == null) return null;

        // 使用正则表达式匹配转义的花括号
        // 匹配 \{ 和 \} 但不匹配 \\{ 和 \\}
        Pattern pattern = Pattern.compile("(?<!\\\\)\\\\[{}]");
        Matcher matcher = pattern.matcher(format);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String escaped = matcher.group();
            // 去掉反斜杠，保留花括号
            String replacement = escaped.substring(1);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        // 处理双反斜杠的情况（转义的反斜杠）
        return result.toString().replace("\\\\", "\\");
    }

    /**
     * 混合格式化：支持在花括号内使用 Java 格式说明符
     * 例如：{0, %s} {1, %05d} {2, %.2f}
     */
    private static String mixedFormat(String format, Object[] args) {
        // 匹配 {数字, 格式} 模式
        Pattern pattern = Pattern.compile("\\{(\\d+)(?:\\s*,\\s*([^}]+))?\\}");
        Matcher matcher = pattern.matcher(format);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String formatSpecifier = matcher.group(2);

            if (index < 0 || index >= args.length) {
                // 索引超出范围，保留原样
                matcher.appendReplacement(result, matcher.group());
                continue;
            }

            Object arg = args[index];
            String replacement;

            if (formatSpecifier != null && !formatSpecifier.trim().isEmpty()) {
                // 有格式说明符
                replacement = applyFormatSpecifier(arg, formatSpecifier.trim());
            } else {
                // 没有格式说明符，直接转换为字符串
                replacement = formatObject(arg);
            }

            // 对替换文本中的特殊字符进行转义，避免在 appendReplacement 中出错
            replacement = replacement.replace("\\", "\\\\").replace("$", "\\$");
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 应用 Java 风格的格式说明符
     */
    private static String applyFormatSpecifier(Object arg, String formatSpecifier) {
        if (arg == null) return "null";

        // 处理 Fraction 类型
        if (arg instanceof Fraction) {
            Fraction fraction = (Fraction) arg;
            return applyFractionFormatSpecifier(fraction, formatSpecifier);
        }

        // 去掉可能的 % 符号（为了兼容性）
        if (formatSpecifier.startsWith("%")) {
            formatSpecifier = formatSpecifier.substring(1);
        }

        try {
            if (arg instanceof Number) {
                return formatNumber((Number) arg, formatSpecifier);
            } else if (arg instanceof String) {
                return formatString((String) arg, formatSpecifier);
            } else {
                // 其他类型使用默认格式化
                return String.format("%" + formatSpecifier, arg);
            }
        } catch (Exception e) {
            // 格式化失败，返回默认字符串表示
            return formatObject(arg);
        }
    }

    /**
     * 数字格式化
     */
    private static String formatNumber(Number number, String formatSpecifier) {
        // 首先检查是否是 Fraction 类型（通过类名判断）
        String className = number.getClass().getName();
        if (className.equals("com.vast.internal.Fraction")) {
            // 使用反射来安全地处理 Fraction
            try {
                java.lang.reflect.Method toStringMethod = number.getClass().getMethod("toString");
                String fractionStr = (String) toStringMethod.invoke(number);

                // 根据格式说明符处理 Fraction 字符串
                if (formatSpecifier.endsWith("f") || formatSpecifier.endsWith("e") ||
                        formatSpecifier.endsWith("g")) {
                    // 获取 Fraction 的 double 值
                    java.lang.reflect.Method toDoubleMethod = number.getClass().getMethod("toDouble");
                    double doubleValue = (Double) toDoubleMethod.invoke(number);
                    return String.format("%" + formatSpecifier, doubleValue);
                } else {
                    // 使用分数的字符串表示
                    return String.format("%" + formatSpecifier + "s", fractionStr);
                }
            } catch (Exception e) {
                // 如果反射失败，回退到默认处理
                return number.toString();
            }
        }

        // 处理标准的 Number 类型
        if (number instanceof Integer || number instanceof Long) {
            return String.format("%" + formatSpecifier + "d", number);
        } else if (number instanceof Double || number instanceof Float) {
            return String.format("%" + formatSpecifier + "f", number);
        } else {
            return String.format("%" + formatSpecifier, number);
        }
    }

    /**
     * 字符串格式化
     */
    private static String formatString(String str, String formatSpecifier) {
        return String.format("%" + formatSpecifier + "s", str);
    }

    // 原有的其他方法保持不变...
    public static void error(Object obj) {
        if (obj == null) {
            err.println("[ERROR] null");
        } else if (obj instanceof Fraction) {
            Fraction fraction = (Fraction) obj;
            err.println("[ERROR] " + fraction.toString());
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

    private static String applyFractionFormatSpecifier(Fraction fraction, String formatSpecifier) {
        // 去掉可能的 % 符号
        if (formatSpecifier.startsWith("%")) {
            formatSpecifier = formatSpecifier.substring(1);
        }

        try {
            // 根据格式说明符的类型处理
            if (formatSpecifier.endsWith("f") || formatSpecifier.endsWith("e") ||
                    formatSpecifier.endsWith("g")) {
                // 浮点数格式
                return String.format("%" + formatSpecifier, fraction.toDouble());
            } else if (formatSpecifier.endsWith("d")) {
                // 整数格式
                return String.format("%" + formatSpecifier, fraction.toInt());
            } else if (formatSpecifier.endsWith("s")) {
                // 字符串格式 - 使用分数表示
                return String.format("%" + formatSpecifier, fraction.toString());
            } else {
                // 默认使用分数字符串表示
                return fraction.toString();
            }
        } catch (Exception e) {
            // 格式化失败，返回默认分数表示
            return fraction.toString();
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
            // 对于格式化输出，我们返回字符串表示
            return fraction.toString();
        } else if (arg instanceof Object[]) {
            // 处理数组中的 Fraction
            Object[] array = (Object[]) arg;
            Object[] processedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                processedArray[i] = processFractionArg(array[i]);
            }
            return processedArray;
        }
        return arg;
    }

    /**
     * 格式化对象为字符串，支持 Fraction
     */
    private static String formatObject(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof Fraction) {
            return ((Fraction) obj).toString();
        } else if (obj instanceof Object[]) {
            // 处理数组
            Object[] array = (Object[]) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatObject(array[i]));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return obj.toString();
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
            // 如果还有剩余字符，或者还需要更多部分
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }


    // Fraction 相关的方法
    /**
     * 解析字符串为 Fraction
     */
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

    /**
     * 检查对象是否为 Fraction
     */
    public static boolean isFraction(Object obj) {
        return obj instanceof Fraction;
    }

    /**
     * 获取 Fraction 的分子
     */
    public static int getFractionNumerator(Fraction fraction) {
        return fraction.getNumerator();
    }

    /**
     * 获取 Fraction 的分母
     */
    public static int getFractionDenominator(Fraction fraction) {
        return fraction.getDenominator();
    }

    /**
     * 将 Fraction 转换为 double
     */
    public static double fractionToDouble(Fraction fraction) {
        return fraction.toDouble();
    }

    /**
     * 将 Fraction 转换为整数（四舍五入）
     */
    public static int fractionToInt(Fraction fraction) {
        return (int) Math.round(fraction.toDouble());
    }

    /**
     * 简化分数
     */
    public static Fraction simplifyFraction(int numerator, int denominator) {
        return new Fraction(numerator, denominator);
    }

    /**
     * 创建分数
     */
    public static Fraction createFraction(int numerator, int denominator) {
        return new Fraction(numerator, denominator);
    }

    /**
     * Fraction 加法
     */
    public static Fraction fractionAdd(Fraction a, Fraction b) {
        // a/b + c/d = (a*d + b*c) / (b*d)
        int numerator = a.getNumerator() * b.getDenominator() + b.getNumerator() * a.getDenominator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    /**
     * Fraction 减法
     */
    public static Fraction fractionSubtract(Fraction a, Fraction b) {
        // a/b - c/d = (a*d - b*c) / (b*d)
        int numerator = a.getNumerator() * b.getDenominator() - b.getNumerator() * a.getDenominator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    /**
     * Fraction 乘法
     */
    public static Fraction fractionMultiply(Fraction a, Fraction b) {
        // a/b * c/d = (a*c) / (b*d)
        int numerator = a.getNumerator() * b.getNumerator();
        int denominator = a.getDenominator() * b.getDenominator();
        return new Fraction(numerator, denominator);
    }

    /**
     * Fraction 除法
     */
    public static Fraction fractionDivide(Fraction a, Fraction b) {
        // a/b ÷ c/d = (a*d) / (b*c)
        int numerator = a.getNumerator() * b.getDenominator();
        int denominator = a.getDenominator() * b.getNumerator();
        return new Fraction(numerator, denominator);
    }

    /**
     * Fraction 比较
     */
    public static int fractionCompare(Fraction a, Fraction b) {
        double valueA = a.toDouble();
        double valueB = b.toDouble();
        return Double.compare(valueA, valueB);
    }
}