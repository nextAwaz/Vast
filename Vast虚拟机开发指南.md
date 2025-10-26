# Vast 虚拟机开发者文档

# 前言

Vast 是一种轻量级脚本语言，专为简单性和性能而设计。本语言及其虚拟机实现采用 BSD-3-Clause 开源协议发布，允许在遵守协议条款的前提下自由使用、修改和分发。

## 协议声明

```
Copyright (c) [年份] [版权持有人姓名]
版权所有。

在满足以下条件的情况下，允许以源代码和二进制形式重新分发和使用（无论是否经过修改）：

1. 重新分发源代码必须保留上述版权声明、此条件列表和以下免责声明。
2. 以二进制形式重新分发必须在文档和/或其他提供的材料中复制上述版权声明、此条件列表和以下免责声明。
3. 未经事先明确书面许可，不得使用版权持有人的姓名或其贡献者的姓名为本软件的衍生产品背书或推广。

本软件由版权所有人和贡献者"按原样"提供，不提供任何明示或暗示的担保，包括但不限于对适销性和特定用途适用性的暗示担保。在任何情况下，无论是在合同、严格责任或侵权行为（包括疏忽或其他）中，版权持有人或贡献者均不对因使用本软件而以任何方式引起的任何直接、间接、偶然、特殊、惩戒性或后果性损害（包括但不限于采购替代商品或服务；使用、数据或利润损失；或业务中断）承担责任，即使已被告知可能发生此类损害。
```

## 语言哲学

Vast 语言的设计遵循"大道至简"的原则，旨在提供简洁而强大的脚本功能。其核心特性包括：

- **简洁语法**：直观的语法设计，降低学习曲线
- **动态类型系统**：支持可选类型提示的灵活类型系统
- **可扩展架构**：模块化设计，易于扩展和维护
- **高性能执行**：基于AST的编译执行，兼顾解释执行的灵活性

本开发文档面向Vast虚拟机的开发者，提供完整的技术参考和开发指南。无论您是希望深入了解虚拟机内部机制，还是计划为Vast生态系统贡献代码，本文档都将为您提供必要的技术指导。

## 文档结构

本文档分为五个主要部分：

1. **语法参考与示例** - 完整的语言语法规范和用法示例
2. **内部原理详解** - 虚拟机核心组件的深入技术分析
3. **外部库开发规范** - 扩展库的开发标准和集成方法
4. **代码修改指南** - 系统修改的具体步骤和最佳实践
5. **架构演进原则** - 系统维护和功能演进的指导原则




# 第一部分：介绍和完整的语法示例

## 1.1 项目概述

Vast 是一个基于 Java 实现的轻量级脚本语言虚拟机，采用抽象语法树（AST）架构设计。该项目旨在提供一个可扩展、高性能的脚本执行环境，支持动态类型系统、可选静态类型提示、外置库扩展等特性。

**核心设计理念**：
- 简洁直观的语法设计
- 可扩展的架构支持
- 严格的类型检查系统
- 完整的错误处理机制
- 模块化的外置库支持

## 1.2 完整的语法规范

### 1.2.1 基础语法结构

```vast
# 变量声明和赋值
var x = 10
var (int) y = 20                    # 带类型提示的变量声明
string name = "Vast Script"         # 类型前置声明语法
var result = x + y

# 导入语句
imp Sys
imp Time
imp com.example.CustomClass

# 循环结构
loop(5):
    var i = 0
    use(Sys.printl("Iteration: " + i))
    i = i + 1

loop(true):
    use(Sys.printl("Infinite loop"))
    use(Time.wait(1000))
    # 可通过条件跳出

# 使用方法调用
use(Sys.printl("Hello World"))
use(Time.wait(500))
use(Array.create(10))

# 变量交换
swap(a, b)
```

#### 1.2.2 数据类型系统

```vast
# 基本数据类型
var intValue = 42                   # 整数
var doubleValue = 3.14159           # 浮点数
var boolValue = true                # 布尔值
var stringValue = "Hello Vast"      # 字符串
var nullValue = null                # 空值

# 类型提示声明
var (int) typedInt = 100
var (string) typedString = "Typed"
var (bool) flag = true
var (double) precision = 2.71828

# 数组支持（通过内置库）
var arr = Array.create(5)
Array.set(arr, 0, "first")
Array.set(arr, 1, 42)
```

### 1.2.3 运算符系统

```vast
# 算术运算符
var a = 10 + 5      # 加法
var b = 20 - 3      # 减法  
var c = 6 * 7       # 乘法
var d = 15 / 3      # 除法
var e = 2 ** 3      # 幂运算 (8)
var f = 17 // 3     # 整数除法 (5)
var g = 17 % 3      # 取模 (2)

# 比较运算符
var eq = (a == b)   # 等于
var ne = (a != b)   # 不等于
var gt = (a > b)    # 大于
var lt = (a < b)    # 小于
var ge = (a >= b)   # 大于等于
var le = (a <= b)   # 小于等于

# 逻辑运算符
var and = (true && false)   # 逻辑与
var or = (true || false)    # 逻辑或
var not = !true            # 逻辑非

# 按位运算符
var bitAnd = 5 & 3         # 按位与 (1)
var bitOr = 5 | 3          # 按位或 (7)
var bitXor = 5 ^ 3         # 按位异或 (6)
var bitNot = ~5            # 按位取反

# 特殊运算符
var concat = 12 ++ 34      # 数字连接 (1234)
var inc = a++              # 自增运算
```

### 1.2.4 分数表达式

```vast
# 单步分数（临时分数）
var tempFraction = $(3/4)
var fromInt = $(5)         # 5/1
var fromDouble = $(3.14)   # 近似分数

# 永久分数
var permFraction = $$(22/7)
var complexFrac = $$($(1/2) + $(1/3))  # 分数运算

# 分数运算
var fracA = $(1/3)
var fracB = $(1/6)
var sum = fracA + fracB    # 自动分数运算 (1/2)
var product = fracA * fracB # (1/18)
```

### 1.2.5 方法调用系统

```vast
# 完整类名调用
use(Sys.printl("Hello World"))
use(Time.wait(1000))
use(DataType.strToUpper("hello"))

# 省略类名调用（当方法名唯一时）
use(printl("Simplified call"))      # 如果 printl 在多个类中存在会报错
use(wait(500))                      # 指代不明异常

# 链式方法调用（通过表达式）
var upper = DataType.strToUpper("hello")
var length = DataType.strLength(upper)
```

### 1.2.6 类型转换系统

```vast
# 显式类型转换
var strToInt = "123"(int)           # 字符串转整数
var intToDouble = 42(double)        # 整数转浮点数
var boolToInt = true(int)           # 布尔转整数 (1)

# 隐式类型转换（带警告）
var (int) implicitlyTyped = 3.14    # 警告：隐式类型转换

# 复杂类型转换
var str = "3.14159"
var asDouble = str(double)          # 字符串转浮点数
var asInt = asDouble(int)           # 浮点数转整数（截断）
```

### 1.2.7 控制流结构

```vast
# 条件循环
var count = 0
loop(count < 5):
    use(Sys.printl("Count: " + count))
    count = count + 1

# 固定次数循环
loop(3):
    use(Sys.printl("Fixed iteration"))

# 布尔条件循环
var running = true
loop(running):
    use(Sys.printl("Running..."))
    # 可通过设置 running = false 来退出
```

### 1.2.8 输入输出操作

```vast
# 基本输出
use(Sys.print("Hello "))           # 不换行输出
use(Sys.printl("World"))           # 换行输出
use(Sys.printl())                  # 输出空行

# 格式化输出
use(Sys.printf("Value: {0}", 42))
use(Sys.printlf("Name: {0}, Age: {1}", "Alice", 25))

# 输入操作
var name = Sys.input("Enter name: ")
var values = Sys.multiValueInput("Enter x y: ", 2)
var x = values[0]
var y = values[1]
```

## 1.3 完整的程序示例

### 1.3.1 基础计算程序

```vast
# 简单计算器示例
imp Sys
imp DataType

var a = 15
var b = 4

use(Sys.printl("Basic Calculator"))
use(Sys.printl("a = " + a + ", b = " + b))

# 算术运算
var sum = a + b
var difference = a - b
var product = a * b
var quotient = a / b
var remainder = a % b
var power = a ** b

use(Sys.printl("Sum: " + sum))
use(Sys.printl("Difference: " + difference))
use(Sys.printl("Product: " + product))
use(Sys.printl("Quotient: " + quotient))
use(Sys.printl("Remainder: " + remainder))
use(Sys.printl("Power: " + power))

# 分数运算
var fraction1 = $(3/4)
var fraction2 = $(2/5)
var fracSum = fraction1 + fraction2

use(Sys.printl("Fraction Sum: " + fracSum))
```

