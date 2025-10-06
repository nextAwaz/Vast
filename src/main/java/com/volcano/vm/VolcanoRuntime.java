package com.volcano.vm;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.*;
import com.volcano.internal.*;
import com.volcano.internal.exception.*;

public class VolcanoRuntime {
    private final Map<String, Class<?>> importedClasses;
    private final Map<String, Object> variables;
    private final Map<String, String> variableTypes = new HashMap<>(); // 存储静态类型声明（如 "int","double","boolean","string"）
    private final Stack<LoopContext> loopStack = new Stack<>();

    private List<Instruction> instructions = new ArrayList<>();
    private int pc = 0; // 程序计数器
    private Object lastResult = null;

    private boolean debugMode = false;

    private Object giveResult = null;// 用于存储give语句的结果
    private DoStatementHandler doStatementHandler;//do语句的功能引入
    private MethodInvocationHandler methodInvocationHandler;//处理外部Method

    // 未初始化变量哨兵
    private static final Object UNINITIALIZED = new Object();

    public VolcanoRuntime(Map<String, Class<?>> importedClasses, Map<String, Object> variables) {
        this.importedClasses = importedClasses;
        this.variables = variables;
        this.methodInvocationHandler = new MethodInvocationHandler(this, importedClasses);
        this.doStatementHandler = new DoStatementHandler(this, importedClasses, variables);

        // 自动导入 DataType 类
        this.importedClasses.put("DataType", DataType.class);
    }

    /**
     * 设置调试模式
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * 调试输出方法
     */
    private void debugPrint(String message) {
        if (debugMode) {
            System.out.println(message);
        }
    }

    /**
     * 调试输出方法（带格式）
     */
    private void debugPrint(String format, Object... args) {
        if (debugMode) {
            System.out.printf(format + "%n", args);
        }
    }

    public void execute(List<String> sourceLines) throws Exception {
        // 新功能: 初始化外部程序（如果存在）
        Object external = VolcanoVM.getGlobal("EXTERNAL_PROGRAM");
        if (external instanceof ExternalVolcanoExtension) {
            ((ExternalVolcanoExtension) external).init();
        }

        // 预编译为指令序列
        compile(sourceLines);

        // 执行指令
        while (pc < instructions.size()) {
            Instruction instr = instructions.get(pc);
            executeInstruction(instr);
            pc++;
        }

        // 新功能: 清理外部程序（如果存在）
        if (external instanceof ExternalVolcanoExtension) {
            ((ExternalVolcanoExtension) external).cleanup();
        }
    }

    public Object getLastResult() {
        return lastResult;
    }

    public Object getGiveResult() {
        return giveResult;
    }

    private void compile(List<String> sourceLines) throws Exception {
        instructions.clear();
        Deque<BlockContext> blockStack = new ArrayDeque<>();

        for (int i = 0; i < sourceLines.size(); i++) {
            String line = sourceLines.get(i);
            int indent = calculateIndent(line);
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) continue;

            // 处理块结束
            while (!blockStack.isEmpty() && indent <= blockStack.peek().indent) {
                BlockContext endedBlock = blockStack.pop();
                instructions.add(new Instruction(OpCode.END_BLOCK, "", i, endedBlock.indent));
            }

            if (trimmedLine.startsWith("imp ")) {
                instructions.add(new Instruction(OpCode.IMPORT, trimmedLine.substring(4).trim(), i, indent));
            } else if (trimmedLine.startsWith("var ")) {
                instructions.add(new Instruction(OpCode.VAR_DECL, trimmedLine.substring(4).trim(), i, indent));
            } else if (trimmedLine.startsWith("loop")) {
                String condition = extractCondition(trimmedLine);
                blockStack.push(new BlockContext(BlockType.LOOP, indent));
                instructions.add(new Instruction(OpCode.LOOP_START, condition, i, indent));
            } else if (trimmedLine.startsWith("give")) {
                GiveInstructionData data = parseGiveStatement(trimmedLine);
                instructions.add(new Instruction(OpCode.GIVE, "", i, indent, data));
            } else if (trimmedLine.startsWith("do")) {
                DoInstructionData data = parseDoStatement(trimmedLine);
                instructions.add(new Instruction(OpCode.DO, "", i, indent, data));
            } else if (trimmedLine.startsWith("change ")) {
                // 新增 change 语句
                instructions.add(new Instruction(OpCode.CHANGE, trimmedLine.substring(7).trim(), i, indent));
            } else if (trimmedLine.contains("=") && !trimmedLine.contains("(") && !trimmedLine.contains(")")) {
                instructions.add(new Instruction(OpCode.VAR_ASSIGN, trimmedLine, i, indent));
            } else {
                instructions.add(new Instruction(OpCode.CALL, trimmedLine, i, indent));
            }
        }

