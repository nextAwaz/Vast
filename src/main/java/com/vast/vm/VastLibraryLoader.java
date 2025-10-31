package com.vast.vm;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 简化的外置库加载器
 * 支持直接加载包含静态方法的 Java 类
 */
public class VastLibraryLoader {
    private static final VastLibraryLoader INSTANCE = new VastLibraryLoader();

    // 已加载的库类缓存
    private final Map<String, Class<?>> loadedLibraries = new HashMap<>();
    private final Map<String, Set<String>> staticMethods = new HashMap<>();
    private final Map<String, String> methodToClass = new HashMap<>();
    private final Map<String, Set<String>> methodConflicts = new HashMap<>();
    private final CustomSyntaxManager customSyntaxManager = new CustomSyntaxManager(null);

    private VastLibraryLoader() {}

    public static VastLibraryLoader getInstance() {
        return INSTANCE;
    }

    /**
     * 根据导入语句加载库
     */
    public boolean loadLibraryFromImport(String importPath, VastVM vm) {
        try {
            // 清理导入路径（移除注释等）
            String cleanPath = importPath.split("//")[0].trim();

            // 跳过 VM 自身类和内置类
            if (isVastVMClass(cleanPath) || VastVM.getBuiltinClasses().containsKey(cleanPath)) {
                return false;
            }

            // 如果已经加载过，直接返回成功
            if (loadedLibraries.containsKey(cleanPath)) {
                vm.getDebugger().debug("Library already loaded: " + cleanPath);
                return true;
            }

            // 查找库文件
            File libraryFile = findLibraryFile(cleanPath);
            if (libraryFile == null) {
                // 尝试作为普通 Java 类加载
                return loadAsJavaClass(cleanPath, vm);
            }

            // 加载库文件
            return loadLibraryFromFile(libraryFile, cleanPath, vm);

        } catch (Exception e) {
            vm.getDebugger().debug("Failed to load library: " + importPath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 作为普通 Java 类加载
     */
    private boolean loadAsJavaClass(String className, VastVM vm) {
        try {
            Class<?> clazz = Class.forName(className);
            registerLibraryClass(className, clazz, vm);
            vm.getDebugger().debug("Loaded as Java class: " + className);
            return true;
        } catch (ClassNotFoundException e) {
            vm.getDebugger().debug("Class not found: " + className);
            return false;
        }
    }

    /**
     * 查找库文件
     */
    private File findLibraryFile(String libraryPath) {
        // 1. 当前目录查找
        File currentDirJar = new File(libraryPath + ".jar");
        if (currentDirJar.exists() && !isVastVMJar(currentDirJar)) {
            return currentDirJar;
        }

        File currentDirZip = new File(libraryPath + ".zip");
        if (currentDirZip.exists() && !isVastVMJar(currentDirZip)) {
            return currentDirZip;
        }

        // 2. vast_libs 目录查找
        File libsDir = new File("vast_libs");
        if (libsDir.exists() && libsDir.isDirectory()) {
            File libJar = new File(libsDir, libraryPath + ".jar");
            if (libJar.exists() && !isVastVMJar(libJar)) {
                return libJar;
            }

            File libZip = new File(libsDir, libraryPath + ".zip");
            if (libZip.exists() && !isVastVMJar(libZip)) {
                return libZip;
            }
        }

        return null;
    }

    /**
     * 获取已加载的库
     */
    public Map<String, Class<?>> getLoadedLibraries() {
        return Collections.unmodifiableMap(loadedLibraries);
    }

    /**
     * 获取库的静态方法
     */
    public Set<String> getStaticMethods(String className) {
        return staticMethods.getOrDefault(className, Collections.emptySet());
    }

    /**
     * 获取方法冲突信息
     */
    public Map<String, Set<String>> getMethodConflicts() {
        return Collections.unmodifiableMap(methodConflicts);
    }

    /**
     * 从文件加载库
     */
    boolean loadLibraryFromFile(File libraryFile, String libraryName, VastVM vm) {
        try {
            // 创建临时目录
            Path tempDir = Files.createTempDirectory("vast_lib_" + System.currentTimeMillis());

            try {
                // 解压文件
                extractLibrary(libraryFile, tempDir);

                // 查找并加载类文件
                List<Class<?>> classes = findAndLoadClasses(tempDir, libraryName, vm);

                if (classes.isEmpty()) {
                    vm.getDebugger().debug("No classes found in library: " + libraryName);
                    return false;
                }

                // 注册所有类
                for (Class<?> clazz : classes) {
                    registerLibraryClass(clazz.getSimpleName(), clazz, vm);
                }

                // 加载自定义语法规则（针对高级库）
                loadCustomRules(tempDir, libraryName, vm);

                vm.getDebugger().debug("Loaded library from file: " + libraryName + " with " + classes.size() + " classes");
                return true;

            } finally {
                // 清理临时目录
                deleteDirectory(tempDir);
            }

        } catch (Exception e) {
            vm.getDebugger().debug("Failed to load library from file: " + libraryName + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 扫描并加载可用库
     */
    public void scanAndLoadAvailableLibraries(VastVM vm) {
        // 扫描当前目录
        scanDirectoryForLibraries(new File("."), vm);

        // 扫描 vast_libs 目录
        File libsDir = new File("vast_libs");
        if (libsDir.exists() && libsDir.isDirectory()) {
            scanDirectoryForLibraries(libsDir, vm);
        }

        vm.getDebugger().debug("Auto-loaded libraries: " + loadedLibraries.size());
    }

    /**
     * 扫描目录中的库文件
     */
    private void scanDirectoryForLibraries(File dir, VastVM vm) {
        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".zip"));

        if (files != null) {
            for (File file : files) {
                if (isVastVMJar(file)) {
                    continue; // 跳过 VM 自身的 JAR
                }

                try {
                    // 从文件名推断库名（移除扩展名）
                    String libraryName = file.getName().replaceFirst("[.][^.]+$", "");
                    loadLibraryFromFile(file, libraryName, vm);
                } catch (Exception e) {
                    vm.getDebugger().debug("Failed to auto-load library: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * 查找并加载类
     */
    private List<Class<?>> findAndLoadClasses(Path libDir, String libraryName, VastVM vm) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            // 创建类加载器
            URLClassLoader classLoader = new URLClassLoader(new URL[]{libDir.toUri().toURL()});

            // 查找 .class 文件
            Files.walk(libDir)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(classFile -> {
                        try {
                            // 转换为类名
                            String className = convertPathToClassName(libDir, classFile);
                            Class<?> clazz = classLoader.loadClass(className);
                            classes.add(clazz);
                        } catch (Exception e) {
                            vm.getDebugger().debug("Failed to load class from: " + classFile + " - " + e.getMessage());
                        }
                    });

            classLoader.close();
        } catch (Exception e) {
            vm.getDebugger().debug("Error finding classes: " + e.getMessage());
        }
        return classes;
    }

    /**
     * 将文件路径转换为类名
     */
    private String convertPathToClassName(Path baseDir, Path classFile) {
        // 获取相对于基目录的路径
        Path relativePath = baseDir.relativize(classFile);
        String pathStr = relativePath.toString();

        // 移除 .class 扩展名，将路径分隔符替换为 .
        return pathStr.substring(0, pathStr.length() - 6)
                .replace(File.separatorChar, '.');
    }

    /**
     * 注册库类
     */
    private void registerLibraryClass(String className, Class<?> clazz, VastVM vm) {
        // 添加到已加载库
        loadedLibraries.put(className, clazz);

        // 添加到 VM 的导入类
        vm.getImportedClasses().put(className, clazz);

        // 收集静态方法
        collectStaticMethods(className, clazz, vm);

        vm.getDebugger().debug("Registered library class: " + className);
    }

    /**
     * 收集静态方法
     */
    private void collectStaticMethods(String className, Class<?> clazz, VastVM vm) {
        Set<String> methods = new HashSet<>();

        try {
            for (java.lang.reflect.Method method : clazz.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    String methodName = method.getName();
                    methods.add(methodName);

                    // 检查方法名冲突
                    if (methodToClass.containsKey(methodName)) {
                        // 记录冲突
                        methodConflicts.computeIfAbsent(methodName, k -> new HashSet<>())
                                .add(methodToClass.get(methodName));
                        methodConflicts.get(methodName).add(className);
                        vm.getDebugger().warning("Method name conflict: '" + methodName +
                                "' exists in multiple classes: " + methodConflicts.get(methodName));
                    } else {
                        // 唯一方法名，添加到映射
                        methodToClass.put(methodName, className);
                        vm.getDebugger().debug("Mapped static method: " + methodName + " -> " + className);
                    }
                }
            }

            staticMethods.put(className, methods);

        } catch (Exception e) {
            vm.getDebugger().debug("Failed to collect methods from: " + className + " - " + e.getMessage());
        }
    }

    /**
     * 解析方法名对应的类名
     */
    public String resolveClassNameForMethod(String methodName, VastVM vm) {
        if (methodToClass.containsKey(methodName)) {
            String className = methodToClass.get(methodName);
            vm.getDebugger().debug("Resolved method '" + methodName + "' to class: " + className);
            return className;
        }

        if (methodConflicts.containsKey(methodName)) {
            vm.getDebugger().warning("Ambiguous method reference: '" + methodName +
                    "' exists in multiple classes: " + methodConflicts.get(methodName));
            return null; // 冲突时返回 null，让调用方处理
        }

        return null; // 方法不存在
    }

    /**
     * 解压库文件
     */
    private void extractLibrary(File libraryFile, Path targetDir) throws IOException {
        String fileName = libraryFile.getName().toLowerCase();

        if (fileName.endsWith(".jar") || fileName.endsWith(".zip")) {
            try (ZipFile zipFile = new ZipFile(libraryFile)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path entryPath = targetDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    /**
     * 检查是否是 VM 自身的类
     */
    private boolean isVastVMClass(String className) {
        return className.startsWith("com.vast.");
    }

    /**
     * 检查是否是 VM 自身的 JAR 文件
     */
    boolean isVastVMJar(File jarFile) {
        String jarName = jarFile.getName().toLowerCase();
        return jarName.contains("vast") &&
                (jarName.contains("vm") || jarName.contains("vast-vm"));
    }

    /**
     * 获取加载状态信息
     */
    public String getLoaderInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Library Loader Status:\n");
        sb.append("Loaded Libraries: ").append(loadedLibraries.size()).append("\n");
        sb.append("Static Methods: ").append(methodToClass.size()).append("\n");
        sb.append("Method Conflicts: ").append(methodConflicts.size()).append("\n");

        if (!loadedLibraries.isEmpty()) {
            sb.append("\nLoaded Libraries:\n");
            loadedLibraries.keySet().forEach(lib ->
                    sb.append("  - ").append(lib).append("\n"));
        }

        if (!methodConflicts.isEmpty()) {
            sb.append("\nMethod Conflicts:\n");
            methodConflicts.forEach((method, classes) ->
                    sb.append("  - ").append(method).append(": ").append(classes).append("\n"));
        }

        return sb.toString();
    }

    /**
     * 检查库是否启用高级特性
     */
    private boolean isAdvancedLibrary(Path libDir, VastVM vm) {
        try {
            // 检查 library.properties 文件
            Path propFile = libDir.resolve("library.properties");
            if (Files.exists(propFile)) {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(propFile)) {
                    props.load(is);
                    String advanced = props.getProperty("advanced_features", "false");
                    return "true".equalsIgnoreCase(advanced.trim());
                }
            }
        } catch (Exception e) {
            vm.getDebugger().debug("Failed to check advanced features: " + e.getMessage());
        }
        return false;
    }

    /**
     * 加载自定义语法规则
     */
    private void loadCustomRules(Path libDir, String libraryName, VastVM vm) {
        if (!isAdvancedLibrary(libDir, vm)) {
            return;
        }

        vm.getDebugger().debug("Loading advanced features for library: " + libraryName);

        Path customDir = libDir.resolve("custom");
        if (!Files.exists(customDir) || !Files.isDirectory(customDir)) {
            vm.getDebugger().debug("No custom directory found for advanced library: " + libraryName);
            return;
        }

        try {
            // 查找所有 .co 文件
            Files.walk(customDir)
                    .filter(path -> path.toString().endsWith(".co"))
                    .forEach(coFile -> {
                        try {
                            CustomRule rule = parseCustomRule(coFile, vm);
                            if (rule != null) {
                                customSyntaxManager.addRule(rule);
                                vm.getDebugger().debug("Loaded custom rule: " + rule.getName());
                            }
                        } catch (Exception e) {
                            vm.getDebugger().debug("Failed to parse custom rule file: " + coFile + " - " + e.getMessage());
                        }
                    });

        } catch (Exception e) {
            vm.getDebugger().debug("Error loading custom rules: " + e.getMessage());
        }
    }

    /**
     * 解析自定义规则文件
     */
    private CustomRule parseCustomRule(Path coFile, VastVM vm) {
        try {
            List<String> lines = Files.readAllLines(coFile);
            String name = null;
            String id = null;
            List<String> keywords = new ArrayList<>();
            String pattern = null;
            boolean overlookSpaces = false;
            Set<Character> overlookChars = new HashSet<>();

            StringBuilder patternBuilder = new StringBuilder();
            boolean inPattern = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // 处理注释
                if (trimmed.startsWith("##")) {
                    // 转义井号，替换为单个井号
                    if (inPattern) {
                        patternBuilder.append(trimmed.substring(1)); // 移除一个井号
                    }
                    continue;
                } else if (trimmed.startsWith("#")) {
                    // 注释行，跳过
                    continue;
                }

                // 处理元数据
                if (trimmed.startsWith("--")) {
                    inPattern = false;
                    String[] parts = trimmed.substring(2).split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        switch (key) {
                            case "name":
                                name = value;
                                break;
                            case "id":
                                id = value;
                                break;
                            case "keyword":
                                // 分割关键字，支持逗号分隔
                                String[] keywordArray = value.split(",");
                                for (String keyword : keywordArray) {
                                    keywords.add(keyword.trim());
                                }
                                break;
                            case "overlook":
                                overlookSpaces = true;
                                // 解析要忽略的字符
                                for (char c : value.toCharArray()) {
                                    if (!Character.isWhitespace(c) && c != '"' && c != '\'') {
                                        overlookChars.add(c);
                                    }
                                }
                                break;
                        }
                    }
                } else if (!trimmed.isEmpty()) {
                    // 开始模式部分
                    inPattern = true;
                    patternBuilder.append(line).append("\n");
                }
            }

            // 构建最终模式
            if (patternBuilder.length() > 0) {
                pattern = patternBuilder.toString().trim();
            }

            // 验证必需字段
            if (name == null || id == null || keywords.isEmpty() || pattern == null) {
                vm.getDebugger().debug("Invalid custom rule: missing required fields in " + coFile);
                return null;
            }

            return new CustomRule(name, id, keywords, pattern, overlookSpaces, overlookChars);

        } catch (Exception e) {
            vm.getDebugger().debug("Failed to parse custom rule file: " + coFile + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取自定义语法管理器
     */
    public CustomSyntaxManager getCustomSyntaxManager() {
        return customSyntaxManager;
    }

    /**
     * 清理所有加载的库
     */
    public void cleanup() {
        loadedLibraries.clear();
        staticMethods.clear();
        methodToClass.clear();
        methodConflicts.clear();
    }
}