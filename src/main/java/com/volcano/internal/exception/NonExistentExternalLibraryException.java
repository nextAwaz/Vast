package com.volcano.internal.exception;

/**
 * 找不到外置库异常
 * 当通过imp引入不存在的外置库时抛出
 */
public class NonExistentExternalLibraryException extends NotGrammarException {
    public NonExistentExternalLibraryException() {
        super("External library not found");
    }

    public NonExistentExternalLibraryException(String message) {
        super(message);
    }

    public NonExistentExternalLibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    // 使用静态工厂方法替代重复的构造函数
    public static NonExistentExternalLibraryException forLibrary(String libraryName) {
        return new NonExistentExternalLibraryException("External library '" + libraryName + "' not found or cannot be loaded");
    }

    public static NonExistentExternalLibraryException forLibrary(String libraryName, Throwable cause) {
        return new NonExistentExternalLibraryException("External library '" + libraryName + "' not found or cannot be loaded", cause);
    }
}