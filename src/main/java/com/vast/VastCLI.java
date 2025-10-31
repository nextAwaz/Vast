package com.vast;

import com.vast.Vast;
import com.vast.internal.exception.VastExceptions;
import com.vast.vm.VastVM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class VastCLI {
    static String ver = "0.1.2(hotfix-10)"; //版本信息
    private static LibraryManager libraryManager;

    public static void main(String[] args) {
        VastVM tempVM = new VastVM();
        libraryManager = new LibraryManager(tempVM);

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

    private static void handleShellCommand() {
        println("@ Vast Interactive Shell");
        println("Type 'exit' or 'quit' to exit");
        println("Type 'clear' to clear screen");
        println("Type 'reset' to reset VM state");
        println("Type 'debug on/off' to toggle stack traces");
        println("=".repeat(50));

        libraryManager.listLibraries();

        Scanner scanner = new Scanner(System.in);
        boolean debugMode = false;

        // 创建单个 VM 实例，在整个 shell 会话中保持
        VastVM vm = new VastVM();
        vm.setDebugMode(debugMode);

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
            if (input.equalsIgnoreCase("reset")) {
                // 重置 VM 状态
                vm.reset();
                println("@ VM state reset");
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
                    vm.setDebugMode(true);
                    println("@ Debug mode ON - showing stack traces");
                } else if ("off".equals(mode) || "false".equals(mode)) {
                    debugMode = false;
                    vm.setDebugMode(false);
                    println("@ Debug mode OFF");
                } else {
                    println("@ Usage: debug on/off");
                }
                continue;
            }

            try {
                // 检查是否是纯数学表达式（不包含语句关键字）
                if (isMathExpression(input)) {
                    // 将表达式包装成 printl 语句
                    String wrappedInput = "printl(" + input + ")";
                    vm.execute(List.of(wrappedInput));
                } else {
                    // 正常执行
                    vm.execute(List.of(input));
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }

                // 对于严重错误，建议用户重置 VM
                if (e instanceof VastExceptions.VastRuntimeException) {
                    println("@ Use 'reset' command to clear VM state if needed");
                }
            }
        }

        scanner.close();
        System.out.println("@ Goodbye!");
    }

    /**
     * 检查输入是否是纯数学表达式
     */
    private static boolean isMathExpression(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String trimmed = input.trim();

        // 检查是否包含语句关键字（不是数学表达式）
        String[] statementKeywords = {
                "imp ", "loop", "use(", "swap(", "var ", "int ", "string ", "bool ",
                "double ", "float ", "char ", "if ", "else", "while", "for "
        };

        for (String keyword : statementKeywords) {
            if (trimmed.startsWith(keyword)) {
                return false;
            }
        }

        // 检查是否是赋值表达式（包含等号但不是比较操作）
        if (trimmed.contains("=")) {
            // 如果是比较操作（==），允许作为数学表达式
            if (trimmed.contains("==") || trimmed.contains("!=") ||
                    trimmed.contains(">=") || trimmed.contains("<=")) {
                return true;
            }
            // 否则是赋值操作，不是纯数学表达式
            return false;
        }

        // 检查是否看起来像数学表达式（包含运算符或数字）
        boolean hasMathOperators = trimmed.matches(".*[+\\-*/%&|^~<>].*");
        boolean hasNumbers = trimmed.matches(".*\\d.*");
        boolean hasVariables = trimmed.matches(".*[a-zA-Z_].*");
        boolean hasParentheses = trimmed.contains("(") || trimmed.contains(")");

        // 如果是简单的变量引用，也当作数学表达式处理
        if (!hasMathOperators && !hasNumbers && hasVariables && !hasParentheses) {
            return true;
        }

        // 包含数学运算符或数字，且不包含语句特征
        return (hasMathOperators || hasNumbers) &&
                !trimmed.endsWith(":") && // 不是代码块
                !trimmed.contains("{");
    }

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
        println("Library Commands:");
        println("  lib create <name>    Create new library template");
        println("  lib list             List loaded libraries");
        println("  lib info <name>      Show library information");
        println("  lib status           Show loader status");
        println();
        println("Examples:");
        println("  vast run script.vast");
        println("  vast lib create MyMath");
        println("  vast lib list");
    }

    private static void handleLibCommand(String[] args) {
        if (args.length < 2) {
            println("Usage: vast lib <command>");
            println("Commands:");
            println("  create <name>    Create a new library template");
            println("  list             List available libraries");
            println("  info <lib>       Show library information");
            println("  status           Show loader status");
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
            case "status":
                handleLibStatusCommand();
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
        println("  a AND b, a OR b, a XOR b      - Logical operators");
        println("  NOT a                         - Logical negation");
        println();

        println("Special Features:");
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
        println("  reset       - Reset VM state (variables, imports, etc.)");
        println("  debug on/off - Toggle stack traces");
        println();
        println("You can type any Vast code directly:");
        println("  var x = 10");
        println("  int y = 20");
        println("  printl(x + y)");
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
        libraryManager.createLibrary(libName);
    }

    private static void handleLibListCommand() {
        libraryManager.listLibraries();
    }

    private static void handleLibInfoCommand(String[] args) {
        if (args.length < 3) {
            println("Usage: vast lib info <library-name>");
            return;
        }

        String libName = args[2];
        libraryManager.showLibraryInfo(libName);
    }

    private static void handleLibStatusCommand() {
        libraryManager.showLoaderInfo();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    // 辅助方法
    private static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // 备用清屏方法
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }

    private static void println(String text) {
        System.out.println(text);
    }

    private static void println() {
        System.out.println();
    }
}