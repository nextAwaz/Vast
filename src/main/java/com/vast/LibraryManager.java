package com.vast;

import com.vast.vm.VastVM;
import com.vast.vm.VastLibraryLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 库管理器 - 处理库的创建、列表、信息查询等操作
 */
public class LibraryManager {
    private final VastVM vm;
    private final VastLibraryLoader loader;

    public LibraryManager(VastVM vm) {
        this.vm = vm;
        this.loader = VastLibraryLoader.getInstance();
    }

    /**
     * 创建新的库模板
     */
    public void createLibrary(String libName) {
        String targetDir = "./" + libName;

        try {
            // 创建目录结构
            Files.createDirectories(Paths.get(targetDir));
            Files.createDirectories(Paths.get(targetDir, "src"));
            Files.createDirectories(Paths.get(targetDir, "custom"));

            // 创建 library.properties
            String props = String.format(
                    "name=%s\n" +
                            "version=1.0.0\n" +
                            "description=A custom Vast library\n" +
                            "author=Your Name\n" +
                            "advanced_features=false\n" +
                            "dependencies=\n" +
                            "config.example=value\n",
                    libName
            );

            Files.write(Paths.get(targetDir, "library.properties"), props.getBytes());

            // 创建示例 Java 文件
            String javaCode = String.format(
                    "// %s Library\n" +
                            "public class %s {\n\n" +
                            "    // Add your static methods here\n" +
                            "    public static void exampleMethod() {\n" +
                            "        System.out.println(\"Hello from %s library!\");\n" +
                            "    }\n\n" +
                            "    public static int add(int a, int b) {\n" +
                            "        return a + b;\n" +
                            "    }\n\n" +
                            "    public static double multiply(double a, double b) {\n" +
                            "        return a * b;\n" +
                            "    }\n" +
                            "}\n",
                    libName, capitalize(libName), libName
            );

            Files.write(Paths.get(targetDir, "src", capitalize(libName) + ".java"),
                    javaCode.getBytes());

            // 创建高级特性示例文件
            createAdvancedExamples(targetDir, libName);

            // 创建 README
            String readme = String.format(
                    "# %s Library\n\n" +
                            "A custom library for Vast scripting language.\n\n" +
                            "## Usage\n\n" +
                            "```vast\n" +
                            "imp %s\n" +
                            "%s.exampleMethod()\n" +
                            "result = %s.add(5, 3)\n" +
                            "```\n\n" +
                            "## Building\n\n" +
                            "1. Compile the Java source: `javac src/*.java`\n" +
                            "2. Package as .jar file: `jar cf %s.jar -C src .`\n" +
                            "3. Place in current directory or vast_libs directory\n",
                    libName, libName, capitalize(libName), capitalize(libName), libName
            );

            Files.write(Paths.get(targetDir, "README.md"), readme.getBytes());

            System.out.println("@ Library template created: " + targetDir);
            System.out.println("@ Next steps:");
            System.out.println("  1. Edit library.properties");
            System.out.println("  2. Implement your library methods in src/");
            System.out.println("  3. For advanced features, edit custom/ directory");
            System.out.println("  4. Compile: javac src/*.java");
            System.out.println("  5. Package: jar cf " + libName + ".jar -C src .");
            System.out.println("  6. Use with: imp " + libName);

        } catch (Exception e) {
            System.err.println("Failed to create library template: " + e.getMessage());
        }
    }

    /**
     * 创建高级特性示例
     */
    private void createAdvancedExamples(String targetDir, String libName) throws IOException {
        // 创建幂运算自定义规则示例
        String powerCo =
                "--name = MyPower\n" +
                        "--id = power\n" +
                        "--keyword = operator{0}, numA{1}, numB{2}\n" +
                        "## This is a comment - demonstrates custom power operator\n" +
                        "## Usage: a ** b\n" +
                        "\n" +
                        "{1}{0}{2}\n";

        Files.write(Paths.get(targetDir, "custom", "power.co"), powerCo.getBytes());

        // 创建条件语句自定义规则示例
        String ifElseCo =
                "--name = MyIfElifElse\n" +
                        "--id = ifElifElse\n" +
                        "--keyword = operIf{0}, codesIf{1}, codesElif{2}, codesElse{3}, operElif{4}, operElse{5}\n" +
                        "--overlook = \" \"\n" +
                        "## Custom if-elif-else syntax\n" +
                        "## Note: --overlook = \" \" allows flexible spacing\n" +
                        "\n" +
                        "{0}\n" +
                        "    {1}\n" +
                        "{4}\n" +
                        "    {2}\n" +
                        "{5}\n" +
                        "    {3}\n";

        Files.write(Paths.get(targetDir, "custom", "if_elif_else.co"), ifElseCo.getBytes());

        // 创建启用高级特性的说明文件
        String advancedReadme =
                "# Advanced Features\n\n" +
                        "To enable advanced features:\n\n" +
                        "1. Edit library.properties\n" +
                        "2. Set `advanced_features = true`\n" +
                        "3. Add custom syntax rules in custom/ directory\n" +
                        "4. Rules use .co files with specific format\n\n" +
                        "## Rule File Format\n\n" +
                        "```\n" +
                        "--name = RuleName        # Rule identifier\n" +
                        "--id = ruleId           # Unique ID\n" +
                        "--keyword = name{index}  # Token definitions\n" +
                        "--overlook = \" \"        # Optional: ignore these chars\n" +
                        "pattern                 # The syntax pattern\n" +
                        "```\n\n" +
                        "See the examples in this directory.\n";

        Files.write(Paths.get(targetDir, "custom", "README.md"), advancedReadme.getBytes());
    }

    /**
     * 列出可用库
     */
    public void listLibraries() {
        Set<String> libraries = loader.getLoadedLibraries().keySet();

        System.out.println("@ Available Libraries:");
        if (libraries.isEmpty()) {
            System.out.println("  No libraries loaded");
        } else {
            libraries.forEach(lib -> System.out.println("  - " + lib));
        }

        // 显示高级特性状态
        if (vm.hasAdvancedFeatures()) {
            System.out.println("\n@ Advanced Features: ENABLED");
            System.out.println("  Custom rules: " +
                    vm.getCustomSyntaxManager().getAllRules().size());
        } else {
            System.out.println("\n@ Advanced Features: disabled");
        }
    }

    /**
     * 显示库信息
     */
    public void showLibraryInfo(String libName) {
        if (!loader.getLoadedLibraries().containsKey(libName)) {
            System.out.println("Library not loaded: " + libName);
            return;
        }

        Class<?> libClass = loader.getLoadedLibraries().get(libName);

        System.out.println("@ Library Info: " + libName);
        System.out.println("  Class: " + libClass.getName());
        System.out.println("  Methods: " + loader.getStaticMethods(libName).size());

        // 显示方法列表
        Set<String> methods = loader.getStaticMethods(libName);
        if (!methods.isEmpty()) {
            System.out.println("  Available methods:");
            methods.forEach(method -> System.out.println("    - " + method + "()"));
        }

        // 显示高级特性信息
        if (vm.hasAdvancedFeatures()) {
            System.out.println("  Advanced Features: ENABLED");
            System.out.println("  Custom rules: " +
                    vm.getCustomSyntaxManager().getAllRules().size());
        }
    }

    /**
     * 显示加载器状态信息
     */
    public void showLoaderInfo() {
        System.out.println(loader.getLoaderInfo());

        if (vm.hasAdvancedFeatures()) {
            System.out.println("\n" + vm.getCustomSyntaxManager().getManagerInfo());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}