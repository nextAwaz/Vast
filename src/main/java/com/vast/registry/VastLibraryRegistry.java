package com.vast.registry;

import com.vast.vm.VastVM;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VastLibraryRegistry {
    private static final VastLibraryRegistry INSTANCE = new VastLibraryRegistry();
    private final Map<String, RegistryToken> registeredLibraries = new ConcurrentHashMap<>();
    private final Map<String, VastExternalLibrary> loadedLibraries = new ConcurrentHashMap<>();
    private final Map<String, String> classNameToLibrary = new ConcurrentHashMap<>();
    private String globalLibsDir = "vast_libs";

    private VastLibraryRegistry() {}

    public static VastLibraryRegistry getInstance() {
        return INSTANCE;
    }

    public void setGlobalLibsDir(String dir) {
        this.globalLibsDir = dir;
    }

    public String getGlobalLibsDir() {
        return globalLibsDir;
    }

    public RegistryToken registerLibrary(String libraryId, Class<? extends VastExternalLibrary> libraryClass) {
        RegistryToken token = new RegistryToken(libraryId, libraryClass, true, 0);
        registeredLibraries.put(libraryId, token);
        System.out.printf("[Info] Library registered: %s%n", libraryId);
        return token;
    }

    public RegistryToken registerLibraryInstance(String libraryId, VastExternalLibrary instance) {
        if (libraryId == null || instance == null) {
            throw new IllegalArgumentException("Library ID and instance cannot be null");
        }

        if (registeredLibraries.containsKey(libraryId) || loadedLibraries.containsKey(libraryId)) {
            throw new IllegalStateException("Library ID already registered: " + libraryId);
        }

        RegistryToken token = new RegistryToken(libraryId, instance.getClass(), true, 0);
        registeredLibraries.put(libraryId, token);
        loadedLibraries.put(libraryId, instance);
        registerProvidedClasses(libraryId, instance);
        System.out.printf("[Info] Library instance registered: %s%n", libraryId);
        return token;
    }

    public boolean loadLibrary(String libraryId, VastVM vm) {
        RegistryToken token = registeredLibraries.get(libraryId);
        if (token == null) {
            System.err.printf("[Error] Library not found: %s%n", libraryId);
            return false;
        }

        if (!token.isEnabled()) {
            System.err.printf("[Error] Library is disabled: %s%n", libraryId);
            return false;
        }

        if (loadedLibraries.containsKey(libraryId)) {
            return true;
        }

        try {
            VastExternalLibrary library = (VastExternalLibrary) token.getLibraryClass()
                    .getDeclaredConstructor().newInstance();
            library.initialize(vm, this);
            registerProvidedClasses(libraryId, library);
            loadedLibraries.put(libraryId, library);
            System.out.printf("[Info] Library loaded: %s%n", libraryId);
            return true;
        } catch (Exception e) {
            System.err.printf("[Error] Failed to load library %s: %s%n", libraryId, e.getMessage());
            return false;
        }
    }

    private void registerProvidedClasses(String libraryId, VastExternalLibrary library) {
        Map<String, Class<?>> providedClasses = library.getProvidedClasses();
        for (Map.Entry<String, Class<?>> entry : providedClasses.entrySet()) {
            String className = entry.getKey();
            String existingLibrary = classNameToLibrary.get(className);
            if (existingLibrary != null) {
                System.err.printf("[Warning] Class name conflict: %s (from %s) conflicts with %s%n",
                        className, libraryId, existingLibrary);
                continue;
            }
            classNameToLibrary.put(className, libraryId);
        }
    }

    public VastExternalLibrary getLoadedLibrary(String libraryId) {
        return loadedLibraries.get(libraryId);
    }

    public boolean isLibraryLoaded(String libraryId) {
        return loadedLibraries.containsKey(libraryId);
    }

    public void unloadLibrary(String libraryId) {
        VastExternalLibrary library = loadedLibraries.remove(libraryId);
        if (library != null) {
            library.cleanup();
            classNameToLibrary.entrySet().removeIf(entry -> entry.getValue().equals(libraryId));
            System.out.printf("[Info] Library unloaded: %s%n", libraryId);
        }
    }

    public String findLibraryByClassName(String className) {
        return classNameToLibrary.get(className);
    }

    public Set<String> getRegisteredLibraryIds() {
        return Collections.unmodifiableSet(registeredLibraries.keySet());
    }

    public Set<String> getLoadedLibraryIds() {
        return Collections.unmodifiableSet(loadedLibraries.keySet());
    }

    public void cleanup() {
        System.out.printf("[Info] Cleaning up %d libraries...%n", loadedLibraries.size());
        loadedLibraries.values().forEach(VastExternalLibrary::cleanup);
        loadedLibraries.clear();
        classNameToLibrary.clear();
    }

    public String getLibraryInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Library Registry Status:\n");
        sb.append("Registered Libraries: ").append(registeredLibraries.size()).append("\n");
        sb.append("Loaded Libraries: ").append(loadedLibraries.size()).append("\n");
        sb.append("Registered Classes: ").append(classNameToLibrary.size()).append("\n");
        sb.append("Global Libs Directory: ").append(globalLibsDir).append("\n");

        if (!registeredLibraries.isEmpty()) {
            sb.append("\nAvailable Libraries:\n");
            registeredLibraries.values().forEach(token ->
                    sb.append("  - ").append(token.getLibraryId())
                            .append(" (").append(token.isEnabled() ? "enabled" : "disabled")
                            .append(", priority=").append(token.getPriority()).append(")\n"));
        }

        return sb.toString();
    }
}