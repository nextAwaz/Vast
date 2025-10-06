package com.volcano.internal.exception;

/**
 * 无法更改异常
 * 当尝试给final修饰的变量重新赋值时抛出
 */
public class CannotBeChanged extends PassParameterException {
    public CannotBeChanged() {
        super("Cannot change final variable");
    }

    public CannotBeChanged(String message) {
        super(message);
    }

    public CannotBeChanged(String message, Throwable cause) {
        super(message, cause);
    }

    // 使用静态工厂方法替代重复的构造函数
    public static CannotBeChanged forVariable(String variableName) {
        return new CannotBeChanged("Variable '" + variableName + "' is final and cannot be reassigned");
    }

    public static CannotBeChanged forField(String className, String fieldName) {
        return new CannotBeChanged("Field '" + fieldName + "' in class '" + className + "' is final and cannot be modified");
    }
}