### 1.3.2 循环和数组操作

```vast
# 数组和循环示例
imp Sys
imp Array
imp DataType

# 创建和初始化数组
var size = 5
var numbers = Array.create(size)

use(Sys.printl("Initializing array..."))
loop(size):
    Array.set(numbers, $0, $0 * 2)  # $0 表示循环索引

# 遍历数组
use(Sys.printl("Array contents:"))
loop(size):
    var value = Array.get(numbers, $0)
    use(Sys.printl("Index " + $0 + ": " + value))

# 数组操作
var contains = Array.contains(numbers, 6)
use(Sys.printl("Array contains 6: " + contains))
```

### 1.3.3 类型系统和错误处理

```vast
# 类型系统和错误处理示例
imp Sys
imp DataType

# 严格类型声明
var (int) integerVar = 100
var (string) stringVar = "Hello"
var (bool) booleanVar = true

use(Sys.printl("Typed variables:"))
use(Sys.printl("Integer: " + integerVar))
use(Sys.printl("String: " + stringVar))
use(Sys.printl("Boolean: " + booleanVar))

# 类型转换示例
var numberString = "12345"
var asInteger = numberString(int)
var asDouble = numberString(double)

use(Sys.printl("Type conversions:"))
use(Sys.printl("String '" + numberString + "' as int: " + asInteger))
use(Sys.printl("String '" + numberString + "' as double: " + asDouble))

# 错误处理（通过异常机制）
try:
    var invalid = "abc"(int)  # 这会抛出异常
    use(Sys.printl("This won't be printed"))
catch:
    use(Sys.error("Failed to convert string to integer"))
```

### 1.3.4 复杂分数运算

```vast
# 复杂分数运算示例
imp Sys

# 基础分数运算
var frac1 = $(1/3)
var frac2 = $(1/4)
var frac3 = $(1/6)

use(Sys.printl("Fraction Operations"))
use(Sys.printl("frac1 = " + frac1))
use(Sys.printl("frac2 = " + frac2))
use(Sys.printl("frac3 = " + frac3))

# 分数算术
var sum = frac1 + frac2
var difference = frac1 - frac2
var product = frac1 * frac2
var quotient = frac1 / frac2

use(Sys.printl("Sum: " + frac1 + " + " + frac2 + " = " + sum))
use(Sys.printl("Difference: " + frac1 + " - " + frac2 + " = " + difference))
use(Sys.printl("Product: " + frac1 + " * " + frac2 + " = " + product))
use(Sys.printl("Quotient: " + frac1 + " / " + frac2 + " = " + quotient))

# 复杂表达式
var complexExpr = (frac1 + frac2) * frac3 / $(1/2)
use(Sys.printl("Complex expression: " + complexExpr))

# 永久分数
var permanentFrac = $$(22/7)
use(Sys.printl("Permanent fraction: " + permanentFrac))
```

### 1.3.5 输入输出和交互

```vast
# 交互式程序示例
imp Sys
imp DataType

use(Sys.printl("Interactive Calculator"))
use(Sys.printl("======================"))

# 获取用户输入
var input1 = Sys.input("Enter first number: ")
var input2 = Sys.input("Enter second number: ")
var operation = Sys.input("Enter operation (+, -, *, /): ")

# 转换输入
var num1 = input1(double)
var num2 = input2(double)

# 执行运算
var result = 0.0
if (operation == "+"):
    result = num1 + num2
else if (operation == "-"):
    result = num1 - num2
else if (operation == "*"):
    result = num1 * num2
else if (operation == "/"):
    if (num2 != 0):
        result = num1 / num2
    else:
        use(Sys.error("Division by zero!"))
else:
    use(Sys.error("Invalid operation!"))

# 显示结果
use(Sys.printl("Result: " + num1 + " " + operation + " " + num2 + " = " + result))

# 多值输入示例
use(Sys.printl(""))
use(Sys.printl("Multiple Values Example"))
var multiInput = Sys.multiValueInput("Enter three values (x y z): ", 3)
use(Sys.printl("You entered: " + multiInput[0] + ", " + multiInput[1] + ", " + multiInput[2]))
```

## 1.4 语法特性总结

### 1.4.1 核心特性
- **简洁语法**：类似 Python 的简洁语法设计
- **动态类型**：支持动态类型推断
- **可选静态类型**：提供类型提示和编译时检查
- **分数运算**：内置分数类型和运算支持
- **外置库扩展**：模块化的库扩展系统

### 1.4.2 执行模型
- **AST 解释执行**：基于抽象语法树的解释执行
- **访问者模式**：使用访问者模式遍历和执行 AST
- **严格类型检查**：运行时的严格类型验证
- **错误处理**：完整的异常处理机制

### 1.4.3 扩展能力
- **内置库系统**：丰富的内置函数库
- **外置库支持**：可扩展的外置库机制
- **反射调用**：支持 Java 类的反射调用
- **调试支持**：多级别的调试输出系统


# 第二部分：内部基本原理详解

## 2.1 整体架构设计

### 2.1.1 虚拟机核心组件

Vast虚拟机采用分层架构设计，各组件职责明确：


┌─────────────────────────────────────────┐
│             应用层 (VastCLI)             │
├─────────────────────────────────────────┤
│           脚本执行层 (Vast)              │
├─────────────────────────────────────────┤
│          虚拟机核心层 (VastVM)           │
├─────────────┬─────────────┬─────────────┤
│  解释器层   │   库管理    │   调试器    │
│ (Interpreter)│ (LibraryRegistry)│  (Debugger) │
├─────────────┼─────────────┼─────────────┤
│  语法分析   │  词法分析   │  异常处理   │
│   (Parser)   │   (Lexer)   │ (Exceptions) │
└─────────────┴─────────────┴─────────────┤
│             AST抽象语法树               │
└─────────────────────────────────────────┘


#### 2.1.2 数据流架构

```
源代码 → 词法分析 → Token流 → 语法分析 → AST → 解释执行 → 结果
     │          │          │          │          │          │
     └──字符流──┘──单词流──┘──语法树──┘──访问者──┘──输出──┘
```

## 2.2 词法分析器 (Lexer)

### 2.2.1 核心设计原理

词法分析器采用**状态机模式**，逐个字符扫描源代码并生成Token流：

```java
public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
}
```

### 2.2.2 Token类型系统

Vast语言支持完整的Token类型：

| Token类型 | 示例 | 描述 |
|-----------|------|------|
| IDENTIFIER | `variable`, `ClassName` | 标识符 |
| KEYWORD | `var`, `imp`, `loop` | 关键字 |
| LITERAL | `123`, `"string"`, `true` | 字面量 |
| OPERATOR | `+`, `-`, `*`, `/` | 运算符 |
| SEPARATOR | `(`, `)`, `,`, `.` | 分隔符 |
| MODIFIER | `$`, `$$` | 分数修饰符 |

### 2.2.3 扫描算法实现

```java
private void scanToken() {
    char c = advance();
    switch (c) {
        case '(': addToken("LEFT_PAREN"); break;
        case ')': addToken("RIGHT_PAREN"); break;
        // ... 其他字符处理
        case '"': string(); break;  // 字符串处理
        default:
            if (isDigit(c)) {
                number();           // 数字处理
            } else if (isAlpha(c)) {
                identifier();       // 标识符处理
            } else {
                error("Unexpected character");
            }
    }
}
```

### 2.2.4 特殊语法处理

**分数表达式语法**：
- `$(expression)` - 单步分数
- `$$(expression)` - 永久分数

**注释语法**：
- `# 单行注释`
- `### 多行注释 ###`

## 2.3 语法分析器 (Parser)

### 2.3.1 递归下降解析

语法分析器采用**递归下降解析算法**，实现表达式优先级：

```java
private Expression parseExpression() {
    return parseAssignment();
}

private Expression parseAssignment() {
    Expression expr = parseLogicalOr();
    if (match("EQUAL")) {
        // 处理赋值表达式
    }
    return expr;
}

private Expression parseLogicalOr() {
    Expression expr = parseLogicalAnd();
    while (match("OR")) {
        // 处理逻辑或
    }
    return expr;
}
// ... 继续向下解析更高级别的表达式
```

### 2.3.2 运算符优先级表

