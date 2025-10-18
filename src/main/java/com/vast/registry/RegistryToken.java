package com.vast.registry;

import java.util.Objects;

public class RegistryToken {
    private final String libraryId;
    private final Class<?> libraryClass;
    private final boolean enabled;
    private final int priority;

    public RegistryToken(String libraryId, Class<?> libraryClass) {
        this(libraryId, libraryClass, true, 0);
    }

    public RegistryToken(String libraryId, Class<?> libraryClass, boolean enabled, int priority) {
        this.libraryId = Objects.requireNonNull(libraryId, "Library ID cannot be null");
        this.libraryClass = Objects.requireNonNull(libraryClass, "Library class cannot be null");
        this.enabled = enabled;
        this.priority = priority;
    }

    public String getLibraryId() { return libraryId; }
    public Class<?> getLibraryClass() { return libraryClass; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryToken that = (RegistryToken) o;
        return libraryId.equals(that.libraryId);
    }

    @Override
    public int hashCode() {
        return libraryId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("RegistryToken{id='%s', class=%s, enabled=%s, priority=%d}",
                libraryId, libraryClass.getSimpleName(), enabled, priority);
    }
}