package com.vast;

import com.vast.internal.Debugger;
import com.vast.registry.VastExternalLibrary;
import com.vast.registry.VastLibraryRegistry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VastCLI {
    static String ver = "0.1.0"; //VAST版本信息

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
                case "eval":
                    handleEvalCommand(args);
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
            println("Usage: run <script.vast> [--debug-level=basic|detail|base]");
            return;
        }

        String scriptPath = args[1];
        Debugger.Level debugLevel = Debugger.Level.BASIC;

        // 解析调试等级参数
        for (String arg : args) {
            if (arg.startsWith("--debug-level=")) {
                String levelStr = arg.substring("--debug-level=".length()).toLowerCase();
                switch (levelStr) {
                    case "detail":
                        debugLevel = Debugger.Level.DETAIL;
                        break;
                    case "base":
                        debugLevel = Debugger.Level.BASE;
                        break;
                    case "basic":
                    default:
                        debugLevel = Debugger.Level.BASIC;
                        break;
                }
            }
        }

        long startTime = System.currentTimeMillis();
        try {
            println("@ Running VastScript: " + scriptPath);
            if (debugLevel != Debugger.Level.BASIC) {
                println("@ Debug level: " + debugLevel);
            }
            println("=".repeat(50));

            Vast.run(scriptPath, debugLevel);

            long endTime = System.currentTimeMillis();
            println("=".repeat(50));
            println("[SUCCESS] Script completed in " + (endTime - startTime) + "ms");

        } catch (Vast.VastException e) {
            System.err.println("[FAILURE] Script execution failed: " + e.getMessage());
            if (debugLevel == Debugger.Level.BASE) {
                e.printStackTrace();
            }
        }
    }

    private static void handleLibCreateCommand(String[] args) {
        if (args.length < 3) {
            println("Usage: vast lib create <library-name>");
            return;
        }

        String libName = args[2];
        String targetDir = "./" + libName;

        try {
            // 从resources复制示例库模板
            copyExampleLibraryTemplate(libName, targetDir);
            println("@ Library template created: " + targetDir);
            println("@ Next steps:");
            println("  1. Edit library.properties");
            println("  2. Implement your library class");
            println("  3. Package as .jar or .zip");
            println("  4. Use with: imp " + libName);

        } catch (Exception e) {
            System.err.println("Failed to create library template: " + e.getMessage());
        }
    }

    private static void copyExampleLibraryTemplate(String libName, String targetDir) throws IOException {
        // 这里实现从resources目录复制exampleLibs.zip内容的逻辑
        // 由于无法直接访问文件系统，这里提供伪代码

        File target = new File(targetDir);
        if (target.exists()) {
            throw new IOException("Target directory already exists: " + targetDir);
        }

        // 创建目录结构
        Files.createDirectories(Paths.get(targetDir));
        Files.createDirectories(Paths.get(targetDir, "src"));

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

        Files.write(Paths.get(targetDir, "library.properties"), props.getBytes());

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

        Files.write(Paths.get(targetDir, "src", capitalize(libName) + "Library.java"),
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

        Files.write(Paths.get(targetDir, "README.md"), readme.getBytes());
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private static void handleLibListCommand() {
        VastLibraryRegistry registry = VastLibraryRegistry.getInstance();
        Set<String> libraries = registry.getRegisteredLibraryIds();

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
        VastLibraryRegistry registry = VastLibraryRegistry.getInstance();

        if (registry.isLibraryLoaded(libName)) {
            VastExternalLibrary lib = registry.getLoadedLibrary(libName);
            println("@ Library Info: " + libName);
            println("  Metadata: " + lib.getMetadata());
            println("  Provided Classes: " + lib.getProvidedClasses().keySet());
        } else {
            println("Library not loaded: " + libName);
        }
    }

    private static void handleEvalCommand(String[] args) {
        if (args.length < 2) {
            println("Usage: eval \"code\" [--debug-level=basic|detail|base]");
            return;
        }

        // 解析调试等级参数
        Debugger.Level debugLevel = Debugger.Level.BASIC;
        StringBuilder code = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--debug-level=")) {
                String levelStr = args[i].substring("--debug-level=".length()).toLowerCase();
                switch (levelStr) {
                    case "detail":
                        debugLevel = Debugger.Level.DETAIL;
                        break;
                    case "base":
                        debugLevel = Debugger.Level.BASE;
                        break;
                    case "basic":
                    default:
                        debugLevel = Debugger.Level.BASIC;
                        break;
                }
            } else {
                if (code.length() > 0) code.append(" ");
                code.append(args[i]);
            }
        }

        try {
            println("@ Evaluating: " + code);
            Vast.execute(code.toString(), debugLevel);
        } catch (Vast.VastException e) {
            System.err.println("@ Evaluation failed: " + e.getMessage());
            if (debugLevel == Debugger.Level.BASE) {
                e.printStackTrace();
            }
        }
    }

    private static void handleShellCommand() {
        println("@ VastScript Interactive Shell");
        println("Type 'exit' or 'quit' to exit");
        println("Type 'clear' to clear screen");
        println("Type 'debug basic|detail|base' to change debug level");
        println("=".repeat(50));

        Scanner scanner = new Scanner(System.in);
        Debugger.Level debugLevel = Debugger.Level.BASIC;

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

            // 处理调试等级设置
            if (input.startsWith("debug ")) {
                String levelStr = input.substring(6).toLowerCase();
                switch (levelStr) {
                    case "detail":
                        debugLevel = Debugger.Level.DETAIL;
                        println("@ Debug level set to: DETAIL");
                        break;
                    case "base":
                        debugLevel = Debugger.Level.BASE;
                        println("@ Debug level set to: BASE");
                        break;
                    case "basic":
                        debugLevel = Debugger.Level.BASIC;
                        println("@ Debug level set to: BASIC");
                        break;
                    default:
                        println("@ Unknown debug level: " + levelStr);
                        println("@ Available levels: basic, detail, base");
                        break;
                }
                continue;
            }

            try {
                Vast.execute(input, debugLevel);
            } catch (Vast.VastException e) {
                System.err.println("Error: " + e.getMessage());
                if (debugLevel == Debugger.Level.BASE) {
                    e.printStackTrace();
                }
            }
        }

        scanner.close();
        System.out.println("@ Goodbye!");
    }

    //外置库模块生成
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
        println("@ VastScript v" + ver);
        println("A lightweight script language with AST");
        println("Built for simplicity and performance");
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
        println("  give(Class)(var1, var2)       - Give statement");
        println("  do(Class)(method)(args)       - Do statement");
        println("  swap(a)(b)                    - Swap variables");
        println();

        println("Operators:");
        println("  a + b                         - Addition/Concatenation");
        println("  a - b                         - Subtraction");
        println("  a * b                         - Multiplication");
        println("  a / b                         - Division");
        println("  a ** b                        - Power");
        println("  a // b                        - Integer division");
        println("  a ++ b                        - Number concatenation");
        println("  a == b, a != b                - Equality");
        println("  a > b, a < b, a >= b, a <= b  - Comparison");
        println("  a && b, a || b                - Logical operators");
        println();

        println("Type System:");
        println("  Dynamic typing with optional static type hints");
        println("  Automatic type conversion");
        println("  Support for: int, double, boolean, string");
        println();

        println("AST Features:");
        println("  Abstract Syntax Tree compilation");
        println("  Visitor pattern for execution");
        println("  Extensible architecture");
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

    // 辅助方法
    private static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private static void printUsage() {
        println("@ VastScript Command Line Interface");
        println("Usage: vast <command> [arguments]");
        println();
        println("Commands:");
        println("  run <script.vast> [--debug-mode=[level]]    Execute a script file");
        println("  eval \"code\"         Execute code directly");
        println("  shell                Start interactive shell");
        println("  help [topic]         Show help information");
        println("  version              Show version info");
        println("  list                 List built-in features");
        println("  info <script.vast>   Show script statistics");
        println();
        println("Examples:");
        println("  vast run script.vast");
        println("  vast run script.vast --debug");
        println("  vast eval \"var x = 10\"");
        println("  vast shell");
        println("  vast help syntax");
    }

    private static void printGeneralHelp() {
        println("@ VastScript Help");
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
        println("  debug level - Set debug level (basic|detail|base)");
        println();
        println("You can type any VastScript code directly:");
        println("  var x = 10");
        println("  var y = x * 2");
        println("  swap(x)(y)");
    }

    private static void printSyntaxHelp() {
        println("VastScript Syntax");
        println("=================");
        println("imp Sys                    # Import class");
        println("var x = 10                 # Variable declaration");
        println("var (int) y = 20           # Typed variable");
        println("loop(5):                   # Loop 5 times");
        println("    var z = x + y          # Indented block");
        println("swap(a)(b)                 # Swap variables");
        println("give(Class)(var1, var2)    # Give variables to class");
    }

    private static void printBuiltinsHelp() {
        handleListCommand();
    }

    private static void printExamplesHelp() {
        println("Example Scripts");
        println("===============");
        println("Basic:");
        println("  var x = 10");
        println("  var y = 20");
        println("  var z = x + y");
        println("  swap(x)(y)");
        println();
        println("Loop:");
        println("  loop(3):");
        println("      var i = _index");
        println("      # loop body");
    }

    // 大道至简
    private static void println(String text) {
        System.out.println(text);
    }

    private static void println() {
        System.out.println();
    }
}