| 优先级 | 运算符 | 结合性 | 处理方法 |
|--------|--------|--------|----------|
| 1 | `=` | 右结合 | `parseAssignment()` |
| 2 | `||` | 左结合 | `parseLogicalOr()` |
| 3 | `&&` | 左结合 | `parseLogicalAnd()` |
| 4 | `==`, `!=` | 左结合 | `parseEquality()` |
| 5 | `<`, `>`, `<=`, `>=` | 左结合 | `parseComparison()` |
| 6 | `+`, `-` | 左结合 | `parseTerm()` |
| 7 | `*`, `/`, `//`, `%` | 左结合 | `parseFactor()` |
| 8 | `**` | 右结合 | `parsePower()` |
| 9 | `!`, `-`, `++`, `~` | 右结合 | `parseUnary()` |
| 10 | `.`, `()` | 左结合 | `parsePrimary()` |

### 2.3.3 语句解析流程

```java
private Statement parseStatement() {
    if (match("IMPORT")) return parseImportStatement();
    if (match("VAR")) return parseVariableDeclaration();
    if (match("LOOP")) return parseLoopStatement();
    if (match("USE")) return parseUseStatement();
    if (match("SWAP")) return parseSwapStatement();
    return parseExpressionOrAssignment();
}
```

### 2.3.4 缩进敏感解析

Vast采用Python风格的缩进敏感语法：

```java
private List<Statement> parseIndentedBlock() {
    List<Statement> statements = new ArrayList<>();
    int baseIndent = getCurrentIndent();
    int blockIndent = -1;
    
    while (!isAtEnd()) {
        int currentIndent = peek().getColumn();
        if (currentIndent <= baseIndent) break;
        
        if (blockIndent == -1) blockIndent = currentIndent;
        if (currentIndent >= blockIndent) {
            Statement stmt = parseStatement();
            if (stmt != null) statements.add(stmt);
        } else {
            break;
        }
    }
    return statements;
}
```

## 2.4 抽象语法树 (AST)

### 2.4.1 访问者模式设计

AST采用**访问者模式**实现多态操作：

```java
public interface ASTVisitor<T> {
    // 表达式访问方法
    T visitLiteralExpression(LiteralExpression expr);
    T visitVariableExpression(VariableExpression expr);
    T visitBinaryExpression(BinaryExpression expr);
    // ... 其他visit方法
    
    // 默认实现
    default T visit(ASTNode node) {
        return node.accept(this);
    }
}
```

### 2.4.2 AST节点层次结构

```
ASTNode (抽象基类)
├── Expression (表达式)
│   ├── LiteralExpression (字面量)
│   ├── VariableExpression (变量引用)
│   ├── BinaryExpression (二元运算)
│   ├── UnaryExpression (一元运算)
│   ├── AssignmentExpression (赋值表达式)
│   ├── FunctionCallExpression (函数调用)
│   ├── MethodCallExpression (方法调用)
│   ├── MemberAccessExpression (成员访问)
│   ├── FractionExpression (分数表达式)
│   ├── BitwiseExpression (按位运算)
│   └── TypeCastExpression (类型转换)
└── Statement (语句)
    ├── VariableDeclaration (变量声明)
    ├── AssignmentStatement (赋值语句)
    ├── ExpressionStatement (表达式语句)
    ├── ImportStatement (导入语句)
    ├── LoopStatement (循环语句)
    ├── UseStatement (使用语句)
    └── SwapStatement (交换语句)
```

### 2.4.3 节点创建示例

```java
// 创建二元表达式：a + b
Expression left = new VariableExpression("a", line, column);
Expression right = new VariableExpression("b", line, column);
Expression binaryExpr = new BinaryExpression(left, "+", right, line, column);

// 创建方法调用：Sys.print("Hello")
VariableExpression className = new VariableExpression("Sys", line, column);
List<Expression> args = List.of(new LiteralExpression("Hello", line, column));
Expression methodCall = new MethodCallExpression(className, "print", args, line, column);
```

## 2.5 解释器 (Interpreter)

### 2.5.1 双访问者模式

解释器采用**双访问者模式**：
- 主访问者：处理语句执行和流程控制
- 表达式求值访问者：专门处理表达式求值

```java
public class Interpreter implements ASTVisitor<Void> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> variableTypes = new HashMap<>();
    private Object lastResult = null;
    
    // 主访问方法
    @Override
    public Void visitVariableDeclaration(VariableDeclaration stmt) {
        // 处理变量声明
    }
    
    // 表达式求值器
    private class ExpressionEvaluator implements ASTVisitor<Object> {
        @Override
        public Object visitBinaryExpression(BinaryExpression expr) {
            // 处理二元表达式求值
        }
    }
}
```

### 2.5.2 类型系统实现

**动态类型与静态提示结合**：

```java
private void validateTypeCompatibility(String expectedType, Object value, 
                                     String varName, int line, int column) {
    if (value == null) return; // null可赋值给任何类型
    
    String actualType = getValueType(value);
    if (!isTypeCompatible(expectedType, value)) {
        throw new VastExceptions.NotGrammarException(
            "Type mismatch: cannot assign " + actualType + 
            " to variable '" + varName + "' of type " + expectedType,
            line, column
        );
    }
}
```

**支持的类型**：
- 基础类型：`int`, `double`, `boolean`, `string`, `char`
- 整数变体：`int8`/`byte`, `int16`/`short`, `int32`, `int64`/`long`
- 特殊类型：`large` (大整数), `Fraction` (分数)

### 2.5.3 运算符重载机制

```java
private Object performAddition(Object left, Object right) {
    if (left instanceof Double && right instanceof Double) {
        return (Double) left + (Double) right;
    }
    if (left instanceof Integer && right instanceof Integer) {
        return (Integer) left + (Integer) right;
    }
    if (left instanceof String || right instanceof String) {
        return stringify(left) + stringify(right);
    }
    throw new VastExceptions.MathError("Operand type mismatch");
}
```

### 2.5.4 方法调用解析

**静态方法解析算法**：

```java
private String resolveClassNameForMethod(String methodName, int line, int column) {
    // 1. 检查唯一映射
    if (staticMethodToClass.containsKey(methodName)) {
        return staticMethodToClass.get(methodName);
    }
    
    // 2. 检查冲突
    if (methodConflicts.containsKey(methodName)) {
        throw VastExceptions.AmbiguousReferenceException.forMethod(
            methodName, methodConflicts.get(methodName), line, column);
    }
    
    return null; // 方法不存在
}
```

## 2.6 分数系统实现

### 2.6.1 Fraction类设计

```java
public class Fraction {
    private final int numerator;    // 分子
    private final int denominator;  // 分母
    private boolean isPermanent;    // 永久性标记
    
    public Fraction(int numerator, int denominator) {
        // 自动约分和规范化
        int gcd = gcd(Math.abs(numerator), Math.abs(denominator));
        this.numerator = numerator / gcd;
        this.denominator = denominator / gcd;
    }
}
```

### 2.6.2 分数运算算法

```java
public static Fraction fractionAdd(Fraction a, Fraction b) {
    // a/b + c/d = (a*d + b*c) / (b*d)
    int numerator = a.getNumerator() * b.getDenominator() + 
                   b.getNumerator() * a.getDenominator();
    int denominator = a.getDenominator() * b.getDenominator();
    return new Fraction(numerator, denominator);
}
```

### 2.6.3 浮点数转分数算法

使用**连分数展开法**进行高精度转换：

```java
private static Fraction approximateFraction(double value) {
    final double EPSILON = 1e-6;
    // 连分数展开实现
    int n0 = 0, d0 = 1;
    int n1 = 1, d1 = 0;
    double x = value;
    
    while (true) {
        int a = (int) Math.floor(x);
        int n2 = a * n1 + n0;
        int d2 = a * d1 + d0;
        
        if (Math.abs((double) n2 / d2 - value) < EPSILON) {
            return new Fraction(sign * n2, d2);
        }
        // ... 继续迭代
    }
}
```

## 2.7 内置库系统

### 2.7.1 核心内置类

| 类名 | 功能 | 主要方法 |
|------|------|----------|
| `Sys` | 系统IO操作 | `print()`, `input()`, `error()` |
| `DataType` | 类型操作 | `typeOf()`, `toString()`, `toInt()` |
| `ArrayUtil` | 数组操作 | `length()`, `contains()`, `create()` |
| `TimeUtil` | 时间操作 | `wait()`, `now()`, `timestamp()` |
| `Ops` | 运算符扩展 | 复杂运算支持 |

### 2.7.2 格式化输出系统

支持混合格式化语法：

