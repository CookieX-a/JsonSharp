# Contents
## [en-us](https://github.com/CookieX-a/JSharpScript/tree/main#j---json-script-language)
## [zh-cn](https://github.com/CookieX-a/JSharpScript#j---json-%E8%84%9A%E6%9C%AC%E8%AF%AD%E8%A8%80)
# J# - JSON Script Language

J# is a JSON-driven scripting language. It executes **standard JSON objects** as commands, supporting variables, arithmetic, conditions, loops, functions, modules, file I/O, arrays, strings, math, networking, time, type conversion, Base64, regular expressions, and more. It features a built-in REPL and multi-project parallel execution.

---

## 🚀 Features

- **Standard JSON syntax** — every command is a valid JSON object
- **Variables & expressions** — `set` supports arithmetic and string interpolation
- **Arithmetic & comparisons** — `add`, `sub`, `mul`, `div`, `eq`, `neq`, `gt`, `lt`, `gte`, `lte`
- **Compound conditions** — `&&` and `||` with short-circuit evaluation
- **Control flow** — `if` / `elseif` / `else` / `endif`, `while` / `endwhile`
- **Functions** — user-defined with parameters and return values (inline expansion)
- **Cross-project calls** — `call project:"name"` to reuse other project blocks
- **File I/O** — `writefile`, `readfile`
- **Arrays** — create, add, get, set, size, remove
- **Strings** — length, substring, replace, trim, upper/lower, concat, split, join
- **Math** — sqrt, abs, ceil, floor, round, sin, cos, log, log10, pow, random
- **Networking** — `http_get`, `http_post`
- **Time** — `time_now`
- **Type conversion** — `conv_int`, `conv_float`, `conv_str`
- **Base64** — encode/decode
- **Regex** — match, find, replace
- **Sleep** — pause execution for milliseconds
- **Multi-project parallel execution** — independent threads, clean output
- **REPL** — interactive environment, persistent variables
- **Verbose version info** — `-version` flag

---

## 📦 Installation

1. Ensure you have **JDK 21 or higher** installed.
2. Clone or download this repository.
3. Compile the source files:

```bash
javac Command.java FuncDef.java Parser.java Runtime.java JSharp.java
```

4. Optionally, create a run script (Windows example):

```bat
@echo off
java JSharp %*
```

---

## 🛠 Usage

### Run a script
```bash
java JSharp script.jsharp
```

### Run a specific project inside a script
```bash
java JSharp script.jsharp ProjectName
```

### Start the REPL
```bash
java JSharp
```

### Show version info
```bash
java JSharp -version
```

### Or use a Jar file
Windows(bat):
```bat
java -jar "%~dp0jsharp.jar" %*
```
Linux/macOS(bash)
```bash
java -jar "$(dirname "$0")/jsharp.jar" "$@"
```


---

## ✍️ Script Example

Create a file `hello.jsharp`:

```json
{"project": {
    "name": "Demo",
    "file": "./demo.txt"
}}

{"set": {
    "name": "x",
    "value": 10
}}

{"set": {
    "name": "y",
    "value": 5
}}

{"set": {
    "name": "z",
    "value": "$x + $y * 2"
}}

{"print": {
    "type": "text",
    "text": "Result: $z"
}}

{"if": {
    "cond": "$z > 15",
    "id": "check"
}}

{"print": {
    "type": "text",
    "text": "z is greater than 15"
}}

{"else": {
    "id": "check"
}}

{"print": {
    "type": "text",
    "text": "z is not greater than 15"
}}

{"endif": {
    "id": "check"
}}
```

Run it:
```bash
java JSharp hello.jsharp
```

---

## 📝 Language Rules

- Every file must have `.jsharp` extension.
- Each JSON object must be separated by **at least one newline**.
- No comments allowed (the language is pure JSON).
- A script must contain at least one `{"project": {...}}` block.

---

# J# - JSON 脚本语言

J# 是一门 JSON 驱动的脚本语言。它将 **标准 JSON 对象** 作为命令执行，支持变量、算术、条件、循环、函数、模块、文件读写、数组、字符串、数学、网络、时间、类型转换、Base64、正则表达式等。内置 REPL 交互环境和多项目并行执行。

---

## 🚀 特性

- **标准 JSON 语法** — 每条命令是一个合法的 JSON 对象
- **变量与表达式** — `set` 支持算术运算和字符串插值
- **算术与比较** — 加减乘除、等于、不等、大于、小于等
- **复合条件** — `&&` 和 `||` 短路求值
- **控制流** — `if` / `elseif` / `else` / `endif`，`while` / `endwhile`
- **函数** — 支持参数和返回值（内联展开）
- **跨项目调用** — `call project:"name"` 复用其他项目块
- **文件 I/O** — `writefile`, `readfile`
- **数组** — 创建、添加、获取、设置、大小、删除
- **字符串** — 长度、子串、替换、去空白、大小写、拼接、分割、连接
- **数学** — 开方、绝对值、上下取整、四舍五入、三角函数、对数、幂、随机数
- **网络** — `http_get`, `http_post`
- **时间** — `time_now`
- **类型转换** — 转为整数、浮点数、字符串
- **Base64** — 编码与解码
- **正则** — 匹配、查找、替换
- **暂停** — 毫秒级等待
- **多项目并行执行** — 独立线程，清晰输出
- **REPL** — 交互环境，变量持久化
- **详细版本信息** — `-version` 参数

---

## 📦 安装

1. 确保已安装 **JDK 21 或更高版本**。
2. 克隆或下载本仓库。
3. 编译源文件：

```bash
javac Command.java FuncDef.java Parser.java Runtime.java JSharp.java
```

4. 可选，创建启动脚本（Windows 示例）：

```bat
@echo off
java JSharp %*
```

---

## 🛠 使用

### 运行脚本
```bash
java JSharp script.jsharp
```

### 运行脚本中的指定项目
```bash
java JSharp script.jsharp ProjectName
```

### 启动 REPL
```bash
java JSharp
```

### 显示版本信息
```bash
java JSharp -version
```

### 或者使用Jar
Windows(bat):
```bat
java -jar "%~dp0jsharp.jar" %*
```
Linux/macOS(bash)
```bash
java -jar "$(dirname "$0")/jsharp.jar" "$@"
```

---

## ✍️ 脚本示例

创建文件 `hello.jsharp`：

```json
{"project": {
    "name": "Demo",
    "file": "./demo.txt"
}}

{"set": {
    "name": "x",
    "value": 10
}}

{"set": {
    "name": "y",
    "value": 5
}}

{"set": {
    "name": "z",
    "value": "$x + $y * 2"
}}

{"print": {
    "type": "text",
    "text": "Result: $z"
}}

{"if": {
    "cond": "$z > 15",
    "id": "check"
}}

{"print": {
    "type": "text",
    "text": "z is greater than 15"
}}

{"else": {
    "id": "check"
}}

{"print": {
    "type": "text",
    "text": "z is not greater than 15"
}}

{"endif": {
    "id": "check"
}}
```

运行：
```bash
java JSharp hello.jsharp
```

---

## 📝 语言规则

- 文件后缀必须为 `.jsharp`。
- 每个 JSON 对象之间至少用一个换行分隔。
- 不支持注释（语言基于纯 JSON）。
- 脚本必须包含至少一个 `{"project": {...}}` 块。

---

*Enjoy J#!*
