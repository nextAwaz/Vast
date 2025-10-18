package com.vast.registry;

import java.util.List;
import java.util.Map;

/**
 * 外置库元数据
 */
public class LibraryMetadata {
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final List<String> dependencies;
    private final Map<String, String> configuration;

    public LibraryMetadata(String name, String version, String description, String author) {
        this(name, version, description, author, List.of(), Map.of());
    }

    public LibraryMetadata(String name, String version, String description,
                           String author, List<String> dependencies, Map<String, String> configuration) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.dependencies = List.copyOf(dependencies);
        this.configuration = Map.copyOf(configuration);
    }

    // Getters
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public List<String> getDependencies() { return dependencies; }
    public Map<String, String> getConfiguration() { return configuration; }

    @Override
    public String toString() {
        return String.format("%s v%s by %s - %s", name, version, author, description);
    }
}