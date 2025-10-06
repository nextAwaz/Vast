package com.volcano;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VolcanoCLI {
    static String ver = "0.0.7";//版本信息
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
        } catch (com.volcano.internal.exception.VolcanoRuntimeException e) {
            // 处理Volcano特定异常
            System.err.println("Volcano Error: " + e.getUserFriendlyMessage());
            if (Arrays.asList(args).contains("--debug")) {
                e.printStackTrace();
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected Error: " + e.getMessage());
            if (Arrays.asList(args).contains("--debug")) {
                e.printStackTrace();
            }
            System.exit(2);
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
            println("@ Running VolcanoScript: " + scriptPath);
            println("=".repeat(50));

            // 将 debug 标志传递给 Volcano
            Volcano.run(scriptPath, debug);

            long endTime = System.currentTimeMillis();
            println("=".repeat(50));
            println("[SUCCESS] Script completed in " + (endTime - startTime) + "ms");

        } catch (Volcano.VolcanoException e) {
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
            // 自动为 CLI eval 添加所有内置库的导入，避免手工写 imp
            StringBuilder finalCode = new StringBuilder();
            // VolcanoVM 在同一包中，可直接访问其静态 BUILTIN_CLASSES
            for (String cls : VolcanoVM.BUILTIN_CLASSES.keySet()) {
                finalCode.append("imp ").append(cls).append("\n");
            }
            finalCode.append(code.toString());

            println("@ Evaluating: " + finalCode);
            Volcano.execute(finalCode.toString());
        } catch (Volcano.VolcanoException e) {
            System.err.println("@ Evaluation failed: " + e.getMessage());
        }
    }

    private static void handleShellCommand() {
        println("@ VolcanoScript Interactive Shell");
        println("Type 'exit' or 'quit' to exit");
        println("Type 'clear' to clear screen");
        println("=".repeat(50));

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("volcano> ");
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
                Volcano.execute(input);
            } catch (Volcano.VolcanoException e) {
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
        println("@ VolcanoScript v" + ver);
        println("A lightly script language");
        println("Built for simplicity and performance");
        println("Java: " + System.getProperty("java.version"));
    }

    private static void handleListCommand() {
        println("@ Built-in Classes and Methods:");
        println("=".repeat(50));

        println("Sys:");
        println("  print(message)         - Print to console");
        println("  error(message)         - Print error to console");
        println("  sleep(ms)              - Sleep for milliseconds");
        println("  time()                 - Get current time in ms");
        println("  exit(code)             - Exit with code");
        println("  input(prompt)          - Read input with prompt");

        println("Time:");
        println("  currentMillis()        - Get current time in ms");
        println("  formatDate(date, fmt)  - Format date");

        println("Array:");
        println("  create(size)           - Create array");
        println("  get(array, index)      - Get element");
        println("  set(array, index, val) - Set element");

        println("Ops:");
        println("  and(a, b)              - Logical AND");
        println("  or(a, b)               - Logical OR");
        println("  not(a)                 - Logical NOT");
        println("  concat(a, b)           - String concatenation");
        println("  repeat(str, n)         - Repeat string");
        println("  equals(a, b)           - Equality check");
        println("  notEquals(a, b)        - Inequality check");

        println("DataType:");
        println("  strLength(str)         - String length");
        println("  strSubstring(str, s, e)- Substring");
        println("  strToUpper(str)        - To upper case");
        println("  strToLower(str)        - To lower case");
        println("  strContains(str, s)    - Contains check");
        println("  strReplace(str, o, n)  - Replace string");
        println("  strTrim(str)           - Trim string");
        println("  numParseInt(str)       - Parse int");
        println("  numParseDouble(str)    - Parse double");
        println("  numAbs(value)          - Absolute value");
        println("  numMax(a, b)           - Max");
        println("  numMin(a, b)           - Min");
        println("  numRound(value)        - Round");
        println("  numCeil(value)         - Ceil");
        println("  numFloor(value)        - Floor");
        println("  numSqrt(value)         - Square root");
        println("  numPow(base, exp)      - Power");
        println("  numRandom(max)         - Random number");
        println("  boolParse(str)         - Parse boolean");
        println("  boolToString(value)    - Convert to string");
        println("  boolAnd(a, b)          - Logical AND");
        println("  boolOr(a, b)           - Logical OR");
        println("  boolNot(a)             - Logical NOT");
        println("  boolXor(a, b)          - Logical XOR");

        println("  Type Conversion:");
        println("    typeOf(obj)          - Get type name");
        println("    toString(obj)        - Convert to string");
        println("    toInt(obj)           - Convert to integer");
        println("    toDouble(obj)        - Convert to double");
        println("    toBoolean(obj)       - Convert to boolean");

        println("  Array Operations:");
        println("    arrLength(array)     - Get array length");
        println("    arrContains(array, value) - Check if array contains value");
        println("    arrCreate(size)      - Create new array");
        println("    arrGet(array, index) - Get array element");
        println("    arrSet(array, index, value) - Set array element");
        println("    arrSlice(array, start, end) - Get array slice");
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

            int imports = 0, loops = 0, conditions = 0, calls = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("imp ")) imports++;
                else if (trimmed.startsWith("loop")) loops++;
                else if (trimmed.startsWith("if") || trimmed.startsWith(":elif")) conditions++;
                else if (trimmed.contains(".") && trimmed.contains("(")) calls++;
            }

            println("Imports: " + imports);
            println("Loops: " + loops);
            println("Conditions: " + conditions);
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
        println("@ VolcanoScript Command Line Interface");
        println("Usage: volcano <command> [arguments]");
        println();
        println("Commands:");
        println("  run <script.vast> [--debug]    Execute a script file");
        println("  eval \"code\"         Execute code directly");
        println("  shell                Start interactive shell");
        println("  help [topic]         Show help information");
        println("  version              Show version info");
        println("  list                 List built-in methods");
        println("  info <script.vast>   Show script statistics");
        println();
        println("Examples:");
        println("  volcano run level1.vast");
        println("  volcano run level1.vast --debug");
        println("  volcano eval \"Sys.print('Hello')\"");
        println("  volcano shell");
        println("  volcano help syntax");
    }

    private static void printGeneralHelp() {
        println("@ VolcanoScript Help");
        println("====================");
        println("For specific help topics, use:");
        println("  volcano help syntax    - Language syntax");
        println("  volcano help builtins  - Built-in methods");
        println("  volcano help examples  - Example scripts");
    }

    private static void printShellHelp() {
        println("Shell Commands:");
        println("  exit, quit  - Exit shell");
        println("  clear       - Clear screen");
        println();
        println("You can type any VolcanoScript code directly:");
        println("  Sys.print('Hello World')");
        println("  loop(3): Sys.print('Looping')");
    }

    private static void printSyntaxHelp() {
        println("VolcanoScript Syntax");
        println("====================");
        println("imp Sys                    # Import class");
        println("loop(5):                  # Loop 5 times");
        println("    Sys.print('Hello')    # Indented block");
        println("Class.method(args)        # Method call");
    }

    private static void printBuiltinsHelp() {
        // 简化的内置方法帮助
        handleListCommand();
    }

    private static void printExamplesHelp() {
        println("Example Scripts");
        println("===============");
        println("Basic:");
        println("  imp Sys");
        println("  Sys.print('Hello Volcano!')");
        println();
        println("Math Operations:");
        println("  imp Math");
        println("  loop(5):");
        println("      result = Math.add(10, 20)");
        println("      Sys.print(result)");
    }

    //大道至简
    private static void println(String text){
        System.out.println(text);
    }
    private static void println(){
        System.out.println();
    }
}
