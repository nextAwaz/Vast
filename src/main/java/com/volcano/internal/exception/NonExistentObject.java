package com.volcano.internal.exception;

/**
 * 不存在的对象异常
 * 向外部程序传参数时，声明的接受参数的对象不存在
 */
public class NonExistentObject extends PassParameterException {
    public NonExistentObject() {
        super("Target object does not exist");
    }

    public NonExistentObject(String message) {
        super(message);
    }

    public NonExistentObject(String message, Throwable cause) {
        super(message, cause);
    }

    // 移除重复的构造函数，只保留一个带两个String参数的构造函数
    public NonExistentObject(String objectType, String objectName) {
        super(objectType + " '" + objectName + "' does not exist");
    }

    // 为方法不存在的情况创建专门的静态工厂方法
    public static NonExistentObject methodNotFound(String className, String methodName) {
        return new NonExistentObject("Method '" + methodName + "' not found in class '" + className + "'");
    }

    public static NonExistentObject classNotFound(String className) {
        return new NonExistentObject("Class", className);
    }

    public static NonExistentObject variableNotFound(String varName) {
        return new NonExistentObject("Variable", varName);
    }

    public static NonExistentObject fieldNotFound(String className, String fieldName) {
        return new NonExistentObject("Field '" + fieldName + "' not found in class '" + className + "'");
    }
}