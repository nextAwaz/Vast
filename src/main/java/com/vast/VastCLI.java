package com.vast;

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
        boolean debug = Arrays.asList(args).contains("--debug");

        long startTime = System.currentTimeMillis();
        try {
            println("@ Running VastScript: " + scriptPath);
            println("=".repeat(50));

            Vast.run(scriptPath, debug);

            long endTime = System.currentTimeMillis();
            println("=".repeat(50));
            println("[SUCCESS] Script completed in " + (endTime - startTime) + "ms");

        } catch (Vast.VastException e) {
            System.err.println("[FAILURE] Script execution failed: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    private static void handleEvalCommand(String[] args) {
        if (args.length < 2) {
            println("Usage: eval \"code\"");
            return;
        }

        // 合并所有参数作为代码
        StringBuilder code = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) code.append(" ");
            code.append(args[i]);
        }

        try {
            println("@ Evaluating: " + code);
            Vast.execute(code.toString());
        } catch (Vast.VastException e) {
            System.err.println("@ Evaluation failed: " + e.getMessage());
        }
    }

    private static void handleShellCommand() {
        println("@ VastScript Interactive Shell");
        println("Type 'exit' or 'quit' to exit");
        println("Type 'clear' to clear screen");
        println("=".repeat(50));

        Scanner scanner = new Scanner(System.in);
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

            try {
                Vast.execute(input);
            } catch (Vast.VastException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("@ Goodbye!");
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
        println("  run <script.vast> [--debug]    Execute a script file");
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