```java
private static String formatString(String format, Object[] args) {
    // 支持 {0} {1} 和 %s %d 混合语法
    // 支持转义花括号：\{ 和 \}
    return mixedFormat(unescapeBraces(format), args);
}
```

## 2.8 异常处理系统

#### 2.8.1 异常层次结构

```
VastRuntimeException (基类)
├── TypeMismatchException (类型不匹配)
├── AmbiguousReferenceException (指代不明)
├── PassParameterException (参数传递异常)
│   └── CannotBeChanged (无法更改异常)
│   └── NonExistentObject (不存在的对象)
├── ExhaustedResourcesException (耗尽资源)
├── MathError (计算异常)
├── NotGrammarException (语法异常)
└── UnknownVastException (未知异常)
```

### 2.8.2 异常上下文信息

```java
public static class NotGrammarException extends VastRuntimeException {
    public NotGrammarException(String message, int lineNumber, int columnNumber) {
        super("Syntax error at line " + lineNumber + 
              ", column " + columnNumber + ": " + message);
    }
}
```

## 2.9 调试器系统

### 2.9.1 三级调试模式

```java
public enum Level {
    BASIC,      // 基本模式：错误堆栈追踪
    DETAIL,     // 详细模式：基本信息和@符号细节  
    BASE        // 底层模式：所有信息包括AST细节
}
```

### 2.9.2 分类日志输出

```java
public void logTypeCheck(String message) {
    if (isDetailEnabled()) {
        System.out.println("@ [TYPE-CHECK] " + message);
    }
}

public void logAST(String message) {
    if (isBaseEnabled()) {
        System.out.println("@ [AST] " + message);
    }
}
```

## 2.10 内存管理与执行环境

### 2.10.1 变量存储结构

```java
public class Interpreter implements ASTVisitor<Void> {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, String> variableTypes = new HashMap<>();
    
    // 全局变量存储
    private static final Map<String, Object> GLOBAL_VARS = new HashMap<>();
}
```

### 2.10.2 执行上下文管理

虚拟机维护多个执行上下文：
- 全局变量上下文（常量、内置变量）
- 局部变量上下文（脚本执行期间）
- 导入类上下文（已加载的类）
- 库注册表上下文（外置库信息）

## 2.11 性能优化策略

#### 2.11.1 静态方法缓存

```java
private void initializeStaticMethodMapping() {
    // 启动时预计算静态方法映射
    // 避免运行时重复反射查找
    Map<String, Set<String>> methodToClasses = new HashMap<>();
    
    // 收集所有类的静态方法并检测冲突
    for (Map.Entry<String, Class<?>> entry : VastVM.getBuiltinClasses().entrySet()) {
        collectStaticMethods(entry.getKey(), entry.getValue(), methodToClasses);
    }
}
```

### 2.11.2 类型检查优化

- **编译时类型提示**：通过类型声明提前发现错误
- **运行时类型缓存**：缓存类型兼容性检查结果
- **惰性求值**：表达式只在需要时求值

## 2.12 扩展性设计

### 2.12.1 插件式架构

通过访问者模式实现功能扩展：

```java
// 可以轻松实现新的AST处理器
public class Optimizer implements ASTVisitor<ASTNode> {
    // 实现AST优化逻辑
}

public class CodeGenerator implements ASTVisitor<String> {
    // 实现代码生成逻辑
}
```

### 2.12.2 配置化系统

支持通过系统属性配置虚拟机行为：

```java
// 调试级别配置
System.setProperty("vast.debug.level", "detail");

// 库目录配置  
System.setProperty("vast.libs.dir", "/custom/libs");
```


# 第三部分：外部库规则

## 3.1 外部库架构概述

Vast虚拟机的外部库系统采用模块化架构设计，支持动态加载、注册和管理第三方功能扩展。整个系统基于接口驱动设计，确保库的独立性和可插拔性。

#### 3.1.1 核心组件关系

```
VastVM ←→ VastLibraryRegistry ←→ VastLibraryLoader ←→ VastExternalLibrary
     ↑              ↑                      ↑                    ↑
  内置类注册       库注册表              库加载器             外部库接口
```

### 3.2 外部库接口规范

#### 3.2.1 核心接口定义

所有外部库必须实现 `VastExternalLibrary` 接口：

```java
public interface VastExternalLibrary {
    LibraryMetadata getMetadata();
    void initialize(VastVM vm, VastLibraryRegistry registry);
    default void cleanup() {}
    Map<String, Class<?>> getProvidedClasses();
}
```

#### 3.2.2 接口方法详细要求

**getMetadata()**
- **作用**: 返回库的元数据信息
- **返回值**: `LibraryMetadata` 对象，包含库的基本信息
- **要求**: 必须返回非空的有效元数据

**initialize(VastVM vm, VastLibraryRegistry registry)**
- **作用**: 库的初始化方法
- **参数**:
  - `vm`: Vast虚拟机实例，用于访问VM功能
  - `registry`: 库注册表，用于注册其他依赖库
- **时机**: 在库被加载时调用，且仅调用一次
- **异常处理**: 应妥善处理所有异常，避免影响VM稳定性

**cleanup()**
- **作用**: 库的清理方法（可选）
- **默认实现**: 空实现
- **时机**: 在库被卸载时调用
- **用途**: 释放资源、关闭连接等清理操作

**getProvidedClasses()**
- **作用**: 返回库提供的类映射
- **返回值**: `Map<String, Class<?>>`，键为类名，值为对应的Class对象
- **要求**: 必须返回非空映射，即使为空映射也应返回 `Collections.emptyMap()`

### 3.3 库元数据规范

#### 3.3.1 LibraryMetadata 结构

```java
public class LibraryMetadata {
    private final String name;           // 库名称（必需）
    private final String version;        // 版本号（必需）
    private final String description;    // 描述信息（可选）
    private final String author;         // 作者信息（可选）
    private final List<String> dependencies;     // 依赖库列表
    private final Map<String, String> configuration; // 配置信息
}
```

#### 3.3.2 元数据属性要求

**name**
- **格式**: 小写字母、数字、连字符组成
- **长度**: 2-50个字符
- **唯一性**: 必须在系统中唯一
- **示例**: `"math-utils"`, `"network-handler"`

**version**
- **格式**: 语义化版本号 (SemVer)
- **模式**: `主版本号.次版本号.修订号`
- **示例**: `"1.0.0"`, `"2.1.3"`

**dependencies**
- **格式**: 逗号分隔的库ID列表
- **解析**: 自动按 `\\s*,\\s*` 模式分割
- **示例**: `"common-utils, logging-lib"`

**configuration**
- **前缀**: 配置键必须以 `config.` 开头
- **示例**: 
  - `config.mainClass=com.example.MyLibrary`
  - `config.threadPoolSize=10`

### 3.4 库文件结构规范

#### 3.4.1 标准目录结构

```
my-library/
├── library.properties          # 库元数据文件（必需）
├── src/
│   └── com/
│       └── example/
│           └── MyLibrary.java  # 库主类（必需）
├── lib/                        # 依赖库目录（可选）
│   └── dependency.jar
└── README.md                   # 说明文档（推荐）
```

#### 3.4.2 打包格式要求

**支持的格式**:
- JAR 文件 (`.jar`)
- ZIP 文件 (`.zip`)

**内部结构要求**:
- 必须包含 `library.properties` 在根目录
- 类文件必须按照标准Java包结构组织
- 资源文件可以放在任意位置，但建议统一管理

### 3.5 库注册与加载机制

#### 3.5.1 注册表架构

`VastLibraryRegistry` 采用单例模式管理所有库的注册和加载状态：

```java
public class VastLibraryRegistry {
    private final Map<String, RegistryToken> registeredLibraries;
    private final Map<String, VastExternalLibrary> loadedLibraries;
    private final Map<String, String> classNameToLibrary;
}
```

#### 3.5.2 注册令牌 (RegistryToken)

注册令牌包含库的完整注册信息：

```java
public class RegistryToken {
    private final String libraryId;      // 库唯一标识
    private final Class<?> libraryClass; // 库实现类
    private final boolean enabled;       // 是否启用
    private final int priority;          // 加载优先级
}
```

#### 3.5.3 库加载流程

1. **注册阶段**
   - 库信息被记录到 `registeredLibraries`
   - 生成对应的 `RegistryToken`
   - 验证库ID的唯一性

2. **加载阶段**
   - 检查库是否已启用 (`enabled`)
   - 实例化库类（无参构造函数）
   - 调用 `initialize()` 方法
   - 注册提供的类到 `classNameToLibrary` 映射