        // 结束所有未关闭的块
        while (!blockStack.isEmpty()) {
            BlockContext endedBlock = blockStack.pop();
            instructions.add(new Instruction(OpCode.END_BLOCK, "", sourceLines.size(), endedBlock.indent));
        }
    }

    private void executeInstruction(Instruction instr) throws Exception {
        switch (instr.opCode) {
            case IMPORT:
                handleImport(instr.operand);
                break;
            case VAR_DECL:
                handleVarDecl(instr.operand, instr.lineNumber);
                break;
            case VAR_ASSIGN:
                handleVarAssign(instr.operand, instr.lineNumber);
                break;
            case LOOP_START:
                handleLoopStart(instr);
                break;
            case END_BLOCK:
                handleEndBlock();
                break;
            case CALL:
                handleMethodCall(instr.operand);
                break;
            case GIVE:
                handleGiveStatement((GiveInstructionData) instr.extraData);
                break;
            case DO:
                handleDoStatement((DoInstructionData) instr.extraData);
                break;
            case CHANGE:
                handleChangeStatement(instr.operand, instr.lineNumber);
                break;
        }
    }



    /**
     * 解析give语句
     * 格式: give([className])(var1, var2, ...)
     */
    private GiveInstructionData parseGiveStatement(String line) {
        // 移除行内注释
        int commentIndex = line.indexOf('#');
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex).trim();
        }

        // 提取第一个括号内的内容（类名）
        int firstParenStart = line.indexOf('(');
        int firstParenEnd = findMatchingParen(line, firstParenStart);
        if (firstParenStart == -1 || firstParenEnd == -1) {
            throw new NotGrammarException("Invalid give statement: missing parentheses");
        }

        String classNamePart = line.substring(firstParenStart + 1, firstParenEnd).trim();

        // 提取第二个括号内的内容（变量名）
        int secondParenStart = line.indexOf('(', firstParenEnd + 1);
        int secondParenEnd = findMatchingParen(line, secondParenStart);
        if (secondParenStart == -1 || secondParenEnd == -1) {
            throw new NotGrammarException("Invalid give statement: missing second parentheses");
        }

        String varsPart = line.substring(secondParenStart + 1, secondParenEnd).trim();

        return new GiveInstructionData(classNamePart, varsPart);
    }

    /**
     * 解析do语句
     * 格式: do([className])(methodName)(arg1, arg2, ...)
     */
    private DoInstructionData parseDoStatement(String line) {
        // 移除行内注释
        int commentIndex = line.indexOf('#');
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex).trim();
        }

        // 提取第一个括号内的内容（类名）
        int firstParenStart = line.indexOf('(');
        int firstParenEnd = findMatchingParen(line, firstParenStart);
        if (firstParenStart == -1 || firstParenEnd == -1) {
            throw new NotGrammarException("Invalid do statement: missing parentheses");
        }

        String classNamePart = line.substring(firstParenStart + 1, firstParenEnd).trim();

        // 提取第二个括号内的内容（方法名）
        int secondParenStart = line.indexOf('(', firstParenEnd + 1);
        int secondParenEnd = findMatchingParen(line, secondParenStart);
        if (secondParenStart == -1 || secondParenEnd == -1) {
            throw new NotGrammarException("Invalid do statement: missing second parentheses");
        }

        String methodNamePart = line.substring(secondParenStart + 1, secondParenEnd).trim();

        // 提取第三个括号内的内容（参数）
        int thirdParenStart = line.indexOf('(', secondParenEnd + 1);
        int thirdParenEnd = findMatchingParen(line, thirdParenStart);
        if (thirdParenStart == -1 || thirdParenEnd == -1) {
            throw new NotGrammarException("Invalid do statement: missing third parentheses");
        }

        String argsPart = line.substring(thirdParenStart + 1, thirdParenEnd).trim();

        return new DoInstructionData(classNamePart, methodNamePart, argsPart);
    }

    /**
     * 找到匹配的右括号
     */
    private int findMatchingParen(String str, int startParen) {
        if (startParen == -1) return -1;

        int depth = 1;
        for (int i = startParen + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 处理give语句 - 只能传递变量
     */
    private void handleGiveStatement(GiveInstructionData data) throws Exception {
        String className = data.className.isEmpty() ? "DEFAULT" : data.className;

        // 解析变量名列表
        String[] varNames = parseVariableNames(data.arguments);

        // 收集变量值
        Map<String, Object> variablesToGive = new HashMap<>();
        for (String varName : varNames) {
            if (!variables.containsKey(varName)) {
                throw NonExistentObject.variableNotFound(varName);
            }
            Object val = variables.get(varName);
            if (val == UNINITIALIZED) {
                throw new NullTokenException(varName, "give statement");
            }
            variablesToGive.put(varName, val);
        }

        // 存储give结果，供外部程序获取
        giveResult = new GiveResult(className, variablesToGive);

        // 输出调试信息
        debugPrint("@ Give: Passing " + variablesToGive.size() + " variable(s) to " + className);
        for (Map.Entry<String, Object> entry : variablesToGive.entrySet()) {
            System.out.println("  Variable " + entry.getKey() + ": " + entry.getValue() +
                    " (type: " + (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
        }
    }

    /**
     * 存储输入变量
     */
    private void storeInputVariable(String varName, String value) {
        Object typedValue = inferInputType(value);
        variables.put(varName, typedValue);
    }

    /**
     * 推断输入值的类型
     */
    private Object inferInputType(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // 尝试解析为整数
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // 不是整数，继续尝试
        }

        // 尝试解析为浮点数
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // 不是数字，继续尝试
        }

        // 检查布尔值
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;

        // 默认为字符串
        return value;
    }

    /**
     * 解析变量名列表（只允许变量名，不允许字面量）
     */
    private String[] parseVariableNames(String varsStr) {
        if (varsStr.trim().isEmpty()) return new String[0];

        List<String> varNames = new ArrayList<>();
        String[] parts = splitArgs(varsStr);

        for (String part : parts) {
            part = part.trim();

            // 检查是否为有效的变量名（不能是字面量）
            if (isLiteral(part)) {
                throw new NotGrammarException("Give statement can only pass variables, not literals: " + part);
            }

            varNames.add(part);
        }
        return varNames.toArray(new String[0]);
    }

    /**
     * 检查字符串是否为字面量（数字、字符串、布尔值）
     */
    private boolean isLiteral(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) return true; // 字符串字面量
        if (str.equals("true") || str.equals("false")) return true;  // 布尔字面量

        // 检查是否为数字字面量
        try {
            if (str.contains(".")) {
                Double.parseDouble(str);
            } else {
                Integer.parseInt(str);
            }
            return true;
        } catch (NumberFormatException e) {
            // 不是数字，继续检查
        }

        return false;
    }

    /**
     * 处理do语句
     */
    private void handleDoStatement(DoInstructionData data) throws Exception {
        Object result = doStatementHandler.executeDoStatement(
                data.className,
                data.methodName,
                data.arguments
        );

        debugPrint("@ Do: " + data.className + "." + data.methodName + "() returned: " + result);
        this.lastResult = result;
    }

    private void handleImport(String className) throws Exception {
        // 类似原有逻辑
        Class<?> clazz = VolcanoVM.BUILTIN_CLASSES.get(className);
        if (clazz == null) {
            clazz = Class.forName(className);
        }
        importedClasses.put(className, clazz);
    }

    private void handleVarDecl(String declaration) throws Exception {
        handleVarDecl(declaration, -1);
    }

    /**
     * 处理 var 声明，支持：
     *   name
     *   name = expr
     *   (type) name
     *   (type) name = expr
     */
    private void handleVarDecl(String declaration, int lineNumber) throws Exception {
        // 支持 (type) 前缀
        // 正则: optional (type) 变量名 optional = expr
        Pattern p = Pattern.compile("^(?:\\s*\\(([^)]+)\\)\\s*)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=\\s*(.+))?$");
        Matcher m = p.matcher(declaration);
        if (!m.matches()) {
            throw NotGrammarException.invalidStatementStructure("variable declaration", "Invalid declaration: " + declaration, lineNumber);
        }

        String typeName = m.group(1); // may be null
        String varName = m.group(2);
        String expr = m.group(3); // may be null

        if (expr == null) {
            // 声明但未赋值
            variables.put(varName, UNINITIALIZED);
            if (typeName != null) {
                variableTypes.put(varName, normalizeTypeName(typeName));
            }
            debugPrint("@ Var declared: %s (type=%s) uninitialized", varName, typeName);
            return;
        }

        Object value = evaluateExpression(expr, lineNumber);

        if (value == UNINITIALIZED) {
            throw new NullTokenException(varName, "variable declaration");
        }

        if (typeName != null) {
            String normType = normalizeTypeName(typeName);
            // 静态类型要求严格匹配（不允许隐式转换）
            if (!isExactTypeMatchToType(value, normType)) {
                throw new NotGrammarException("Type mismatch: cannot assign value of type " +
                        (value != null ? value.getClass().getSimpleName() : "null") +
                        " to variable '" + varName + "' declared as " + normType);
            }
            variableTypes.put(varName, normType);
        }

        variables.put(varName, value);
        debugPrint("@ Var declared: %s = %s (type=%s)", varName, value, typeName);
    }

    private void handleVarAssign(String assignment) throws Exception {
        handleVarAssign(assignment, -1);
    }

    private void handleVarAssign(String assignment, int lineNumber) throws Exception {
        String[] parts = assignment.split("=", 2);
        if (parts.length != 2) {
            throw NotGrammarException.invalidStatementStructure("variable assignment", "Missing assignment", lineNumber);
        }

        String varName = parts[0].trim();
        String expr = parts[1].trim();

        Object value = evaluateExpression(expr, lineNumber);

        if (value == UNINITIALIZED) {
            throw new NullTokenException(varName, "assignment");
        }

        // 如果变量已被声明为静态类型，强制类型检查（严格匹配）
        if (variableTypes.containsKey(varName)) {
            String expectedType = variableTypes.get(varName);
            if (!isExactTypeMatchToType(value, expectedType)) {
                throw new NotGrammarException("Type mismatch: cannot assign value of type " +
                        (value != null ? value.getClass().getSimpleName() : "null") +
                        " to variable '" + varName + "' declared as " + expectedType);
            }
        }

        variables.put(varName, value);
        debugPrint("@ Var assign: %s = %s", varName, value);
    }

    /**
     * 增加 change 语句处理： change targetVar::targetType = sourceVar::sourceType
     * 将 sourceVar 的值按 sourceType 解析并转换为 targetType，赋给 targetVar 并标记 targetVar 的静态类型为 targetType。
     */
    private void handleChangeStatement(String operand, int lineNumber) throws Exception {
        // operand 已经是 "target::t1 = source::t2" 形式，做严格解析
        Pattern p = Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)::([a-zA-Z]+)\\s*=\\s*([a-zA-Z_][a-zA-Z0-9_]*)::([a-zA-Z]+)$");
        Matcher m = p.matcher(operand);
        if (!m.find()) {
            throw NotGrammarException.invalidStatementStructure("change statement", "Invalid syntax: " + operand, lineNumber);
        }

        String targetVar = m.group(1);
        String targetType = normalizeTypeName(m.group(2));
        String sourceVar = m.group(3);
        String sourceType = normalizeTypeName(m.group(4));

        // 检查 sourceVar 存在且已初始化
        if (!variables.containsKey(sourceVar)) {
            throw NonExistentObject.variableNotFound(sourceVar);
        }
        Object sourceVal = variables.get(sourceVar);
        if (sourceVal == UNINITIALIZED) {
            throw new NullTokenException(sourceVar, "change statement");
        }

        // 如果 sourceVar 有静态类型声明，确认它与提供的 sourceType 一致（否则可能是用户错误）
        if (variableTypes.containsKey(sourceVar)) {
            String declaredSourceType = variableTypes.get(sourceVar);
            if (!declaredSourceType.equals(sourceType)) {
                throw new NotGrammarException("Source variable '" + sourceVar + "' declared as " + declaredSourceType +
                        " but change specified source type " + sourceType);
            }
        }

        // 尝试把 sourceVal 转换为 targetType
        Object converted = convertToType(sourceVal, targetType);
        if (converted == null && targetType.equals("string")) {
            converted = "null";
        }

        // 存储到 targetVar 并设置静态类型
        variables.put(targetVar, converted);
        variableTypes.put(targetVar, targetType);

        debugPrint("@ Change: %s set to %s (type %s) from %s::%s", targetVar, converted, targetType, sourceVar, sourceType);
    }

    /**
     * 将对象转换为指定的目标类型（严格转换，仅能在可预见的情形下转换）
     */
    private Object convertToType(Object value, String targetType) {
        if (targetType == null) return value;

        switch (targetType) {
            case "int":
                if (value instanceof Integer) return value;
                if (value instanceof Number) return ((Number) value).intValue();
                if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
                if (value instanceof String) {
                    try { return Integer.parseInt((String) value); }
                    catch (NumberFormatException e) { throw new NotGrammarException("Cannot convert string to int: " + value); }
                }
                break;
            case "double":
                if (value instanceof Double) return value;
                if (value instanceof Number) return ((Number) value).doubleValue();
                if (value instanceof Boolean) return (Boolean) value ? 1.0 : 0.0;
                if (value instanceof String) {
                    try { return Double.parseDouble((String) value); }
                    catch (NumberFormatException e) { throw new NotGrammarException("Cannot convert string to double: " + value); }
                }
                break;
            case "boolean":
                if (value instanceof Boolean) return value;
                if (value instanceof Number) return ((Number) value).doubleValue() != 0;
                if (value instanceof String) return !((String) value).isEmpty();
                break;
            case "string":
                return value != null ? value.toString() : "null";
            default:
                // 未知类型，尝试直接返回
                return value;
        }

        throw new NotGrammarException("Unsupported conversion to " + targetType + " from " +
                (value != null ? value.getClass().getSimpleName() : "null"));
    }

    private void handleMethodCall(String line) throws Exception {
        // 移除行内注释
        int commentIndex = line.indexOf('#');
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex).trim();
        }

        if (line.isEmpty()) {
            return;
        }

        int dotIndex = line.indexOf('.');
        int parenIndex = line.indexOf('(');

        if (dotIndex == -1 || parenIndex == -1) {
            throw new NotGrammarException("Invalid method call: " + line);
        }

        String className = line.substring(0, dotIndex);
        String methodName = line.substring(dotIndex + 1, parenIndex);
        String argsStr = line.substring(parenIndex + 1, line.length() - 1);

        // 特殊处理 Sys.input 调用
        if ("Sys".equals(className) && "input".equals(methodName)) {
            handleSysInput(argsStr);
            return;
        }

        // 使用 MethodInvocationHandler 处理方法调用
        Object result = methodInvocationHandler.invokeMethod(className, methodName, argsStr);
        this.lastResult = result;

        if (debugMode) {
            debugPrint("@ Method call: " + className + "." + methodName + "() returned: " + result);
        }
    }

    /**
     * 处理 Sys.input 调用 - 支持多值输入
     */
    private void handleSysInput(String argsStr) throws Exception {
        debugPrint("@ 处理输入调用: " + argsStr);

        if (argsStr.trim().isEmpty()) {
            // 无参数调用
            String input = (String) methodInvocationHandler.invokeSysInput("");
            this.lastResult = input;
            return;
        }

        // 解析输入参数
        InputParseResult parseResult = parseInputArguments(argsStr);
        String prompt = parseResult.prompt;
        List<String> varNames = parseResult.varNames;

        debugPrint("@ 输入配置: 提示='%s', 变量数量=%d", prompt, varNames.size());

        if (varNames.size() > 1) {
            // 多个变量 - 使用多值输入
            debugPrint("@ 多变量输入模式");

            // 读取一行输入
            String inputLine;
            if (prompt.isEmpty()) {
                inputLine = (String) methodInvocationHandler.invokeSysInput("");
            } else {
                inputLine = (String) methodInvocationHandler.invokeSysInput("\"" + prompt + "\"");
            }

            debugPrint("@ 原始输入: '%s'", inputLine);

            // 按空格分割输入
            String[] inputValues = inputLine.split("\\s+", varNames.size());

            // 存储每个变量
            for (int i = 0; i < varNames.size(); i++) {
                String value = (i < inputValues.length) ? inputValues[i] : "";
                storeInputVariable(varNames.get(i), value);
                debugPrint("@ 设置变量 '%s' = '%s'", varNames.get(i), value);
            }

            this.lastResult = inputValues.length > 0 ? inputValues[0] : "";

        } else if (varNames.size() == 1) {
            // 单个变量
            String input;
            if (prompt.isEmpty()) {
                input = (String) methodInvocationHandler.invokeSysInput("");
            } else {
                input = (String) methodInvocationHandler.invokeSysInput("\"" + prompt + "\"");
            }

            // 存储变量
            storeInputVariable(varNames.get(0), input);
            debugPrint("@ 设置变量 '%s' = '%s'", varNames.get(0), input);

            this.lastResult = input;
        } else {
            // 没有变量，只有提示
            if (!prompt.isEmpty()) {
                String input = (String) methodInvocationHandler.invokeSysInput("\"" + prompt + "\"");
                this.lastResult = input;
            } else {
                String input = (String) methodInvocationHandler.invokeSysInput("");
                this.lastResult = input;
            }
        }
    }


    /**
     * 专门解析输入参数的方法 - 修复版
     */
    private InputParseResult parseInputArguments(String argsStr) throws Exception {
        InputParseResult result = new InputParseResult();

        // 移除所有空白字符，方便解析
        String cleanArgs = argsStr.replaceAll("\\s+", " ").trim();

        // 检查是否有字符串提示部分
        if (cleanArgs.startsWith("\"")) {
            // 解析字符串字面量
            ParseResult stringResult = parseCompleteStringLiteral(cleanArgs, -1);
            result.prompt = stringResult.value;

            // 解析剩余部分中的变量声明
            if (!stringResult.remaining.isEmpty()) {
                parseVariableDeclarations(stringResult.remaining, result.varNames);
            }
        } else {
            // 没有字符串提示，直接解析变量声明
            parseVariableDeclarations(cleanArgs, result.varNames);
        }

        debugPrint("@ 解析结果: 提示='%s', 变量=%s", result.prompt, result.varNames);
        return result;
    }

    /**
     * 解析变量声明 - 修复版
     */
    private void parseVariableDeclarations(String expr, List<String> varNames) {
        // 使用正则表达式匹配 var + 变量名的模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("var\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        java.util.regex.Matcher matcher = pattern.matcher(expr);

        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!varNames.contains(varName)) {
                varNames.add(varName);
                debugPrint("@ 发现变量: " + varName);
            }
        }

        // 如果没有找到变量，尝试简单分割
        if (varNames.isEmpty()) {
            String[] parts = expr.split("\\+");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("var ")) {
                    String varName = part.substring(4).trim();
                    varNames.add(varName);
                }
            }
        }
    }

    /**
     * 输入解析结果容器
     */
    private static class InputParseResult {
        String prompt = "";
        List<String> varNames = new ArrayList<>();
    }

    private int calculateIndent(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    private String extractCondition(String line) {
        int start = line.indexOf('(');
        int end = line.lastIndexOf(')');
        return (start != -1 && end != -1) ? line.substring(start + 1, end) : "";
    }

    private Object[] parseArguments(String argsStr) {
        if (argsStr.trim().isEmpty()) return new Object[0];

        List<Object> args = new ArrayList<>();
        String[] parts = splitArgs(argsStr);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            try {
                // 尝试作为表达式求值
                Object value = evaluateExpression(part, -1);
                args.add(value);
            } catch (Exception e) {
                // 如果求值失败，作为字符串处理
                if (part.startsWith("\"") && part.endsWith("\"")) {
                    args.add(part.substring(1, part.length() - 1));
                } else {
                    args.add(part);
                }
            }
        }
        return args.toArray();
    }

    private String[] splitArgs(String argsStr) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : argsStr.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }

        return parts.toArray(new String[0]);
    }

    // 内部类
    private static class LoopContext {
        int remainingIterations;
        int startPC;
        boolean infiniteLoop; // 新增：标记是否为无限循环

        LoopContext(int iterations, int startPC) {
            this.remainingIterations = iterations;
            this.startPC = startPC;
            this.infiniteLoop = false;
        }

        LoopContext(int iterations, int startPC, boolean infiniteLoop) {
            this.remainingIterations = iterations;
            this.startPC = startPC;
            this.infiniteLoop = infiniteLoop;
        }
    }

    private static class Instruction {
        OpCode opCode;
        String operand;
        int lineNumber;
        int indentLevel;
        Object extraData;

        Instruction(OpCode opCode, String operand, int lineNumber, int indentLevel) {
            this.opCode = opCode;
            this.operand = operand;
            this.lineNumber = lineNumber;
            this.indentLevel = indentLevel;
        }

        Instruction(OpCode opCode, String operand, int lineNumber, int indentLevel, Object extraData) {
            this.opCode = opCode;
            this.operand = operand;
            this.lineNumber = lineNumber;
            this.indentLevel = indentLevel;
            this.extraData = extraData;
        }
    }

    private enum OpCode {
        IMPORT, VAR_DECL, VAR_ASSIGN, LOOP_START, END_BLOCK, CALL, GIVE, DO, CHANGE
    }

    // 块类型枚举
    private enum BlockType {
        LOOP
    }

    // 块上下文
    private static class BlockContext {
        BlockType type;
        int indent;

        BlockContext(BlockType type, int indent) {
            this.type = type;
            this.indent = indent;
        }
    }

    // Give语句数据结构
    private static class GiveInstructionData {
        String className;
        String arguments;

        GiveInstructionData(String className, String arguments) {
            this.className = className;
            this.arguments = arguments;
        }
    }

    // Do语句数据结构
    private static class DoInstructionData {
        String className;
        String methodName;
        String arguments;

        DoInstructionData(String className, String methodName, String arguments) {
            this.className = className;
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }

    // Give结果类
    public static class GiveResult {
        private String targetClass;
        private Map<String, Object> variables;

        public GiveResult(String targetClass, Map<String, Object> variables) {
            this.targetClass = targetClass;
            this.variables = variables;
        }

        public String getTargetClass() {
            return targetClass;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }

        @Override
        public String toString() {
            return "GiveResult{targetClass='" + targetClass + "', variables=" + variables + '}';
        }
    }

    /**
     * 表达式求值入口（带行号）
     */
    Object evaluateExpression(String expr, int lineNumber) throws Exception{
        expr = (expr == null ? "" : expr.trim());

        // 移除行内注释（# 之后的内容）
        int commentIndex = expr.indexOf('#');
        if (commentIndex != -1) {
            expr = expr.substring(0, commentIndex).trim();
        }

        if (expr.isEmpty()) {
            return null;
        }

        // 处理运算符表达式 - 优先处理
        if (containsOperators(expr)) {
            return evaluateOperatorExpression(expr, lineNumber);
        }

        // 处理字符串字面量 - 使用新的完整解析（传递行号）
        if (expr.startsWith("\"")) {
            ParseResult result = parseCompleteStringLiteral(expr, lineNumber);
            if (result.remaining.isEmpty()) {
                return result.value;
            } else {
                // 如果还有剩余内容，作为表达式继续处理
                return evaluateOperatorExpression("\"" + result.value + "\"" + result.remaining, lineNumber);
            }
        }

        // 处理布尔值
        if (expr.equals("true")) return true;
        if (expr.equals("false")) return false;

        // 处理数字
        try {
            if (expr.contains(".")) {
                return Double.parseDouble(expr);
            } else {
                return Integer.parseInt(expr);
            }
        } catch (NumberFormatException e) {
            // 不是数字，继续处理
        }

        // 处理变量引用
        if (variables.containsKey(expr)) {
            Object val = variables.get(expr);
            if (val == UNINITIALIZED) {
                throw new NullTokenException(expr, "variable usage");
            }
            return val;
        }

        // 如果都不是，返回原始字符串（可能是未引用的字符串）
        return expr;
    }

    /**
     * 解析完整的字符串字面量，返回值和剩余部分（带行号）
     */
    private ParseResult parseCompleteStringLiteral(String expr, int lineNumber) {
        if (!expr.startsWith("\"")) {
            if (lineNumber != -1) {
                throw NotGrammarException.invalidSymbolUsage("string literal", "Missing opening quote", lineNumber);
            } else {
                throw NotGrammarException.invalidSymbolUsage("string literal", "Missing opening quote");
            }
        }

        StringBuilder result = new StringBuilder();
        boolean inEscape = false;
        int i = 1; // 跳过开头的双引号

        for (; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inEscape) {
                // 处理转义字符
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 't': result.append('\t'); break;
                    case 'r': result.append('\r'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    default: result.append('\\').append(c); // 保留未知转义
                }
                inEscape = false;
            } else if (c == '\\') {
                // 遇到转义字符
                inEscape = true;
            } else if (c == '"') {
                // 遇到结束引号
                i++; // 移动到下一个字符
                break;
            } else {
                // 普通字符
                result.append(c);
            }
        }

        // 检查是否在转义序列中结束
        if (inEscape) {
            if (lineNumber != -1) {
                throw NotGrammarException.invalidSymbolUsage("escape sequence", "Unterminated escape sequence", lineNumber);
            } else {
                throw NotGrammarException.invalidSymbolUsage("escape sequence", "Unterminated escape sequence");
            }
        }

        String remaining = (i < expr.length()) ? expr.substring(i) : "";
        return new ParseResult(result.toString(), remaining.trim());
    }

    /**
     * 解析结果容器
     */
    private static class ParseResult {
        final String value;
        final String remaining;

        ParseResult(String value, String remaining) {
            this.value = value;
            this.remaining = remaining;
        }
    }

    /**
     * 解析字符串字面量，支持转义字符
     */
    private String parseStringLiteral(String expr) {
        // 检查是否以双引号开始
        if (!expr.startsWith("\"")) {
            return expr;
        }

        StringBuilder result = new StringBuilder();
        boolean inEscape = false;

        // 从第一个字符开始遍历（跳过开头的双引号）
        for (int i = 1; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inEscape) {
                // 处理转义字符
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 't': result.append('\t'); break;
                    case 'r': result.append('\r'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    default: result.append(c); // 未知转义序列，按原样处理
                }
                inEscape = false;
            } else if (c == '\\') {
                // 遇到转义字符
                inEscape = true;
            } else if (c == '"') {
                // 遇到结束引号，停止解析
                // 检查后面是否还有内容（可能是字符串拼接）
                String remaining = expr.substring(i + 1).trim();
                if (!remaining.isEmpty() && remaining.startsWith("+")) {
                    // 有字符串拼接，我们需要特殊处理
                    // 这里暂时返回已解析的部分，剩余部分由表达式求值器处理
//                    System.out.println("[Debug] String literal ends but has concatenation: " + remaining);
                }
                break;
            } else {
                // 普通字符
                result.append(c);
            }
        }

        return result.toString();
    }

    private boolean containsOperators(String expr) {
        String[] operators = {"+", "-", "*", "/", "%", ">", "<", "==", "!=", ">=", "<=", "&&", "||", "++"};

        boolean inString = false;
        boolean inEscape = false;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inEscape) {
                inEscape = false;
                continue;
            }

            if (c == '\\') {
                inEscape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                for (String op : operators) {
                    if (expr.startsWith(op, i)) {
                        // 确保运算符不在引号内
                        if (!isInQuotes(expr, i)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isInQuotes(String str, int index) {
        boolean inQuotes = false;
        for (int i = 0; i < index; i++) {
            if (str.charAt(i) == '"') {
                inQuotes = !inQuotes;
            }
        }
        return inQuotes;
    }

    /**
     * 数字拼接运算：将两个数字拼接成一个数字
     * 例如：12++14 -> 1214（数字，不是字符串）
     */
    private Object concatenateNumbers(Object left, Object right) {
        // 处理自增情况：10++ -> 11
        if (right == null || (right instanceof String && ((String) right).isEmpty())) {
            if (left instanceof Integer) {
                return (Integer) left + 1;
            } else if (left instanceof Double) {
                return (Double) left + 1.0;
            } else {
                throw new NotGrammarException("Cannot increment non-numeric value: " + left);
            }
        }

        // 处理自增情况：++10 -> 11（前缀自增）
        if (left == null || (left instanceof String && ((String) left).isEmpty())) {
            if (right instanceof Integer) {
                return (Integer) right + 1;
            } else if (right instanceof Double) {
                return (Double) right + 1.0;
            } else {
                throw new NotGrammarException("Cannot increment non-numeric value: " + right);
            }
        }

        // 正常数字拼接
        String leftStr = String.valueOf(left);
        String rightStr = String.valueOf(right);

        // 移除可能的小数点和后缀，确保是整数拼接
        leftStr = leftStr.replaceAll("\\.0*$", "").replaceAll("\\.", "");
        rightStr = rightStr.replaceAll("\\.0*$", "").replaceAll("\\.", "");

        try {
            return Integer.parseInt(leftStr + rightStr);
        } catch (NumberFormatException e) {
            // 如果数字太大，尝试返回长整型
            try {
                return Long.parseLong(leftStr + rightStr);
            } catch (NumberFormatException e2) {
                throw new NotGrammarException("Number concatenation result is too large: " + leftStr + rightStr);
            }
        }
    }


    /**
     * 增强的运算符表达式求值 - 先分词再计算
     */
    private Object evaluateOperatorExpression(String expr, int lineNumber) throws Exception {
        expr = expr.trim();

        // 首先将表达式分解为令牌
        List<Object> tokens = tokenizeExpression(expr, lineNumber);

        // 如果只有一个令牌，直接返回
        if (tokens.size() == 1) {
            return ensureEvaluated(tokens.get(0), lineNumber);
        }

        // 处理运算符表达式
        return evaluateTokens(tokens, lineNumber);
    }

    /**
     * 增强的令牌化方法 - 识别 var 关键字
     */
    private List<Object> tokenizeExpression(String expr, int lineNumber) throws Exception {
        List<Object> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean inEscape = false;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inEscape) {
                current.append('\\').append(c);
                inEscape = false;
                continue;
            }

            if (c == '\\') {
                inEscape = true;
                continue;
            }

            if (c == '"') {
                if (inString) {
                    // 结束字符串
                    current.append(c);
                    tokens.add(evaluateExpression(current.toString(), lineNumber));
                    current.setLength(0);
                    inString = false;
                } else {
                    // 开始字符串
                    if (current.length() > 0) {
                        // 处理当前累积的内容
                        processNonStringContent(current.toString(), tokens, lineNumber);
                        current.setLength(0);
                    }
                    current.append(c);
                    inString = true;
                }
            } else if (!inString && Character.isWhitespace(c)) {
                // 空格分隔符
                if (current.length() > 0) {
                    processNonStringContent(current.toString(), tokens, lineNumber);
                    current.setLength(0);
                }
            } else if (!inString && (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
                    c == '>' || c == '<' || c == '=' || c == '!' || c == '&' || c == '|')) {
                // 运算符
                if (current.length() > 0) {
                    processNonStringContent(current.toString(), tokens, lineNumber);
                    current.setLength(0);
                }

                // 检查多字符运算符
                if (i + 1 < expr.length()) {
                    char next = expr.charAt(i + 1);
                    String twoCharOp = String.valueOf(c) + next;
                    if (twoCharOp.equals("++") || twoCharOp.equals("--") || twoCharOp.equals("==") ||
                            twoCharOp.equals("!=") || twoCharOp.equals(">=") || twoCharOp.equals("<=") ||
                            twoCharOp.equals("&&") || twoCharOp.equals("||")) {
                        tokens.add(twoCharOp);
                        i++; // 跳过下一个字符
                        continue;
                    }
                }

                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }

        // 处理最后一个令牌
        if (current.length() > 0) {
            processNonStringContent(current.toString(), tokens, lineNumber);
        }

        return tokens;
    }

    /**
     * 处理非字符串内容，识别 var 关键字
     */
    private void processNonStringContent(String content, List<Object> tokens, int lineNumber) throws Exception {
        content = content.trim();
        if (content.isEmpty()) return;

        // 检查是否是 var 声明
        if (content.startsWith("var ")) {
            tokens.add(new VarDeclaration(content.substring(4).trim()));
        } else {
            // 普通内容，进行表达式求值
            tokens.add(evaluateExpression(content, lineNumber));
        }
    }

    /**
     * 变量声明类
     */
    private static class VarDeclaration {
        final String variableName;

        VarDeclaration(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public String toString() {
            return "var " + variableName;
        }
    }


    /**
     * 计算令牌列表的值（带行号）
     */
    private Object evaluateTokens(List<Object> tokens, int lineNumber) throws Exception {
        if (tokens.isEmpty()) return null;
        if (tokens.size() == 1) return ensureEvaluated(tokens.get(0), lineNumber);

        // 先处理数字拼接 (++)
        for (int i = 0; i < tokens.size() - 1; i++) {
            if ("++".equals(tokens.get(i))) {
                Object left = (i > 0) ? ensureEvaluated(tokens.get(i - 1), lineNumber) : null;
                Object right = (i < tokens.size() - 1) ? ensureEvaluated(tokens.get(i + 1), lineNumber) : null;
                Object result = concatenateNumbers(left, right);

                // 替换这三个令牌为结果
                List<Object> newTokens = new ArrayList<>();
                if (i > 0) newTokens.addAll(tokens.subList(0, i - 1));
                newTokens.add(result);
                if (i < tokens.size() - 2) newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                return evaluateTokens(newTokens, lineNumber);
            }
        }

        // 处理乘除模运算符
        for (int i = 1; i < tokens.size() - 1; i++) {
            Object token = tokens.get(i);
            if (token instanceof String) {
                String op = (String) token;
                if ("*".equals(op) || "/".equals(op) || "%".equals(op)) {
                    Object left = ensureEvaluated(tokens.get(i - 1), lineNumber);
                    Object right = ensureEvaluated(tokens.get(i + 1), lineNumber);
                    Object result = calculateValues(left, right, op, lineNumber);

                    List<Object> newTokens = new ArrayList<>();
                    newTokens.addAll(tokens.subList(0, i - 1));
                    newTokens.add(result);
                    newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                    return evaluateTokens(newTokens, lineNumber);
                }
            }
        }

        // 处理加减运算符
        for (int i = 1; i < tokens.size() - 1; i++) {
            Object token = tokens.get(i);
            if (token instanceof String) {
                String op = (String) token;
                if ("+".equals(op) || "-".equals(op)) {
                    Object left = ensureEvaluated(tokens.get(i - 1), lineNumber);
                    Object right = ensureEvaluated(tokens.get(i + 1), lineNumber);
                    Object result = calculateValues(left, right, op, lineNumber);

                    List<Object> newTokens = new ArrayList<>();
                    newTokens.addAll(tokens.subList(0, i - 1));
                    newTokens.add(result);
                    newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                    return evaluateTokens(newTokens, lineNumber);
                }
            }
        }

        // 处理关系运算符
        for (int i = 1; i < tokens.size() - 1; i++) {
            Object token = tokens.get(i);
            if (token instanceof String) {
                String op = (String) token;
                if (">".equals(op) || "<".equals(op) || ">=".equals(op) || "<=".equals(op)) {
                    Object left = ensureEvaluated(tokens.get(i - 1), lineNumber);
                    Object right = ensureEvaluated(tokens.get(i + 1), lineNumber);
                    Object result = compareValues(left, right, op, lineNumber);

                    List<Object> newTokens = new ArrayList<>();
                    newTokens.addAll(tokens.subList(0, i - 1));
                    newTokens.add(result);
                    newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                    return evaluateTokens(newTokens, lineNumber);
                }
            }
        }

        // 处理相等运算符
        for (int i = 1; i < tokens.size() - 1; i++) {
            Object token = tokens.get(i);
            if (token instanceof String) {
                String op = (String) token;
                if ("==".equals(op) || "!=".equals(op)) {
                    Object left = ensureEvaluated(tokens.get(i - 1), lineNumber);
                    Object right = ensureEvaluated(tokens.get(i + 1), lineNumber);
                    Object result = compareValues(left, right, op, lineNumber);

                    List<Object> newTokens = new ArrayList<>();
                    newTokens.addAll(tokens.subList(0, i - 1));
                    newTokens.add(result);
                    newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                    return evaluateTokens(newTokens, lineNumber);
                }
            }
        }

        // 处理逻辑运算符
        for (int i = 1; i < tokens.size() - 1; i++) {
            Object token = tokens.get(i);
            if (token instanceof String) {
                String op = (String) token;
                if ("&&".equals(op) || "||".equals(op)) {
                    Object left = ensureEvaluated(tokens.get(i - 1), lineNumber);
                    Object right = ensureEvaluated(tokens.get(i + 1), lineNumber);
                    boolean leftBool = toBoolean(left);
                    boolean rightBool = toBoolean(right);
                    Object result = "&&".equals(op) ? (leftBool && rightBool) : (leftBool || rightBool);

                    List<Object> newTokens = new ArrayList<>();
                    newTokens.addAll(tokens.subList(0, i - 1));
                    newTokens.add(result);
                    newTokens.addAll(tokens.subList(i + 2, tokens.size()));

                    return evaluateTokens(newTokens, lineNumber);
                }
            }
        }

        // 如果没有运算符，返回第一个令牌
        return ensureEvaluated(tokens.get(0), lineNumber);
    }

    /**
     * 确保对象已经被求值（带行号）
     */
    private Object ensureEvaluated(Object obj, int lineNumber) throws Exception {
        if (obj instanceof String) {
            return evaluateExpression((String) obj, lineNumber);
        }
        if (obj instanceof StringRepeat) {
            return obj.toString();
        }
        return obj;
    }

    private int findOperator(String expr, String op) {
        boolean inString = false;
        boolean inEscape = false;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inEscape) {
                inEscape = false;
                continue;
            }

            if (c == '\\') {
                inEscape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (expr.startsWith(op, i)) {
                    // 检查是否是连续多个+的情况
                    if ("+".equals(op)) {
                        // 如果是+，检查是否是++的一部分
                        if (i > 0 && expr.charAt(i - 1) == '+') {
                            continue;
                        }
                        if (i < expr.length() - 1 && expr.charAt(i + 1) == '+') {
                            continue;
                        }
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 字符串重复的内部表示（支持链式重复）
     */
    private static class StringRepeat {
        final String base;
        final int count;

        StringRepeat(String base, int count) {
            this.base = base == null ? "null" : base;
            this.count = Math.max(0, count);
        }

        @Override
        public String toString() {
            if (count <= 0) return "";
            StringBuilder sb = new StringBuilder(base.length() * count);
            for (int i = 0; i < count; i++) sb.append(base);
            return sb.toString();
        }
    }

    private Object calculateValues(Object left, Object right, String op, int lineNumber) {
        try {
            // 处理加法运算符 +
            if ("+".equals(op)) {
                // 布尔值相加报错
                if (left instanceof Boolean && right instanceof Boolean) {
                    throw new NotGrammarException("Cannot add two boolean values: " + left + " + " + right);
                }

                // 字符串拼接：任意一边是字符串，或者 StringRepeat，或者数字+字符串，或者布尔值+字符串
                if (left instanceof String || right instanceof String || left instanceof StringRepeat || right instanceof StringRepeat) {
                    return String.valueOf(left) + String.valueOf(right);
                }

                // 数字相加
                if (left instanceof Number && right instanceof Number) {
                    // 自动类型转换：如果任意一边是double，都转为double
                    if (left instanceof Double || right instanceof Double) {
                        double l = toDouble(left);
                        double r = toDouble(right);
                        return l + r;
                    } else {
                        // 整数运算
                        int l = toInt(left);
                        int r = toInt(right);
                        return l + r;
                    }
                }

                // 布尔值+数字：将布尔值转换为数字（true=1, false=0）
                if (left instanceof Boolean && right instanceof Number) {
                    int l = (Boolean) left ? 1 : 0;
                    double r = toDouble(right);
                    if (right instanceof Double) {
                        return l + r;
                    } else {
                        return l + toInt(right);
                    }
                }

                if (left instanceof Number && right instanceof Boolean) {
                    double l = toDouble(left);
                    int r = (Boolean) right ? 1 : 0;
                    if (left instanceof Double) {
                        return l + r;
                    } else {
                        return toInt(left) + r;
                    }
                }

                throw new NotGrammarException("Unsupported operand types for +: " +
                        (left != null ? left.getClass().getSimpleName() : "null") + " and " + (right != null ? right.getClass().getSimpleName() : "null"));
            }

            // 对 / 或 % 操作，如果任一操作数是字符串或StringRepeat，抛出 MathError（不能对字符串做除法或取模）
            if ("/".equals(op) || "%".equals(op)) {
                if (left instanceof String || right instanceof String || left instanceof StringRepeat || right instanceof StringRepeat) {
                    throw new MathError("Cannot perform '" + op + "' on non-numeric operand(s)");
                }
                double r = toDouble(right);
                if (r == 0) {
                    throw MathError.divisionByZero();
                }
            }

            // 处理乘法运算符 - 包含字符串重复支持
            if ("*".equals(op)) {
                // 字符串或 StringRepeat 与 数字 的重复语义（支持链式）
                if (left instanceof String && right instanceof Number) {
                    return new StringRepeat((String) left, toInt(right));
                }
                if (left instanceof Number && right instanceof String) {
                    return new StringRepeat((String) right, toInt(left));
                }
                if (left instanceof StringRepeat && right instanceof Number) {
                    StringRepeat sr = (StringRepeat) left;
                    return new StringRepeat(sr.base, sr.count * toInt(right));
                }
                if (left instanceof Number && right instanceof StringRepeat) {
                    StringRepeat sr = (StringRepeat) right;
                    return new StringRepeat(sr.base, toInt(left) * sr.count);
                }

                // 如果任一是字符串但不满足上面模式，则错误
                if (left instanceof String || right instanceof String || left instanceof StringRepeat || right instanceof StringRepeat) {
                    throw new MathError("Cannot perform '*' between non-numeric operands except for string repetition");
                }

                // 数字乘法（和 / % - 的处理类似）
                if (left instanceof Double || right instanceof Double) {
                    double l = toDouble(left);
                    double r = toDouble(right);
                    return l * r;
                } else {
                    int l = toInt(left);
                    int r = toInt(right);
                    return l * r;
                }
            }

            // 处理其他运算符：-, /, %（/,% 的零检查上面已经处理）
            // 自动类型转换：如果任意一边是double，都转为double
            if (left instanceof Double || right instanceof Double) {
                double l = toDouble(left);
                double r = toDouble(right);

                switch (op) {
                    case "-":
                        return l - r;
                    case "/":
                        return r != 0 ? l / r : 0;
                    case "%":
                        return r != 0 ? l % r : 0;
                }
            } else {
                // 整数运算
                int l = toInt(left);
                int r = toInt(right);

                switch (op) {
                    case "-":
                        return l - r;
                    case "/":
                        return r != 0 ? l / r : 0;
                    case "%":
                        return r != 0 ? l % r : 0;
                }
            }
        } catch (ArithmeticException e) {
            throw MathError.overflow(op);
        }

        throw new NotGrammarException("Unsupported operator: " + op);
    }

    private boolean compareValues(Object left, Object right, String op, int lineNumber) {
        // 字符串比较
        if (left instanceof String && right instanceof String) {
            int result = ((String) left).compareTo((String) right);
            switch (op) {
                case "==": return result == 0;
                case "!=": return result != 0;
                case ">": return result > 0;
                case "<": return result < 0;
                case ">=": return result >= 0;
                case "<=": return result <= 0;
            }
        }

        // 数字比较
        if (left instanceof Number && right instanceof Number) {
            double l = toDouble(left);
            double r = toDouble(right);

            switch (op) {
                case "==": return l == r;
                case "!=": return l != r;
                case ">": return l > r;
                case "<": return l < r;
                case ">=": return l >= r;
                case "<=": return l <= r;
            }
        }

        // 布尔值比较
        if (left instanceof Boolean && right instanceof Boolean) {
            boolean l = (Boolean) left;
            boolean r = (Boolean) right;

            switch (op) {
                case "==": return l == r;
                case "!=": return l != r;
                default: throw new NotGrammarException("Boolean values only support == and != operators");
            }
        }

        throw new NotGrammarException("Cannot compare values of different or unsupported types: " + left + " and " + right);
    }

    private double toDouble(Object obj) {
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                throw new MathError("Cannot convert string to number: " + obj);
            }
        }
        throw new MathError("Cannot convert to double: " + obj);
    }

    private int toInt(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Double) return ((Double) obj).intValue();
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                throw new MathError("Cannot convert string to int: " + obj);
            }
        }
        if (obj instanceof Boolean) return (Boolean) obj ? 1 : 0;
        throw new MathError("Cannot convert to int: " + obj);
    }

    private boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
        if (obj instanceof String) return !((String) obj).isEmpty();
        return obj != null;
    }

    private void handleLoopStart(Instruction instr) throws Exception {
        Object conditionValue = evaluateExpression(instr.operand, instr.lineNumber);
        int loopCount;
        boolean infiniteLoop = false;

        if (conditionValue instanceof Boolean) {
            // 布尔值处理
            if ((Boolean) conditionValue) {
                infiniteLoop = true; // true 表示无限循环
                loopCount = 1; // 初始设置为1，但实际会无限循环
            } else {
                loopCount = 0; // false 表示不循环
            }
        } else if (conditionValue instanceof Number) {
            // 数字处理
            loopCount = toInt(conditionValue);
            if (loopCount < 0) {
                loopCount = 0; // 负数视为0次循环
            }
        } else {
            // 其他类型尝试转换为布尔值
            boolean boolValue = toBoolean(conditionValue);
            if (boolValue) {
                infiniteLoop = true;
                loopCount = 1;
            } else {
                loopCount = 0;
            }
        }

        LoopContext context = new LoopContext(loopCount, pc, infiniteLoop);
        loopStack.push(context);

        debugPrint("@ Loop condition: %s -> count: %d, infinite: %s",
                instr.operand, loopCount, infiniteLoop);

        if (loopCount <= 0 && !infiniteLoop) {
            skipLoopBody();
        }
    }

    private void handleEndBlock() {
        if (!loopStack.isEmpty()) {
            LoopContext loop = loopStack.peek();

            if (loop.infiniteLoop) {
                // 无限循环：永远跳回循环开始
                pc = loop.startPC;
            } else {
                // 有限循环：减少计数并判断是否继续
                loop.remainingIterations--;

                if (loop.remainingIterations > 0) {
                    pc = loop.startPC;
                } else {
                    loopStack.pop();
                }
            }
        }
    }

    private void skipLoopBody() {
        int depth = 1;
        while (pc < instructions.size() - 1) {
            pc++;
            Instruction instr = instructions.get(pc);
            if (instr.opCode == OpCode.LOOP_START) depth++;
            else if (instr.opCode == OpCode.END_BLOCK) {
                depth--;
                if (depth == 0) break;
            }
        }
    }

    // 工具方法：规范化类型名
    private String normalizeTypeName(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        switch (s) {
            case "int": case "integer": return "int";
            case "double": case "float": return "double";
            case "bool": case "boolean": return "boolean";
            case "string": case "str": return "string";
            default: return s;
        }
    }

    // 判断对象的实际类型是否精确匹配目标类型（不允许隐式转换）
    private boolean isExactTypeMatchToType(Object val, String typeName) {
        if (val == null) return false;
        switch (typeName) {
            case "int": return val instanceof Integer;
            case "double": return val instanceof Double;
            case "boolean": return val instanceof Boolean;
            case "string": return val instanceof String;
            default: return true;
        }
    }

    // 工具：检测是否与类型严格匹配（在 change 或 assignment 前使用）
    private boolean isExactTypeMatch(Object val, Class<?> t) {
        if (val == null) return !t.isPrimitive();
        if (t.isInstance(val)) return true;
        if (t.isPrimitive()) {
            Class<?> wrapper = getWrapperClass(t);
            return wrapper.isInstance(val);
        }
        return false;
    }

    private Class<?> getWrapperClass(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == double.class) return Double.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == char.class) return Character.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        return primitive;
    }
}