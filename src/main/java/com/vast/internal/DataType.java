package com.vast.internal;

// 简化数据类型操作类，移除数组功能
public class DataType {

    // 字符串操作 - 保留基本功能
    public static int strLength(String str) {
        return str != null ? str.length() : 0;
    }

    public static String strSubstring(String str, int start, int end) {
        if (str == null) return "";
        if (start < 0) start = 0;
        if (end > str.length()) end = str.length();
        if (start >= end) return "";
        return str.substring(start, end);
    }

    public static String strToUpper(String str) {
        return str != null ? str.toUpperCase() : "";
    }

    public static String strToLower(String str) {
        return str != null ? str.toLowerCase() : "";
    }

    public static boolean strContains(String str, String search) {
        return str != null && search != null && str.contains(search);
    }

    public static String strReplace(String str, String oldStr, String newStr) {
        return str != null ? str.replace(oldStr, newStr) : "";
    }

    public static String strTrim(String str) {
        return str != null ? str.trim() : "";
    }

    // 数字操作 - 简化实现
    public static int numParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double numParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // 布尔操作 - 简化
    public static boolean boolParse(String str) {
        return "true".equalsIgnoreCase(str) || "1".equals(str);
    }

    public static String boolToString(boolean value) {
        return value ? "true" : "false";
    }

    // 类型检查和转换 - 简化
    public static String typeOf(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "string";
        if (obj instanceof Integer) return "number";
        if (obj instanceof Double) return "number";
        if (obj instanceof Boolean) return "boolean";
        return "object";
    }

    public static String toString(Object obj) {
        return obj != null ? obj.toString() : "null";
    }

    public static int toInt(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Double) return ((Double) obj).intValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (obj instanceof Boolean) return (Boolean) obj ? 1 : 0;
        return 0;
    }

    public static double toDouble(Object obj) {
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        if (obj instanceof Boolean) return (Boolean) obj ? 1.0 : 0.0;
        return 0.0;
    }

    public static boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
        if (obj instanceof String) return !((String) obj).isEmpty();
        return obj != null;
    }
}