3. **初始化阶段**
   - 库完成自身的初始化配置
   - 注册依赖的其他库（如果需要）
   - 准备提供的类和方法

### 3.6 库发现与自动加载

#### 3.6.1 库搜索路径

系统按以下顺序搜索库文件：

1. **当前工作目录**
   - `./library-name.jar`
   - `./library-name.zip`

2. **全局库目录**
   - `./vast_libs/library-name.jar`
   - `./vast_libs/library-name.zip`

3. **自定义路径**
   - 通过 `VastLibraryRegistry.setGlobalLibsDir()` 设置

#### 3.6.2 导入语句解析

当解析 `imp` 语句时：

```vast
imp com.example.MyLibrary    // 导入类或库
```

加载器执行以下步骤：
1. 检查是否为VM内置类（跳过）
2. 检查是否为已导入的类（跳过）
3. 尝试作为外部库加载
4. 如果库加载失败，尝试作为普通Java类加载

### 3.7 类名冲突解决机制

#### 3.7.1 冲突检测

系统维护类名到库的映射关系，检测规则：

```java
// 在注册提供的类时检查冲突
String existingLibrary = classNameToLibrary.get(className);
if (existingLibrary != null) {
    System.err.printf("[Warning] Class name conflict: %s (from %s) conflicts with %s%n",
            className, libraryId, existingLibrary);
    continue; // 跳过冲突的类注册
}
```

#### 3.7.2 冲突处理策略

1. **警告并跳过**: 后加载的类被跳过，使用先加载的类
2. **日志记录**: 详细的冲突信息输出到错误流
3. **运行时选择**: 在方法调用时根据上下文解析

### 3.8 库依赖管理

#### 3.8.1 依赖声明

在 `library.properties` 中声明依赖：

```properties
dependencies=common-utils, network-lib, logging-framework
```

#### 3.8.2 依赖解析流程

1. 解析依赖字符串为库ID列表
2. 按顺序加载每个依赖库
3. 如果依赖库加载失败，当前库加载也失败
4. 确保依赖库的初始化顺序

### 3.9 配置系统

#### 3.9.1 配置格式

库可以通过元数据传递配置：

```properties
config.mainClass=com.example.MyLibraryImpl
config.database.url=jdbc:mysql://localhost:3306/mydb
config.cache.enabled=true
config.cache.size=1000
```

#### 3.9.2 配置访问

在库初始化时访问配置：

```java
@Override
public void initialize(VastVM vm, VastLibraryRegistry registry) {
    LibraryMetadata metadata = getMetadata();
    Map<String, String> config = metadata.getConfiguration();
    
    String mainClass = config.get("mainClass");
    boolean cacheEnabled = Boolean.parseBoolean(config.get("cache.enabled"));
    // ... 使用配置
}
```

### 3.10 库生命周期管理

#### 3.10.1 完整生命周期

```
注册 → 加载 → 初始化 → 运行 → 清理 → 卸载
```

#### 3.10.2 状态转换

- **注册**: 库信息被记录，但未实例化
- **加载**: 库类被实例化，准备初始化
- **初始化**: `initialize()` 方法被调用，库完成设置
- **运行**: 库提供服务，处理请求
- **清理**: `cleanup()` 方法被调用，释放资源
- **卸载**: 库实例被移除，类映射被清理

### 3.11 错误处理与容错

#### 3.11.1 加载失败处理

库加载失败时的处理策略：

1. **静默失败**: 不中断VM执行，仅记录错误
2. **依赖隔离**: 单个库失败不影响其他库
3. **重试机制**: 支持多次加载尝试（可配置）

#### 3.11.2 异常处理规范

库实现应遵循的异常处理原则：

```java
@Override
public void initialize(VastVM vm, VastLibraryRegistry registry) {
    try {
        // 库初始化代码
        setupComponents();
        registerServices();
    } catch (LibraryInitializationException e) {
        // 记录详细错误信息
        System.err.println("Library initialization failed: " + e.getMessage());
        // 可以选择重新抛出或静默处理
        throw new RuntimeException("Failed to initialize library", e);
    }
}
```

### 3.12 性能与资源管理

#### 3.12.1 资源使用规范

1. **内存管理**: 库应合理管理内存，避免内存泄漏
2. **连接管理**: 及时关闭数据库连接、网络连接等资源
3. **线程管理**: 合理使用线程，避免创建过多线程
4. **文件管理**: 及时关闭文件流，释放文件锁

#### 3.12.2 性能优化建议

1. **延迟加载**: 资源密集型操作应延迟到实际需要时
2. **缓存策略**: 合理使用缓存，但要注意缓存失效
3. **连接池**: 对频繁使用的资源使用连接池
4. **异步操作**: 耗时操作应使用异步方式

### 3.13 安全规范

#### 3.13.1 安全要求

1. **输入验证**: 对所有外部输入进行严格验证
2. **权限控制**: 按照最小权限原则访问系统资源
3. **数据加密**: 敏感数据应进行加密处理
4. **审计日志**: 重要操作应记录审计日志

#### 3.13.2 安全最佳实践

```java
@Override
public void initialize(VastVM vm, VastLibraryRegistry registry) {
    // 验证必要的配置参数
    validateRequiredConfig();
    
    // 设置安全管理器（如果需要）
    setupSecurityManager();
    
    // 限制资源访问权限
    configureResourceLimits();
}
```

### 3.14 测试与验证

#### 3.14.1 测试要求

每个外部库应提供：

1. **单元测试**: 测试库的各个组件
2. **集成测试**: 测试库与VM的集成
3. **性能测试**: 验证库的性能表现
4. **兼容性测试**: 验证与不同VM版本的兼容性

#### 3.14.2 测试示例

```java
public class MyLibraryTest {
    @Test
    public void testLibraryInitialization() {
        VastExternalLibrary library = new MyLibrary();
        VastVM vm = new VastVM();
        VastLibraryRegistry registry = VastLibraryRegistry.getInstance();
        
        // 测试初始化
        assertDoesNotThrow(() -> library.initialize(vm, registry));
        
        // 测试元数据
        LibraryMetadata metadata = library.getMetadata();
        assertNotNull(metadata);
        assertEquals("my-library", metadata.getName());
        
        // 测试提供的类
        Map<String, Class<?>> classes = library.getProvidedClasses();
        assertFalse(classes.isEmpty());
    }
}
```

### 3.15 文档与示例

#### 3.15.1 文档要求

每个库应提供完整的文档：

1. **README**: 库的基本介绍和使用方法
2. **API文档**: 详细的API参考
3. **示例代码**: 实际使用示例
4. **变更日志**: 版本更新记录

#### 3.15.2 示例库模板

使用CLI工具创建标准库模板：

```bash
vast lib create my-library
```

这将生成包含完整文档和示例的标准库结构。

### 3.16 版本兼容性

#### 3.16.1 版本管理策略

1. **向后兼容**: 次要版本和修订版本应保持向后兼容
2. **破坏性变更**: 主版本号递增表示包含破坏性变更
3. **弃用策略**: 提供足够的弃用期和迁移指南

#### 3.16.2 VM版本兼容性

库应声明支持的VM版本范围：

```properties
config.supportedVMVersions=0.1.0+
config.minimumVMVersion=0.1.0
config.testedVMVersions=0.1.0,0.1.1,0.1.2
```


# 第四部分：Vast虚拟机修改指南

## 4.1 代码架构概览

```
com.vast
├── ast/                          # 抽象语法树定义
│   ├── expressions/              # 表达式节点
│   ├── statements/               # 语句节点
│   ├── ASTNode.java              # AST节点基类
│   ├── ASTVisitor.java           # 访问者接口
│   └── Program.java              # 程序根节点
├── parser/                       # 语法分析器
│   ├── Lexer.java                # 词法分析器
│   ├── Parser.java               # 语法分析器
│   └── Token.java                # 词法单元
├── interpreter/                  # 解释器
│   └── Interpreter.java          # 解释器实现
├── vm/                          # 虚拟机核心
│   └── VastVM.java              # 虚拟机主类
├── internal/                    # 内部库
│   ├── Sys.java                 # 系统函数
│   ├── Fraction.java            # 分数类
│   ├── Debugger.java            # 调试器
│   └── DataType.java            # 数据类型操作
├── registry/                    # 库注册系统
│   ├── VastExternalLibrary.java # 外部库接口
│   ├── VastLibraryRegistry.java # 库注册表
│   └── VastLibraryLoader.java   # 库加载器
└── exception/                   # 异常系统
    └── VastExceptions.java      # 异常定义
```

## 4.2 AST系统修改

