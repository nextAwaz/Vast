package com.volcano.registry;

import com.volcano.vm.VolcanoVM;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外置库注册表 - 管理所有外置库的注册和生命周期
 */
public class LibraryRegistry {
    private static final LibraryRegistry INSTANCE = new LibraryRegistry();

    // 内置库注册表
    private final Map<String, RegistryToken> builtinLibraries = new ConcurrentHashMap<>();
    // 已加载的外置库实例
    private final Map<String, VolcanoExternalLibrary> loadedLibraries = new ConcurrentHashMap<>();
    // 类名到库名的映射（用于解决冲突）
    private final Map<String, String> classNameToLibrary = new ConcurrentHashMap<>();
    // 自定义关键字和语句处理器
    private final Map<String, KeywordHandler> keywordHandlers = new ConcurrentHashMap<>();
    private final Map<String, StatementHandler> statementHandlers = new ConcurrentHashMap<>();

    private LibraryRegistry() {
        // 私有构造函数
        initializeDefaultLibraries();
    }

    public static LibraryRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化默认库
     */
    private void initializeDefaultLibraries() {
        // 可以在这里注册一些默认的内置库
        // registerBuiltinLibrary("Standard", StandardLibrary.class);
    }

    /**
     * 注册内置库
     */
    public RegistryToken registerBuiltinLibrary(String name, Class<? extends VolcanoExternalLibrary> libraryClass) {
        RegistryToken token = new RegistryToken(name, libraryClass, true, 0);
        builtinLibraries.put(name, token);
        System.out.printf("[Info] Builtin library registered: %s%n", name);
        return token;
    }

    /**
     * 注册库实例（而不是通过反射创建）
     */
    public RegistryToken registerLibraryInstance(String name, VolcanoExternalLibrary instance) {
        if (name == null || instance == null) {
            throw new IllegalArgumentException("Library name and instance cannot be null");
        }

        // 检查名称冲突
        if (builtinLibraries.containsKey(name) || loadedLibraries.containsKey(name)) {
            throw new IllegalStateException("Library name already registered: " + name);
        }

        RegistryToken token = new RegistryToken(name, instance.getClass(), true, 0);

        // 直接放入已加载库映射
        loadedLibraries.put(name, instance);

        // 注册提供的类
        Map<String, Class<?>> providedClasses = instance.getProvidedClasses();
        for (Map.Entry<String, Class<?>> entry : providedClasses.entrySet()) {
            String className = entry.getKey();
            String existingLibrary = classNameToLibrary.get(className);
            if (existingLibrary != null) {
                System.err.printf("[Warning] Class name conflict: %s (from %s) conflicts with %s%n",
                        className, name, existingLibrary);
                continue;
            }
            classNameToLibrary.put(className, name);
        }

        // 注册自定义处理器
        keywordHandlers.putAll(instance.getKeywordHandlers());
        statementHandlers.putAll(instance.getStatementHandlers());

        System.out.printf("[Info] Library instance registered: %s%n", name);
        return token;
    }

    /**
     * 从外置库注册自身
     */
    public RegistryToken registerFromLibrary(String name, Class<? extends VolcanoExternalLibrary> libraryClass,
                                             VolcanoExternalLibrary instance) {
        RegistryToken token = new RegistryToken(name, libraryClass, true, 1);

        // 检查名称冲突
        if (builtinLibraries.containsKey(name) || loadedLibraries.containsKey(name)) {
            throw new IllegalStateException("Library name already registered: " + name);
        }

        loadedLibraries.put(name, instance);
        return token;
    }

    /**
     * 获取已加载的库实例
     */
    public VolcanoExternalLibrary getLoadedLibrary(String libraryName) {
        return loadedLibraries.get(libraryName);
    }

    /**
     * 获取所有已加载的库
     */
    public Map<String, VolcanoExternalLibrary> getLoadedLibraries() {
        return Collections.unmodifiableMap(loadedLibraries);
    }

    /**
     * 加载并初始化库
     */
    public void loadLibrary(String libraryName, VolcanoVM vm) {
        RegistryToken token = builtinLibraries.get(libraryName);
        if (token == null) {
            throw new IllegalArgumentException("Library not found: " + libraryName);
        }

        if (!token.isEnabled()) {
            throw new IllegalStateException("Library is disabled: " + libraryName);
        }

        // 检查是否已经加载
        if (loadedLibraries.containsKey(libraryName)) {
            return; // 已经加载过了
        }

        try {
            VolcanoExternalLibrary library = (VolcanoExternalLibrary) token.getLibraryClass()
                    .getDeclaredConstructor().newInstance();

            // 初始化库
            library.initialize(vm, this);

            // 注册提供的类
            Map<String, Class<?>> providedClasses = library.getProvidedClasses();
            for (Map.Entry<String, Class<?>> entry : providedClasses.entrySet()) {
                String className = entry.getKey();
                String existingLibrary = classNameToLibrary.get(className);
                if (existingLibrary != null) {
                    System.err.printf("[Warning] Class name conflict: %s (from %s) conflicts with %s%n",
                            className, libraryName, existingLibrary);
                    // 不覆盖，保持第一个注册的
                    continue;
                }
                classNameToLibrary.put(className, libraryName);

                // 修复：使用 vm.getImportedClasses() 方法
                Map<String, Class<?>> importedClasses = vm.getImportedClasses();
                if (importedClasses != null) {
                    importedClasses.put(className, entry.getValue());
                } else {
                    System.err.printf("[Warning] Cannot import class %s: importedClasses map is null%n", className);
                }
            }

            // 注册自定义处理器
            keywordHandlers.putAll(library.getKeywordHandlers());
            statementHandlers.putAll(library.getStatementHandlers());

            loadedLibraries.put(libraryName, library);
            System.out.printf("[Info] Library loaded: %s%n", libraryName);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load library: " + libraryName, e);
        }
    }

