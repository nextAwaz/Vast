package com.volcano.internal;

public class DataType {

    // 字符串操作
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

    // 数字操作 - 替代原来的 Math 类功能
    public static int numParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 幂运算方法
    public static double numPower(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    public static int numIntPower(int base, int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative for integer power");
        }
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    public static double numParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static int numAbs(int value) {
        return Math.abs(value);
    }

    public static double numAbs(double value) {
        return Math.abs(value);
    }

    public static int numMax(int a, int b) {
        return a > b ? a : b;
    }

    public static int numMin(int a, int b) {
        return a < b ? a : b;
    }

    public static long numRound(double value) {
        return Math.round(value);
    }

    public static double numCeil(double value) {
        return Math.ceil(value);
    }

    public static double numFloor(double value) {
        return Math.floor(value);
    }

    public static double numSqrt(double value) {
        return Math.sqrt(value);
    }

    public static double numPow(double base, double exponent) {
        return Math.pow(base, exponent);
    }

    public static int numRandom(int max) {
        return (int) (Math.random() * max);
    }

    // 布尔操作
    public static boolean boolParse(String str) {
        return "true".equalsIgnoreCase(str) || "1".equals(str);
    }

    public static String boolToString(boolean value) {
        return value ? "true" : "false";
    }

    public static boolean boolAnd(boolean a, boolean b) {
        return a && b;
    }

    public static boolean boolOr(boolean a, boolean b) {
        return a || b;
    }

    public static boolean boolNot(boolean a) {
        return !a;
    }

    public static boolean boolXor(boolean a, boolean b) {
        return a != b;
    }

    // 类型检查和转换
    public static String typeOf(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "string";
        if (obj instanceof Integer) return "number";
        if (obj instanceof Double) return "number";
        if (obj instanceof Boolean) return "boolean";
        if (obj instanceof Object[]) return "array";
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

    // 数组操作
    public static int arrLength(Object[] array) {
        return array != null ? array.length : 0;
    }

    public static boolean arrContains(Object[] array, Object value) {
        if (array == null) return false;
        for (Object item : array) {
            if (eq(item, value)) return true;
        }
        return false;
    }

    public static Object[] arrCreate(int size) {
        return new Object[size];
    }

    public static Object arrGet(Object[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return null;
        }
        return array[index];
    }

    public static void arrSet(Object[] array, int index, Object value) {
        if (array != null && index >= 0 && index < array.length) {
            array[index] = value;
        }
    }

    public static Object[] arrSlice(Object[] array, int start, int end) {
        if (array == null) return new Object[0];
        if (start < 0) start = 0;
        if (end > array.length) end = array.length;
        if (start >= end) return new Object[0];

        Object[] result = new Object[end - start];
        System.arraycopy(array, start, result, 0, end - start);
        return result;
    }

    private static boolean eq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