### 4.2.1 添加新的AST节点类型

#### 步骤1：定义新的AST节点类

在对应的包中创建新的AST节点类：

```java
// 在 com.vast.ast.expressions 或 com.vast.ast.statements 包中
public class NewExpression extends Expression {
    private final List<Expression> elements;
    
    public NewExpression(List<Expression> elements, 
                        int lineNumber, int columnNumber) {
        super(lineNumber, columnNumber);
        this.elements = elements;
    }
    
    public List<Expression> getElements() { return elements; }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitNewExpression(this);
    }
    
    @Override
    public String toString() {
        return "new(" + elements + ")";
    }
}
```

#### 步骤2：更新ASTVisitor接口

在`ASTVisitor.java`中添加新的访问方法：

```java
public interface ASTVisitor<T> {
    // 现有方法...
    T visitNewExpression(NewExpression expr);
    
    // 默认实现处理未知节点
    default T visit(ASTNode node) {
        return node.accept(this);
    }
}
```

#### 步骤3：更新所有访问者实现

确保所有实现了`ASTVisitor`的类都实现了新的访问方法：

```java
// 在Interpreter.java的ExpressionEvaluator中
@Override
public Object visitNewExpression(NewExpression expr) {
    // 实现具体的表达式求值逻辑
    List<Object> evaluatedElements = new ArrayList<>();
    for (Expression element : expr.getElements()) {
        evaluatedElements.add(evaluate(element));
    }
    return processNewExpression(evaluatedElements);
}
```

## 4.3 语法分析器修改

### 4.3.1 添加新的语法规则

#### 步骤1：更新词法分析器（Lexer）

在`Lexer.java`中添加新的词法单元：

```java
public class Lexer {
    // 在scanToken方法中添加新的词法单元处理
    private void scanToken() {
        char c = advance();
        
        switch (c) {
            // 现有case...
            case '@': 
                if (match('@')) {
                    addToken("DOUBLE_AT"); // 新的词法单元
                } else {
                    addToken("AT");
                }
                break;
            // 其他case...
        }
    }
}
```

#### 步骤2：更新语法分析器（Parser）

在`Parser.java`中添加新的解析方法：

```java
public class Parser {
    // 在合适的位置添加新的解析方法
    private Expression parseNewExpression() {
        Token newToken = previous(); // 假设已经匹配了"new"关键字
        consume("LEFT_PAREN", "Expect '(' after 'new'");
        
        List<Expression> elements = new ArrayList<>();
        if (!check("RIGHT_PAREN")) {
            do {
                elements.add(parseExpression());
            } while (match("COMMA"));
        }
        
        consume("RIGHT_PAREN", "Expect ')' after new expression elements");
        
        return new NewExpression(elements, 
                               newToken.getLine(), 
                               newToken.getColumn());
    }
    
    // 在parsePrimary或其他合适的方法中调用
    private Expression parsePrimary() {
        // 现有代码...
        if (match("NEW")) {
            return parseNewExpression();
        }
        // 其他情况...
    }
}
```

### 4.3.2 修改运算符优先级

在`Parser.java`中调整解析方法的调用顺序来改变运算符优先级：

```java
private Expression parseExpression() {
    return parseAssignment();
}

private Expression parseAssignment() {
    // 修改这里的调用顺序来改变优先级
    Expression expr = parseNewPrecedenceLevel(); // 新的优先级级别
    // 现有逻辑...
}

// 添加新的优先级级别
private Expression parseNewPrecedenceLevel() {
    Expression expr = parseLogicalOr(); // 或现有的其他级别
    
    while (match("NEW_OPERATOR")) {
        Token operator = previous();
        Expression right = parseLogicalOr(); // 或更低优先级的级别
        expr = new BinaryExpression(expr, operator.getLexeme(), right,
                                  operator.getLine(), operator.getColumn());
    }
    
    return expr;
}
```

## 4.4 解释器修改

### 4.4.1 添加新的表达式求值逻辑

在`Interpreter.java`的`ExpressionEvaluator`中添加：

```java
private class ExpressionEvaluator implements ASTVisitor<Object> {
    // 现有方法...
    
    @Override
    public Object visitNewExpression(NewExpression expr) {
        // 1. 计算所有元素的表达式值
        List<Object> evaluatedElements = new ArrayList<>();
        for (Expression element : expr.getElements()) {
            evaluatedElements.add(evaluate(element));
        }
        
        // 2. 执行具体的语义逻辑
        return createNewObject(evaluatedElements);
    }
    
    private Object createNewObject(List<Object> elements) {
        // 根据语言语义创建新的对象
        // 例如：创建数组、列表或其他数据结构
        
        if (elements.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 检查所有元素是否为同一类型
        Class<?> firstType = elements.get(0).getClass();
        for (Object element : elements) {
            if (!element.getClass().equals(firstType)) {
                throw new VastExceptions.TypeMismatchException(
                    "new expression", 
                    firstType.getSimpleName(),
                    element.getClass().getSimpleName(),
                    expr.getLineNumber(),
                    expr.getColumnNumber()
                );
            }
        }
        
        return new ArrayList<>(elements);
    }
}
```

### 4.4.2 修改类型系统

在`Interpreter.java`中修改类型检查和转换逻辑：

```java
public class Interpreter {
    // 修改类型兼容性检查
    private boolean isTypeCompatible(String expectedType, Object value) {
        if (value == null) return true;
        
        // 添加对新类型的支持
        if (expectedType.equals("list")) {
            return value instanceof List;
        }
        if (expectedType.equals("array")) {
            return value instanceof Object[];
        }
        
        // 现有类型检查...
        switch (expectedType) {
            case "int": return value instanceof Integer;
            // 其他现有类型...
            default: return true;
        }
    }
    
    // 修改类型转换
    private Object performTypeCast(Object value, String targetType,
                                  int lineNumber, int columnNumber, 
                                  boolean isExplicit) {
        // 添加对新类型的转换支持
        if (targetType.equals("list")) {
            return castToList(value, isExplicit, lineNumber, columnNumber);
        }
        
        // 现有类型转换...
        return value;
    }
    
    private Object castToList(Object value, boolean isExplicit, 
                             int lineNumber, int columnNumber) {
        if (value instanceof List) {
            return value;
        }
        if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        if (value instanceof String) {
            return Arrays.asList(((String) value).split(""));
        }
        
        throw new VastExceptions.MathError(
            "Cannot convert " + getValueType(value) + " to list",
            lineNumber, columnNumber
        );
    }
}
```

## 4.5 内置库修改

### 4.5.1 添加新的内置函数

在相应的内置类中添加新的静态方法：

```java
public class Sys {
    // 现有方法...
    
    /**
     * 新的内置函数：格式化输出
     */
    public static void format(String template, Object... args) {
        if (template == null) {
            out.print("null");
            return;
        }
        
        try {
            // 实现格式化逻辑
            String result = String.format(template, args);
            out.print(result);
        } catch (Exception e) {
            // 格式化失败时回退到简单输出
            out.print("Format error: " + template);
            for (Object arg : args) {
                out.print(" " + arg);
            }
        }
    }
    
    /**
     * 新的数学函数
     */
    public static double logarithm(double value, double base) {
        if (value <= 0 || base <= 0 || base == 1) {
            throw new IllegalArgumentException(
                "Logarithm arguments must be positive and base cannot be 1"
            );
        }
        return Math.log(value) / Math.log(base);
    }
}
```

### 4.5.2 修改现有内置函数

谨慎修改现有内置函数，确保向后兼容：

```java
public class DataType {
    // 修改现有函数以增强功能
    public static String toString(Object obj) {
        if (obj == null) return "null";
        
        // 增强：支持更多类型的字符串表示
        if (obj instanceof List) {
            return listToString((List<?>) obj);
        }
        if (obj instanceof Map) {
            return mapToString((Map<?, ?>) obj);
        }
        if (obj instanceof Object[]) {
            return arrayToString((Object[]) obj);
        }
        
        // 保持现有逻辑
        return obj.toString();
    }
    
    private static String listToString(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(toString(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
```

## 4.6 外部库系统修改

### 4.6.1 修改库加载机制

在`VastLibraryLoader.java`中增强库加载功能：