    /**
     * 按名称加载库
     */
    public boolean loadLibraryByName(String libraryName, VolcanoVM vm) {
        try {
            loadLibrary(libraryName, vm);
            return true;
        } catch (Exception e) {
            System.err.printf("[Error] Failed to load library %s: %s%n", libraryName, e.getMessage());
            return false;
        }
    }

    /**
     * 加载所有启用的内置库
     */
    public void loadAllLibraries(VolcanoVM vm) {
        System.out.printf("[Info] Loading all libraries... (%d available)%n", builtinLibraries.size());

        builtinLibraries.values().stream()
                .filter(RegistryToken::isEnabled)
                .sorted(Comparator.comparingInt(RegistryToken::getPriority))
                .forEach(token -> {
                    try {
                        loadLibrary(token.getLibraryName(), vm);
                    } catch (Exception e) {
                        System.err.printf("[Error] Failed to load library %s: %s%n",
                                token.getLibraryName(), e.getMessage());
                    }
                });

        System.out.printf("[Info] Libraries loaded: %d/%d%n",
                loadedLibraries.size(), builtinLibraries.size());
    }

    /**
     * 获取关键字处理器
     */
    public KeywordHandler getKeywordHandler(String keyword) {
        return keywordHandlers.get(keyword);
    }

    /**
     * 获取语句处理器
     */
    public StatementHandler getStatementHandler(String statementPrefix) {
        return statementHandlers.get(statementPrefix);
    }

    /**
     * 获取所有语句处理器
     */
    public Map<String, StatementHandler> getStatementHandlers() {
        return Collections.unmodifiableMap(statementHandlers);
    }

    /**
     * 获取所有关键字处理器
     */
    public Map<String, KeywordHandler> getKeywordHandlers() {
        return Collections.unmodifiableMap(keywordHandlers);
    }

    /**
     * 获取语句处理器映射（返回可修改的视图，仅供内部使用）
     */
    Map<String, StatementHandler> getStatementHandlersInternal() {
        return statementHandlers;
    }

    /**
     * 获取关键字处理器映射（返回可修改的视图，仅供内部使用）
     */
    Map<String, KeywordHandler> getKeywordHandlersInternal() {
        return keywordHandlers;
    }

    /**
     * 获取所有已注册的内置库名称
     */
    public Set<String> getBuiltinLibraryNames() {
        return Collections.unmodifiableSet(builtinLibraries.keySet());
    }

    /**
     * 获取所有已加载的库名称
     */
    public Set<String> getLoadedLibraryNames() {
        return Collections.unmodifiableSet(loadedLibraries.keySet());
    }

    /**
     * 检查库是否已加载
     */
    public boolean isLibraryLoaded(String libraryName) {
        return loadedLibraries.containsKey(libraryName);
    }

    /**
     * 卸载指定库
     */
    public void unloadLibrary(String libraryName) {
        VolcanoExternalLibrary library = loadedLibraries.remove(libraryName);
        if (library != null) {
            library.cleanup();

            // 移除相关的类和处理器
            classNameToLibrary.entrySet().removeIf(entry -> entry.getValue().equals(libraryName));

            // 这里简化处理：清除所有关键字和语句处理器
            // 实际实现中可能需要更精细的管理
            keywordHandlers.clear();
            statementHandlers.clear();

            System.out.printf("[Info] Library unloaded: %s%n", libraryName);
        }
    }

    /**
     * 清理所有加载的库
     */
    public void cleanup() {
        System.out.printf("[Info] Cleaning up %d libraries...%n", loadedLibraries.size());

        loadedLibraries.values().forEach(VolcanoExternalLibrary::cleanup);
        loadedLibraries.clear();
        keywordHandlers.clear();
        statementHandlers.clear();
        classNameToLibrary.clear();
    }

    /**
     * 根据类名查找所属库名
     */
    public String findLibraryByClassName(String className) {
        return classNameToLibrary.get(className);
    }

    /**
     * 获取库信息
     */
    public String getLibraryInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Library Registry Status:\n");
        sb.append("Builtin Libraries: ").append(builtinLibraries.size()).append("\n");
        sb.append("Loaded Libraries: ").append(loadedLibraries.size()).append("\n");
        sb.append("Registered Classes: ").append(classNameToLibrary.size()).append("\n");
        sb.append("Keyword Handlers: ").append(keywordHandlers.size()).append("\n");
        sb.append("Statement Handlers: ").append(statementHandlers.size()).append("\n");

        if (!builtinLibraries.isEmpty()) {
            sb.append("\nAvailable Libraries:\n");
            builtinLibraries.values().forEach(token ->
                    sb.append("  - ").append(token.getLibraryName())
                            .append(" (").append(token.isEnabled() ? "enabled" : "disabled")
                            .append(", priority=").append(token.getPriority()).append(")\n"));
        }

        return sb.toString();
    }
}