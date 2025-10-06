package com.volcano;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * VolcanoScript 命令行接口
 * 支持多种命令：run, eval, shell, help, version, list, info
 */
public class VolcanoCLI {

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
            println("@ Evaluating: " + code);
            Volcano.execute(code.toString());
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
        println("@ VolcanoScript v0.0.6");//在这里配置版本信息
        println("A lightly script language");
        println("Built for simplicity and performance");
        println("Java: " + System.getProperty("java.version"));
    }

    private static void handleListCommand() {
        println("@ Built-in Classes and Methods:");
        println("=".repeat(50));

        println("Sys:");
        println("  print(message)         - Print to console");
        println("  error(message)         - Print error");
        println("  sleep(ms)              - Sleep milliseconds");
        println("  time()                 - Current time in ms");
        println("  exit(code)             - Exit program");
        println("  input()                - Read input (returns value)");
        println("  input(\"prompt\")        - Show prompt and read input");
        println("  input(\"prompt\" + var x) - Show prompt and store in variable");
        println("  input(\"prompt\" + var x + var y) - Multi-variable input (space separated)");

        println("Loop Syntax:");
        println("  loop(5)              - Loop 5 times");
        println("  loop(true)           - Infinite loop");
        println("  loop(false)          - Skip loop");
        println("  loop(x > 0)          - Loop while condition is true");
        println("  loop(var)            - Loop based on variable value");

        println("\nTime:");
        println("  wait(seconds)     - Wait seconds");
        println("  now()             - Current time string");
        println("  timestamp()       - Current timestamp");
        println("  format(ts)        - Format timestamp");

        println("\nArray:");
        println("  length(array)     - Get array length");
        println("  contains(array, value) - Check if array contains value");
        println("  create(size)      - Create new array");
        println("  get(array, index) - Get element at index");
        println("  set(array, index, value) - Set element at index");

        println("\nOperator Description:");
        println("  +  : Number addition or string concatenation");
        println("  ++ : Number concatenation (12++14 → 1214) or increment (10++ → 11)");
        println("  -  : Number subtraction");
        println("  *  : Number multiplication");
        println("  /  : Number division");
        println("  %  : Modulo operation");
        println("  == : Equality comparison");
        println("  != : Inequality comparison");
        println("  >, <, >=, <= : Relational comparison");
        println("  && : Logical AND");
        println("  || : Logical OR");

        println("\nDataType (Auto-imported):");
        println("  String Operations:");
        println("    strLength(str)              - Get string length");
        println("    strSubstring(str, start, end) - Get substring");
        println("    strToUpper(str)             - Convert to uppercase");
        println("    strToLower(str)             - Convert to lowercase");
        println("    strContains(str, search)    - Check if contains substring");
        println("    strReplace(str, old, new)   - Replace substring");
        println("    strTrim(str)                - Trim leading and trailing spaces");

        println("  Number Operations (Replaces Math class):");
        println("    numParseInt(str)            - Parse string to integer");
        println("    numParseDouble(str)         - Parse string to double");
        println("    numAbs(value)               - Absolute value");
        println("    numMax(a, b)                - Maximum value");
        println("    numMin(a, b)                - Minimum value");
        println("    numRound(value)             - Round to nearest integer");
        println("    numCeil(value)              - Round up");
        println("    numFloor(value)             - Round down");
        println("    numSqrt(value)              - Square root");
        println("    numPow(base, exp)           - Power operation");
        println("    numRandom(max)              - Random number");

        println("  Boolean Operations:");
        println("    boolParse(str)              - Parse string to boolean");
        println("    boolToString(value)         - Convert to string");
        println("    boolAnd(a, b)               - Logical AND");
        println("    boolOr(a, b)                - Logical OR");
        println("    boolNot(a)                  - Logical NOT");
        println("    boolXor(a, b)               - Logical XOR");

        println("  Type Conversion:");
        println("    typeOf(obj)                 - Get type name");
        println("    toString(obj)               - Convert to string");
        println("    toInt(obj)                  - Convert to integer");
        println("    toDouble(obj)               - Convert to double");
        println("    toBoolean(obj)              - Convert to boolean");

        println("  Array Operations:");
        println("    arrLength(array)            - Get array length");
        println("    arrContains(array, value)   - Check if array contains value");
        println("    arrCreate(size)             - Create new array");
        println("    arrGet(array, index)        - Get array element");
        println("    arrSet(array, index, value) - Set array element");
        println("    arrSlice(array, start, end) - Get array slice");

        println("\nOps:");
        println("  and(a, b)         - Logical AND");
        println("  or(a, b)          - Logical OR");
        println("  not(a)            - Logical NOT");
        println("  concat(a, b)      - String concatenation");
        println("  repeat(str, n)    - Repeat string");
        println("  equals(a, b)      - Deep equality check");
        println("  notEquals(a, b)   - Deep inequality check");
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
        println("  help        - Show this help");
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