```java
public class VastLibraryLoader {
    // 增强库发现机制
    public boolean loadLibraryFromImport(String importPath, VastVM vm) {
        try {
            String cleanPath = importPath.split("//")[0].trim();
            
            // 增强：支持更多导入路径格式
            if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
                return loadRemoteLibrary(cleanPath, vm);
            }
            
            if (cleanPath.startsWith("maven:")) {
                return loadMavenLibrary(cleanPath, vm);
            }
            
            // 现有本地库加载逻辑...
            File libraryFile = findLibraryFile(cleanPath);
            if (libraryFile == null) {
                return false;
            }
            
            return loadLibraryFromFile(libraryFile, vm);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean loadRemoteLibrary(String url, VastVM vm) {
        // 实现远程库加载逻辑
        try {
            // 下载库文件到临时目录
            Path tempFile = downloadRemoteLibrary(url);
            return loadLibraryFromFile(tempFile.toFile(), vm);
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 4.6.2 增强库元数据支持

在`LibraryMetadata.java`中添加新的元数据字段：

```java
public class LibraryMetadata {
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final List<String> dependencies;
    private final Map<String, String> configuration;
    private final String license; // 新增字段
    private final String repository; // 新增字段
    private final List<String> tags; // 新增字段
    
    public LibraryMetadata(String name, String version, String description,
                          String author, List<String> dependencies, 
                          Map<String, String> configuration, String license,
                          String repository, List<String> tags) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.dependencies = List.copyOf(dependencies);
        this.configuration = Map.copyOf(configuration);
        this.license = license;
        this.repository = repository;
        this.tags = List.copyOf(tags);
    }
    
    // 新增getter方法
    public String getLicense() { return license; }
    public String getRepository() { return repository; }
    public List<String> getTags() { return tags; }
}
```

## 4.7 调试和测试系统修改

### 4.7.1 增强调试输出

在`Debugger.java`中添加新的调试级别或输出类型：

```java
public class Debugger {
    // 现有代码...
    
    // 添加新的调试类别
    public void logPerformance(String message) {
        if (isDetailEnabled()) {
            System.out.println("@ [PERF] " + message);
        }
    }
    
    public void logMemory(String message) {
        if (isBaseEnabled()) {
            System.out.println("@ [MEMORY] " + message);
        }
    }
    
    // 添加性能监控方法
    public void startTimer(String operation) {
        if (isDetailEnabled()) {
            System.out.println("@ [TIMER-START] " + operation);
        }
    }
    
    public void stopTimer(String operation) {
        if (isDetailEnabled()) {
            System.out.println("@ [TIMER-END] " + operation);
        }
    }
}
```

### 4.7.2 修改性能监控

在关键位置添加性能监控点：

```java
public class Interpreter {
    public Void visitLoopStatement(LoopStatement stmt) {
        debugger.startTimer("loop_execution");
        
        try {
            Object condition = evaluate(stmt.getCondition());
            // 循环执行逻辑...
        } finally {
            debugger.stopTimer("loop_execution");
        }
        
        return null;
    }
}
```

## 4.8 配置和构建系统修改

### 4.8.1 添加配置选项

在`VastVM.java`中添加新的配置参数：

```java
public class VastVM {
    private boolean debugMode = false;
    private int maxExecutionTime = 0; // 新增：最大执行时间（毫秒）
    private int maxMemoryUsage = 100 * 1024 * 1024; // 新增：最大内存使用（字节）
    private boolean sandboxMode = false; // 新增：沙箱模式
    
    // 新增配置方法
    public void setMaxExecutionTime(int milliseconds) {
        this.maxExecutionTime = milliseconds;
    }
    
    public void setMaxMemoryUsage(int bytes) {
        this.maxMemoryUsage = bytes;
    }
    
    public void setSandboxMode(boolean enabled) {
        this.sandboxMode = enabled;
    }
    
    // 在解释器中应用配置
    private void applySecurityRestrictions() {
        if (sandboxMode) {
            // 应用沙箱限制
            restrictSystemCalls();
            limitResourceUsage();
        }
        
        if (maxExecutionTime > 0) {
            startExecutionTimer();
        }
    }
}
```

### 4.8.2 修改构建配置

如果需要修改构建过程，更新相应的构建脚本或配置：

```java
// 在CLI中添加新的命令行选项
private static void handleRunCommand(String[] args) {
    // 解析新的配置选项
    int maxTime = 0;
    boolean sandbox = false;
    
    for (String arg : args) {
        if (arg.startsWith("--max-time=")) {
            maxTime = Integer.parseInt(arg.substring("--max-time=".length()));
        }
        if (arg.equals("--sandbox")) {
            sandbox = true;
        }
    }
    
    // 应用配置到VM
    VastVM vm = new VastVM();
    vm.setMaxExecutionTime(maxTime);
    vm.setSandboxMode(sandbox);
    // 执行脚本...
}
```

## 4.9 测试和验证

### 4.9.1 添加单元测试

为新的功能添加相应的测试用例：

```java
// 在新的测试类中
public class NewFeatureTest {
    @Test
    public void testNewExpression() {
        VastVM vm = new VastVM();
        String code = "var list = new(1, 2, 3)";
        
        try {
            vm.execute(List.of(code));
            Object result = vm.getLastResult();
            assertTrue(result instanceof List);
            List<?> list = (List<?>) result;
            assertEquals(3, list.size());
        } catch (Exception e) {
            fail("New expression should work correctly");
        }
    }
    
    @Test
    public void testTypeCompatibility() {
        Interpreter interpreter = new Interpreter(new VastVM());
        
        // 测试新的类型兼容性规则
        assertTrue(interpreter.isTypeCompatible("list", Arrays.asList(1, 2, 3)));
        assertFalse(interpreter.isTypeCompatible("list", "not a list"));
    }
}
```

### 4.9.2 性能基准测试

添加性能测试来确保修改不会导致性能下降：

```java
public class PerformanceTest {
    @Test
    public void benchmarkNewFeature() {
        VastVM vm = new VastVM();
        String code = "// 性能测试代码";
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            vm.execute(List.of(code));
        }
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        assertTrue("Performance should be acceptable", duration < 1000);
    }
}
```

## 4.10 文档更新

### 4.10.1 更新开发者文档

确保所有修改都有相应的文档说明：

```markdown
# 新功能文档

## 新增表达式：new表达式

### 语法
```
new(element1, element2, ..., elementN)
```

### 语义
创建一个包含指定元素的新列表。

### 示例
```
var numbers = new(1, 2, 3, 4, 5)
var strings = new("hello", "world")
```

### 实现细节
- 位置：`com.vast.ast.expressions.NewExpression`
- 解析：`Parser.parseNewExpression()`
- 求值：`Interpreter.visitNewExpression()`
```

### 4.10.2 更新API文档

为新的公共API添加JavaDoc注释：

```java
/**
 * 新的内置函数：格式化输出
 * 
 * @param template 格式化模板，支持标准格式说明符
 * @param args 要格式化的参数
 * @throws VastExceptions.MathError 如果格式化失败
 * 
 * @example
 * Sys.format("Hello, %s!", "World") // 输出: Hello, World!
 * Sys.format("Value: %d", 42)       // 输出: Value: 42
 */
public static void format(String template, Object... args) {
    // 实现...
}
```


# 第五部分：增删减原则与实施指南

### 5.1.1 兼容性优先原则

在进行任何增删减操作时，必须优先考虑向后兼容性。任何可能破坏现有代码的更改都需要经过严格评估，并提供迁移路径。

### 5.1.2 渐进式演进原则

系统演进应采取渐进式策略，避免激进的重构。新功能应作为现有系统的扩展，而不是替代。

### 5.1.3 模块化设计原则

所有更改都应遵循模块化设计原则，确保系统各组件之间的松耦合和高内聚。

## 5.2 增加功能的原则与方法

### 5.2.1 功能增加的条件

在考虑增加新功能时，必须满足以下条件：

1. **明确的需求场景**：功能必须有明确的用户需求和使用场景
2. **技术可行性**：功能必须在当前架构下技术可行
3. **性能影响可控**：新增功能不应显著降低系统性能
4. **维护成本可接受**：新增功能的长期维护成本应在可接受范围内

### 5.2.2 语法扩展指南

#### 新增关键字
```java
// 在 Lexer.java 的 KEYWORDS 映射中添加
KEYWORDS.put("newkeyword", "NEW_KEYWORD");

// 在 Parser.java 的 parseStatement() 中添加解析逻辑
if (match("NEW_KEYWORD")) {
    return parseNewKeywordStatement();
}
```

#### 新增表达式类型
```java
// 1. 创建新的表达式类
public class NewExpression extends Expression {
    // 实现细节
}

// 2. 在 ASTVisitor.java 中添加访问方法
T visitNewExpression(NewExpression expr);

