package com.vast;

import com.vast.Vast;
import com.vast.vm.VastVM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class VastCLI {
    static String ver = "0.1.2(hotfix-2)"; //版本信息

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "run":
                    handleRunCommand(args);
                    break;
                case "shell":
                    handleShellCommand();
                    break;
                case "lib":
                    handleLibCommand(args);
                    break;
                case "help":
                    handleHelpCommand(args);
                    break;
                case "version":
                    handleVersionCommand();
                    break;
                case "list":
                    handleListCommand();
                    break;
                case "info":
                    handleInfoCommand(args);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    printUsage();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (Arrays.asList(args).contains("--debug")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static void handleRunCommand(String[] args) {
        if (args.length < 2) {
            println("Usage: run <script.vast> [--debug]");
            return;
        }

        String scriptPath = args[1];
        boolean debugMode = false;

        // 检查--debug参数
        for (String arg : args) {
            if ("--debug".equals(arg)) {
                debugMode = true;
                break;
            }
        }

        long startTime = System.currentTimeMillis();
        try {
            println("@ Running Vast: " + scriptPath);
            if (debugMode) {
                println("@ Debug mode enabled - showing stack traces");
            }
            println("=".repeat(50));

            Vast.builder()
                    .debug(debugMode)
                    .run(scriptPath);

            long endTime = System.currentTimeMillis();
            println("=".repeat(50));
            println("[SUCCESS] Script completed in " + (endTime - startTime) + "ms");

        } catch (Vast.VastException e) {
            System.err.println("[FAILURE] Script execution failed: " + e.getMessage());
            // 堆栈追踪由Debugger根据模式决定是否显示
        }
    }

    // 移除handleEvalCommand方法

    private static void handleShellCommand() {
        println("@ Vast Interactive Shell");
        println("Type 'exit' or 'quit' to exit");
        println("Type 'clear' to clear screen");
        println("Type 'debug on/off' to toggle stack traces");
        println("=".repeat(50));

        Scanner scanner = new Scanner(System.in);
        boolean debugMode = false;

        while (true) {
            System.out.print("vast> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }
            if (input.equalsIgnoreCase("clear")) {
                clearScreen();
                continue;
            }
            if (input.equalsIgnoreCase("help")) {
                printShellHelp();
                continue;
            }

            // 处理调试模式切换
            if (input.startsWith("debug ")) {
                String mode = input.substring(6).toLowerCase();
                if ("on".equals(mode) || "true".equals(mode)) {
                    debugMode = true;
                    println("@ Debug mode ON - showing stack traces");
                } else if ("off".equals(mode) || "false".equals(mode)) {
                    debugMode = false;
                    println("@ Debug mode OFF");
                } else {
                    println("@ Usage: debug on/off");
                }
                continue;
            }

            try {
                // 在shell中直接使用VastVM执行单行代码
                VastVM vm = new VastVM();
                vm.setDebugMode(debugMode);
                vm.execute(List.of(input));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }
            }
        }

        scanner.close();
        System.out.println("@ Goodbye!");
    }

    // 修改printUsage方法 - 移除eval相关说明
    private static void printUsage() {
        println("@ Vast Command Line Interface");
        println("Usage: vast <command> [arguments]");
        println();
        println("Commands:");
        println("  run <script.vast> [--debug]    Execute a script file");
        println("  shell                Start interactive shell");
        println("  help [topic]         Show help information");
        println("  version              Show version info");
        println("  list                 List built-in features");
        println("  info <script.vast>   Show script statistics");
        println("  lib <command>        Manage external libraries");
        println();
        println("Examples:");
        println("  vast run script.vast");
        println("  vast run script.vast --debug");
        println("  vast shell");
        println("  vast help syntax");
    }

    private static void handleLibCommand(String[] args) {
        if (args.length < 2) {
            println("Usage: vast lib <command>");
            println("Commands:");
            println("  create <name>    Create a new library template");
            println("  list             List available libraries");
            println("  info <lib>       Show library information");
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "create":
                handleLibCreateCommand(args);
                break;
            case "list":
                handleLibListCommand();
                break;
            case "info":
                handleLibInfoCommand(args);
                break;
            default:
                println("Unknown lib command: " + subCommand);
                break;
        }
    }

    private static void handleHelpCommand(String[] args) {
        if (args.length > 1) {
            String topic = args[1].toLowerCase();
            switch (topic) {
                case "syntax":
                    printSyntaxHelp();
                    break;
                case "builtins":
                    printBuiltinsHelp();
                    break;
                case "examples":
                    printExamplesHelp();
                    break;
                default:
                    System.out.println("Unknown help topic: " + topic);
                    printGeneralHelp();
                    break;
            }
        } else {
            printGeneralHelp();
        }
    }

    private static void handleVersionCommand() {
        println("@ Vast v" + ver);
        println("Java: " + System.getProperty("java.version"));
    }

    private static void handleListCommand() {
        println("@ Vast Built-in Features:");
        println("=".repeat(50));

        println("Core Syntax:");
        println("  var x = 10                    - Variable declaration");
        println("  var (int) x = 10              - Typed variable");
        println("  imp ClassName                 - Import class");
        println("  loop(5): ...                  - Loop statement");
        println("  use(Class.method(args))       - Use statement");
        println("  swap(a, b)                    - Swap variables");
        println();

        println("Operators:");
        println("  a + b, a - b, a * b, a / b   - Basic arithmetic");
        println("  a ** b                        - Power");
        println("  a // b                        - Integer division");
        println("  a ++ b                        - Number concatenation");
        println("  a == b, a != b                - Equality");
        println("  a > b, a < b, a >= b, a <= b  - Comparison");
        println("  a && b, a || b                - Logical operators");
        println("  a & b, a | b, a ^ b, ~a       - Bitwise operators");
        println();

        println("Special Features:");
        println("  $(expression)                 - Fraction (single step)");
        println("  $$(expression)                - Permanent fraction");
        println("  Type casting: (type) value    - Explicit type conversion");
    }

    private static void handleInfoCommand(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: info <script.vast>");
            return;
        }

        String scriptPath = args[1];
        try {
            List<String> lines = Files.readAllLines(Paths.get(scriptPath));
            System.out.println("? Script Info: " + scriptPath);
            System.out.println("Lines: " + lines.size());

            int imports = 0, loops = 0, variables = 0, calls = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("imp ")) imports++;
                else if (trimmed.startsWith("loop")) loops++;
                else if (trimmed.startsWith("var ")) variables++;
                else if (trimmed.contains(".") && trimmed.contains("(")) calls++;
            }

            println("Imports: " + imports);
            println("Loops: " + loops);
            println("Variables: " + variables);
            println("Method calls: " + calls);

        } catch (IOException e) {
            println("Cannot read script: " + e.getMessage());
        }
    }

    private static void printGeneralHelp() {
        println("@ Vast Help");
        println("=================");
        println("For specific help topics, use:");
        println("  vast help syntax    - Language syntax");
        println("  vast help builtins  - Built-in features");
        println("  vast help examples  - Example scripts");
    }

    private static void printShellHelp() {
        println("Shell Commands:");
        println("  exit, quit  - Exit shell");
        println("  clear       - Clear screen");
        println("  debug on/off - Toggle stack traces");
        println();
        println("You can type any Vast code directly:");
        println("  var x = 10");
        println("  var y = x * 2");
        println("  swap(x, y)");
    }

    private static void printSyntaxHelp() {
        println("Vast Syntax");
        println("=================");
        println("imp Sys                    # Import class");
        println("a = 10                     # Free type assignment");
        println("int b = 20                 # Strong type assignment");
        println("string name = \"hello\"     # Strong type with string");
        println("int c = int(\"123\")        # Type cast assignment");
        println("int(d)                     # Inline type cast");
        println("loop(5):                   # Loop 5 times");
        println("    e = a + b              # Indented block");
        println("swap(a, b)                 # Swap variables");
        println("use(ClassName.method(args)) # Use method");
    }

    private static void printBuiltinsHelp() {
        handleListCommand();
    }

    private static void printExamplesHelp() {
        println("Example Scripts");
        println("===============");
        println("Basic:");
        println("  a = 10");
        println("  int b = 20");
        println("  c = a + b");
        println("  swap(a, b)");
        println();
        println("Type Conversion:");
        println("  string numStr = \"123\"");
        println("  int num = int(numStr)    # Convert string to int");
        println("  int(numStr)              # Inline conversion");
        println();
        println("Method Call:");
        println("  use(Sys.printl(\"Hello\"))");
        println("  use(Time.wait(1000))");
    }

    private static void handleLibCreateCommand(String[] args) {
        if (args.length < 3) {
            println("Usage: vast lib create <library-name>");
            return;
        }

        String libName = args[2];
        String targetDir = "./" + libName;

        try {
            // 创建目录结构
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(targetDir));
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(targetDir, "src"));

            // 创建library.properties
            String props = String.format(
                    "name=%s\n" +
                            "version=1.0.0\n" +
                            "description=A custom Vast library\n" +
                            "author=Your Name\n" +
                            "mainClass=com.example.%s.%sLibrary\n" +
                            "dependencies=\n" +
                            "config.example=value\n",
                    libName, libName.toLowerCase(), capitalize(libName)
            );

            java.nio.file.Files.write(java.nio.file.Paths.get(targetDir, "library.properties"), props.getBytes());

            // 创建示例Java文件
            String javaCode = String.format(
                    "package com.example.%s;\n\n" +
                            "import com.vast.registry.*;\n" +
                            "import com.vast.vm.VastVM;\n" +
                            "import java.util.*;\n\n" +
                            "public class %sLibrary implements VastExternalLibrary {\n\n" +
                            "    @Override\n" +
                            "    public LibraryMetadata getMetadata() {\n" +
                            "        return new LibraryMetadata(\n" +
                            "            \"%s\", \n" +
                            "            \"1.0.0\", \n" +
                            "            \"A custom Vast library\", \n" +
                            "            \"Your Name\"\n" +
                            "        );\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public void initialize(VastVM vm, VastLibraryRegistry registry) {\n" +
                            "        System.out.println(\"%s library initialized\");\n" +
                            "    }\n\n" +
                            "    @Override\n" +
                            "    public Map<String, Class<?>> getProvidedClasses() {\n" +
                            "        Map<String, Class<?>> classes = new HashMap<>();\n" +
                            "        // Register your classes here\n" +
                            "        // classes.put(\"MyClass\", MyClass.class);\n" +
                            "        return classes;\n" +
                            "    }\n\n" +
                            "    // Add your library methods here\n" +
                            "    public static void exampleMethod() {\n" +
                            "        System.out.println(\"Hello from %s library!\");\n" +
                            "    }\n" +
                            "}\n",
                    libName.toLowerCase(), capitalize(libName), libName, libName, libName
            );

            java.nio.file.Files.write(java.nio.file.Paths.get(targetDir, "src", capitalize(libName) + "Library.java"),
                    javaCode.getBytes());

            // 创建README
            String readme = String.format(
                    "# %s Library\n\n" +
                            "A custom library for Vast scripting language.\n\n" +
                            "## Usage\n\n" +
                            "```vast\n" +
                            "imp %s\n" +
                            "// Use your library classes and methods here\n" +
                            "```\n\n" +
                            "## Building\n\n" +
                            "1. Compile the Java source\n" +
                            "2. Package as .jar file\n" +
                            "3. Place in vast_libs directory or current directory\n",
                    libName, libName
            );

            java.nio.file.Files.write(java.nio.file.Paths.get(targetDir, "README.md"), readme.getBytes());

            println("@ Library template created: " + targetDir);
            println("@ Next steps:");
            println("  1. Edit library.properties");
            println("  2. Implement your library class");
            println("  3. Package as .jar or .zip");
            println("  4. Use with: imp " + libName);

        } catch (Exception e) {
            println("Failed to create library template: " + e.getMessage());
        }
    }

    private static void handleLibListCommand() {
        com.vast.registry.VastLibraryRegistry registry = com.vast.registry.VastLibraryRegistry.getInstance();
        java.util.Set<String> libraries = registry.getRegisteredLibraryIds();

        println("@ Available Libraries:");
        if (libraries.isEmpty()) {
            println("  No libraries registered");
        } else {
            libraries.forEach(lib -> println("  - " + lib));
        }
    }

    private static void handleLibInfoCommand(String[] args) {
        if (args.length < 3) {
            println("Usage: vast lib info <library-name>");
            return;
        }

        String libName = args[2];
        com.vast.registry.VastLibraryRegistry registry = com.vast.registry.VastLibraryRegistry.getInstance();

        if (registry.isLibraryLoaded(libName)) {
            com.vast.registry.VastExternalLibrary lib = registry.getLoadedLibrary(libName);
            println("@ Library Info: " + libName);
            println("  Metadata: " + lib.getMetadata());
            println("  Provided Classes: " + lib.getProvidedClasses().keySet());
        } else {
            println("Library not loaded: " + libName);
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    // 辅助方法
    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private static void println(String text) {
        System.out.println(text);
    }

    private static void println() {
        System.out.println();
    }
}