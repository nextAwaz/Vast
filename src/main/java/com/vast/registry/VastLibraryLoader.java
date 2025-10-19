package com.vast.registry;

import com.vast.vm.VastVM;
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
 * 外置库加载器 - 负责从文件系统加载外置库
 */
public class VastLibraryLoader {
    private static final VastLibraryLoader INSTANCE = new VastLibraryLoader();
    private final VastLibraryRegistry registry = VastLibraryRegistry.getInstance();

    private VastLibraryLoader() {}

    public static VastLibraryLoader getInstance() {
        return INSTANCE;
    }

    /**
     * 根据导入语句加载库
     */
    public boolean loadLibraryFromImport(String importPath, VastVM vm) {
        try {
            // 解析导入路径（支持注释）
            String cleanPath = importPath.split("//")[0].trim();

            // 直接跳过 VM 自身的类和内置类
            if (isVastVMClass(cleanPath)) {
                return false; // 静默跳过，不报错
            }

            // 查找库文件
            File libraryFile = findLibraryFile(cleanPath);
            if (libraryFile == null) {
                return false; // 让后续的类加载器处理
            }

            // 再次检查是否是 VM 自身的 JAR
            if (isVastVMJar(libraryFile)) {
                return false; // 静默跳过，不报错
            }

            return loadLibraryFromFile(libraryFile, vm);

        } catch (Exception e) {
            // 静默处理异常，不打印错误信息
            return false;
        }
    }

    /**
     * 检查是否是 VM 自身的类
     */
    private boolean isVastVMClass(String className) {
        // VM 内置类
        Set<String> builtinClasses = Set.of(
                "Sys", "Time", "Array", "Ops", "DataType",
                "EncodingUtil", "EnhancedInput", "VastVM"
        );

        if (builtinClasses.contains(className)) {
            return true;
        }

        // VM 包下的类
        return className.startsWith("com.vast.");
    }

    /**
     * 检查是否是 VM 自身的 JAR 文件
     */
    private boolean isVastVMJar(File jarFile) {
        String jarName = jarFile.getName().toLowerCase();
        return jarName.contains("vast") &&
                (jarName.contains("vm") || jarName.contains("vast-vm"));
    }

    /**
     * 查找库文件
     */
    private File findLibraryFile(String libraryPath) {
        // 替换点号为路径分隔符
        String path = libraryPath.replace('.', File.separatorChar);

        // 1. 首先在当前目录查找
        File currentDirLib = new File(path + ".jar");
        if (currentDirLib.exists() && !isVastVMJar(currentDirLib)) {
            return currentDirLib;
        }

        currentDirLib = new File(path + ".zip");
        if (currentDirLib.exists() && !isVastVMJar(currentDirLib)) {
            return currentDirLib;
        }

        // 2. 在全局库目录查找
        File globalLib = new File(registry.getGlobalLibsDir(), path + ".jar");
        if (globalLib.exists() && !isVastVMJar(globalLib)) {
            return globalLib;
        }

        globalLib = new File(registry.getGlobalLibsDir(), path + ".zip");
        if (globalLib.exists() && !isVastVMJar(globalLib)) {
            return globalLib;
        }

        return null;
    }

    /**
     * 从文件加载库
     */
    private boolean loadLibraryFromFile(File libraryFile, VastVM vm) {
        try {
            // 创建临时目录来解压库文件
            Path tempDir = Files.createTempDirectory("vast_lib_" + System.currentTimeMillis());

            try {
                // 解压库文件
                extractLibrary(libraryFile, tempDir);

                // 查找库描述文件
                LibraryMetadata metadata = findLibraryMetadata(tempDir);
                if (metadata == null) {
                    return false; // 静默失败
                }

                // 加载库类
                VastExternalLibrary library = loadLibraryClass(tempDir, metadata);
                if (library == null) {
                    return false; // 静默失败
                }

                // 注册库实例
                registry.registerLibraryInstance(metadata.getName(), library);

                // 初始化库
                return registry.loadLibrary(metadata.getName(), vm);

            } finally {
                // 清理临时目录
                deleteDirectory(tempDir);
            }

        } catch (Exception e) {
            // 静默处理异常
            return false;
        }
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
     * 查找库元数据
     */
    private LibraryMetadata findLibraryMetadata(Path libDir) throws IOException {
        // 查找库描述文件
        Path metaFile = libDir.resolve("library.properties");
        if (!Files.exists(metaFile)) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(metaFile)) {
            props.load(is);
        }

        return new LibraryMetadata(
                props.getProperty("name", "Unknown"),
                props.getProperty("version", "1.0.0"),
                props.getProperty("description", ""),
                props.getProperty("author", "Unknown"),
                parseDependencies(props.getProperty("dependencies", "")),
                parseConfiguration(props)
        );
    }

    private List<String> parseDependencies(String deps) {
        if (deps == null || deps.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.asList(deps.split("\\s*,\\s*"));
    }

    private Map<String, String> parseConfiguration(Properties props) {
        Map<String, String> config = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("config.")) {
                config.put(key.substring(7), props.getProperty(key));
            }
        }
        return config;
    }

    /**
     * 加载库类
     */
    private VastExternalLibrary loadLibraryClass(Path libDir, LibraryMetadata metadata) {
        try {
            // 创建类加载器
            URL[] urls = { libDir.toUri().toURL() };
            URLClassLoader classLoader = new URLClassLoader(urls);

            // 加载主类
            String mainClass = metadata.getConfiguration().get("mainClass");
            if (mainClass == null) {
                return null;
            }

            Class<?> clazz = classLoader.loadClass(mainClass);
            if (!VastExternalLibrary.class.isAssignableFrom(clazz)) {
                return null;
            }

            return (VastExternalLibrary) clazz.getDeclaredConstructor().newInstance();

        } catch (Exception e) {
            return null;
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
     * 扫描并加载所有可用的库
     */
    public void scanAndLoadAvailableLibraries(VastVM vm) {
        // 扫描全局库目录
        scanLibraryDirectory(new File(registry.getGlobalLibsDir()), vm);

        // 扫描当前目录
        scanLibraryDirectory(new File("."), vm);
    }

    private void scanLibraryDirectory(File dir, VastVM vm) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".zip"));

        if (files != null) {
            for (File file : files) {
                // 跳过 VM 自身的 JAR 文件
                if (isVastVMJar(file)) {
                    continue;
                }

                try {
                    loadLibraryFromFile(file, vm);
                } catch (Exception e) {
                    // 静默处理异常
                }
            }
        }
    }
}