// 3. 在 Interpreter.java 中实现访问逻辑
@Override
public Void visitNewExpression(NewExpression expr) {
    // 解释执行逻辑
    return null;
}
```

#### 新增语句类型
```java
// 1. 创建新的语句类
public class NewStatement extends Statement {
    // 实现细节
}

// 2. 在 Parser.java 中添加解析方法
private Statement parseNewStatement() {
    // 解析逻辑
}
```

### 5.2.3 内置库扩展

#### 新增内置类
```java
// 在 VastVM.java 的静态初始化块中注册
BUILTIN_CLASSES.put("NewClass", com.vast.internal.NewClass.class);

// 实现对应的Java类
package com.vast.internal;
public class NewClass {
    // 静态方法实现
}
```

#### 扩展现有内置类
```java
// 在现有类中添加新方法，确保不影响现有方法
public class ExistingClass {
    // 保持现有方法不变
    public static void existingMethod() { /* ... */ }
    
    // 新增方法
    public static void newMethod() { /* ... */ }
}
```

## 5.3 删除功能的原则与方法

### 5.3.1 功能删除的条件

在考虑删除功能时，必须满足以下条件：

1. **使用率极低**：功能在现实使用中几乎不被使用
2. **存在更好的替代方案**：有更优的替代实现可用
3. **维护成本过高**：功能的维护成本远超其价值
4. **技术债务**：功能存在无法修复的技术问题

### 5.3.2 删除流程

#### 第一阶段：标记废弃
```java
// 1. 在相关类和方法上添加@Deprecated注解
@Deprecated
public class DeprecatedClass {
    @Deprecated
    public static void deprecatedMethod() {
        // 实现
    }
}

// 2. 在文档中明确说明废弃原因和替代方案
```

#### 第二阶段：发出警告
```java
// 在解释器中添加废弃警告
@Override
public Void visitDeprecatedExpression(DeprecatedExpression expr) {
    System.err.println("Warning: Deprecated feature used at line " + 
                      expr.getLineNumber());
    // 继续执行原有逻辑
    return super.visitDeprecatedExpression(expr);
}
```

#### 第三阶段：完全移除
在至少一个主要版本周期后，确认没有重要依赖后完全移除。

### 5.3.3 具体删除操作

#### 删除关键字
```java
// 从 Lexer.java 的 KEYWORDS 映射中移除
// KEYWORDS.remove("oldkeyword");

// 从 Parser.java 中移除相关解析逻辑
```

#### 删除表达式/语句类型
```java
// 1. 删除对应的AST节点类
// 2. 从ASTVisitor接口中移除对应方法
// 3. 从所有实现类中移除对应实现
// 4. 从Parser中移除解析逻辑
```

## 5.4 修改功能的原则与方法

### 5.4.1 修改类型分类

#### 兼容性修改
- 性能优化（不影响接口）
- Bug修复（不改变行为约定）
- 内部重构（不改变外部行为）

#### 非兼容性修改
- 接口变更
- 行为改变
- 语法语义调整

### 5.4.2 修改实施步骤

#### 步骤1：影响分析
```java
// 分析修改影响的组件
- 词法分析器 (Lexer.java)
- 语法分析器 (Parser.java) 
- AST节点 (com.vast.ast.*)
- 解释器 (Interpreter.java)
- 内置库 (com.vast.internal.*)
```

#### 步骤2：制定迁移计划
对于非兼容性修改，必须提供：
- 详细的修改说明
- 迁移指南
- 兼容性层（如有可能）

#### 步骤3：分阶段实施
```java
// 第一阶段：添加新实现，保持旧实现
public class DualImplementation {
    // 旧实现（标记为废弃）
    @Deprecated
    public static void oldMethod() { /* ... */ }
    
    // 新实现
    public static void newMethod() { /* ... */ }
}
```

#### 步骤4：测试验证
确保修改后的功能：
- 新功能正常工作
- 现有功能不受影响
- 性能指标在可接受范围内

### 5.4.3 具体修改示例

#### 修改运算符优先级
```java
// 在 Parser.java 中调整解析方法顺序
private Expression parseExpression() {
    return parseNewPrecedenceLevel(); // 调整解析顺序
}
```

#### 修改类型系统
```java
// 在 Interpreter.java 中修改类型检查逻辑
private void validateTypeCompatibility(String expectedType, Object value, 
                                      String varName, int lineNumber, int columnNumber) {
    // 更新类型兼容性规则
}
```

## 5.5 版本管理策略

### 5.5.1 语义化版本控制

遵循严格的语义化版本控制规范：

- **主版本号**：不兼容的API修改
- **次版本号**：向下兼容的功能性新增
- **修订号**：向下兼容的问题修正

### 5.5.2 变更日志规范

所有增删减操作都必须在变更日志中记录：

```markdown
## [版本号] - YYYY-MM-DD

### 新增
- 新增功能A描述
- 新增功能B描述

### 变更
- 修改功能C的描述和原因
- 性能优化描述

### 废弃
- 废弃功能D的描述和替代方案
- 移除时间表

### 修复
- Bug修复描述
```

### 5.5.3 分支管理策略

采用功能分支工作流：

```bash
# 新功能开发
git checkout -b feature/new-feature
# 功能完成后合并到develop分支

# 修复开发  
git checkout -b fix/bug-description
# 修复完成后合并到develop和main分支

# 发布准备
git checkout -b release/version-number
# 发布测试完成后合并到main和develop分支
```

## 5.6 测试验证要求

### 5.6.1 测试覆盖要求

所有增删减操作必须包含相应的测试：

#### 语法层面测试
```java
// 新增语法测试用例
@Test
public void testNewSyntax() {
    String source = "new syntax example";
    // 验证词法分析、语法分析、解释执行都正确
}
```

#### 语义层面测试
```java
// 新增语义测试用例  
@Test
public void testNewSemantics() {
    // 验证新功能的运行时行为
}
```

#### 回归测试
```java
// 确保现有功能不受影响
@Test
public void testBackwardCompatibility() {
    // 运行现有功能的测试用例
}
```

### 5.6.2 性能测试要求

所有修改都需要进行性能影响评估：

```java
// 性能基准测试
@Benchmark
public void benchmarkNewFeature() {
    // 测量新功能的性能指标
}
```

## 5.7 文档更新要求

### 5.7.1 必须更新的文档

任何增删减操作都需要更新相应的文档：

1. **语法参考文档**：描述新的语法元素
2. **API文档**：新增或修改的类和方法文档
3. **用户指南**：使用示例和最佳实践
4. **开发者指南**：内部实现细节
5. **迁移指南**：不兼容变更的迁移说明

### 5.7.2 文档质量标准

- 所有新功能必须有完整的使用示例
- 所有废弃功能必须明确标注和提供替代方案
- 所有重大变更必须有详细的迁移说明
- 所有文档必须与代码实现保持同步

## 5.8 代码审查标准

### 5.8.1 审查清单

所有增删减操作的代码提交必须经过审查，审查清单包括：

- [ ] 功能实现正确性
- [ ] 测试覆盖完整性
- [ ] 文档更新完整性
- [ ] 性能影响评估
- [ ] 向后兼容性保证
- [ ] 代码风格一致性
- [ ] 错误处理完整性

### 5.8.2 审查流程

1. **自审**：开发者在提交前完成自审
2. **同伴审查**：至少一名其他开发者审查
3. **架构师审查**：重大变更需要架构师审查
4. **集成测试**：在合并前通过所有自动化测试

## 5.9 紧急情况处理

### 5.9.1 紧急修复流程

对于生产环境中的紧急问题：

1. 从发布分支创建热修复分支
2. 实施最小化的必要修复
3. 紧急测试和验证
4. 快速发布补丁版本
5. 事后进行根本原因分析

### 5.9.2 回滚策略

每次发布都必须有明确的回滚策略：

1. 保留前一个稳定版本的部署能力
2. 数据库迁移必须是可逆的
3. 配置变更必须是向后兼容的
4. 制定详细的回滚操作手册

## 5.10 长期维护考虑

### 5.10.1 技术债务管理

定期进行技术债务评估：

1. **代码质量评估**：静态分析工具结果审查
2. **架构健康度评估**：系统架构的演化方向
3. **依赖管理**：第三方依赖的更新和维护
4. **性能基准**：系统性能趋势分析

### 5.10.2 生命周期管理

为每个功能组件定义明确的生命周期：

- **引入期**：新功能的测试和稳定阶段
- **成熟期**：功能稳定，广泛使用阶段
- **维护期**：功能稳定，仅进行必要维护
- **废弃期**：功能标记为废弃，准备移除
- **移除期**：功能从系统中完全移除