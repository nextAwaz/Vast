package com.volcano.registry;

import java.util.Objects;

/**
 * 注册表令牌 - 用于标识和管理外置库的注册状态
 */
public class RegistryToken {
    private final String libraryName;
    private final Class<?> libraryClass;
    private final boolean enabled;
    private final int priority;

    public RegistryToken(String libraryName, Class<?> libraryClass) {
        this(libraryName, libraryClass, true, 0);
    }

    public RegistryToken(String libraryName, Class<?> libraryClass, boolean enabled, int priority) {
        this.libraryName = Objects.requireNonNull(libraryName, "Library name cannot be null");
        this.libraryClass = Objects.requireNonNull(libraryClass, "Library class cannot be null");
        this.enabled = enabled;
        this.priority = priority;
    }

    // Getters
    public String getLibraryName() { return libraryName; }
    public Class<?> getLibraryClass() { return libraryClass; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }

    /**
     * 创建一个禁用的令牌副本
     */
    public RegistryToken disabled() {
        return new RegistryToken(libraryName, libraryClass, false, priority);
    }

    /**
     * 创建一个带有新优先级的令牌副本
     */
    public RegistryToken withPriority(int newPriority) {
        return new RegistryToken(libraryName, libraryClass, enabled, newPriority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryToken that = (RegistryToken) o;
        return libraryName.equals(that.libraryName);
    }

    @Override
    public int hashCode() {
        return libraryName.hashCode();
    }

    @Override
    public String toString() {
        return String.format("RegistryToken{name='%s', class=%s, enabled=%s, priority=%d}",
                libraryName, libraryClass.getSimpleName(), enabled, priority);
    }
}