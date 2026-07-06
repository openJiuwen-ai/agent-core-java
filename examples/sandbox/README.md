# Sandbox Execution Java Example

这个目录演示如何在 Java 框架里通过 JiuwenBox 远程沙箱服务执行命令、处理文件和运行代码：

1. 创建 `OperationMode.SANDBOX` 模式的 SysOperation，自动连接 jiuwenbox 沙箱
2. 通过 `JiuwenBoxSandboxProfile.config()` 配置沙箱网关
3. 直接调用 SysOperation 的 FS / Shell / Code API 验证沙箱执行能力
4. 支持 DeepAgent 和 ReActAgent 在沙箱模式下使用 Skill 执行任务
5. 支持沙箱失败回退到本地执行（fallback_on_failure）

## 核心机制

使用 `JiuwenBoxSandboxProfile` 创建沙箱模式的 SysOperation：

```java
SysOperationCard sysOpCard = SysOperationCard.builder()
    .id("sandbox_sysop")
    .mode(OperationMode.SANDBOX)
    .gatewayConfig(JiuwenBoxSandboxProfile.config(SANDBOX_URL))
    .build();
Runner.resourceMgr().addSysOperation(sysOpCard, null);

SysOperation sysOp = Runner.resourceMgr().getSysOperation("sandbox_sysop");
sysOp.shell().executeCmd("echo hello_from_sandbox", ".", 30, null, null);
sysOp.fs().writeFile("/tmp/test.txt", "content", "text", false, false, true, null, null, null);
sysOp.code().executeCode("print('hello')", "python", 30, null, null);
```

回退配置（沙箱不可用时自动切换到本地）：

```java
SandboxGatewayConfig fallbackConfig = JiuwenBoxSandboxProfile.config(SANDBOX_URL,
    SandboxOperationSupport.params(new Object[]{
        "fallback_on_failure", true,
        "excluded_commands", List.of("dangerous_*")
    }));
```

## 文件说明

- `SandboxExample.java`: 示例入口，包含 10 个场景演示。
- `run.ps1`: 一键编译运行脚本（PowerShell）。
- `../utils/SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置（场景 2/4/5b 需要）。
- `skills/disk_analyzer/SKILL.md`: 场景 2 Skill — 在沙箱中执行磁盘分析命令。
- `skills/data_processor/SKILL.md`: 场景 4 Skill — 在沙箱中安装 Python 依赖并运行数据分析。

## 运行前提

### 1. jiuwenbox 服务

[jiuwenbox](https://gitcode.com/openJiuwen/jiuwenswarm/blob/develop/jiuwenbox/README_CN.md) 是一个轻量级 Linux 沙箱服务，用于在分层隔离环境中运行 agent 工具和代码片段。它基于 `bubblewrap` 提供进程隔离，支持文件系统访问控制、网络隔离、Seccomp 系统调用过滤等功能，并通过 FastAPI 服务管理沙箱生命周期、文件传输和命令执行。

本示例需要 jiuwenbox 服务运行在 `http://127.0.0.1:8321`。启动方式：

```bash
jiuwenbox-server start
```

验证服务是否可用：

```bash
curl http://127.0.0.1:8321/health
```

预期返回：

```json
{"status": "ok", "version": "...", "sandboxes_active": 0}
```

### 2. LLM API 配置

场景 2、4、5b 依赖 LLM 接口驱动 Agent 执行 Skill。编辑 `examples/apiconfig.json`，填入真实模型配置。

场景 6-10 不依赖 LLM，仅需 jiuwenbox 服务即可运行。

### 3. 从当前 Java 仓库根目录运行

也就是包含 `pom.xml`、`examples` 和 `src` 的目录。

## 关键环境变量与属性

- `SANDBOX_URL`: jiuwenbox 沙箱服务地址。默认是 `http://127.0.0.1:8321`。
- `SKILLS_DIR`: 本地 skill 目录。默认是 `examples/sandbox/skills`。
- `MAX_ITERATIONS`: 最大推理轮数。默认是 `15`。
- `OPENJIUWEN_API_CONFIG`: 可选。显式指定 `apiconfig.json` 路径。
- `openjiuwen.example.config`: 可选。通过 JVM system property 显式指定 `apiconfig.json` 路径。

环境变量覆盖优先级：`System.getenv(key)` > `System.getProperty(key)` > 默认值。

## 运行方式

### 方式一：一键脚本（推荐）

直接运行 `run.ps1`，脚本会自动编译项目、构建 classpath、编译示例并运行：

```powershell
cd examples/sandbox
.\run.ps1
```

### 方式二：手动编译运行

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -DskipTests -q
javac -source 17 -target 17 -cp "target/classes;target/dependency/*" -d examples/sandbox/build examples/utils/SharedExampleApiConfigLoader.java examples/sandbox/SandboxExample.java
java -Dfile.encoding=UTF-8 -cp "examples/sandbox/build;target/classes;target/dependency/*" examples.sandbox.SandboxExample
```

### 指定 Agent 类型

默认使用 DeepAgent。传入 `react` 参数切换到 ReActAgent：

```powershell
java -cp "examples/sandbox/build;target/classes;target/dependency/*" examples.sandbox.SandboxExample react
```

## 场景说明

当前默认启用场景 6-10（直接调用 SysOperation API），场景 1-5 和 5b 已注释（需要手动取消注释启用）。

### 场景 1：普通命令使用沙箱

创建 SANDBOX 模式 SysOperation，执行 `echo hello_from_sandbox`，验证命令在远程沙箱中运行。

### 场景 2：Skill 技能中的命令使用沙箱

创建 Agent（DeepAgent/ReActAgent），配置 SANDBOX SysOperation，注册 `disk_analyzer` Skill。Agent 在 Skill 指导下通过沙箱执行磁盘分析命令。

**需要 LLM API 配置。**

### 场景 3：沙箱执行失败后回退到本地

配置 `fallback_on_failure=true` + `excluded_commands`，演示沙箱不可用时自动回退到 LOCAL 模式。

### 场景 4：Skill 中复杂命令（Python脚本 + 依赖安装）

注册 `data_processor` Skill，在沙箱中 `pip install numpy` 然后运行数据分析脚本。pip install 只在沙箱生效，本地不受影响。

**需要 LLM API 配置。**

### 场景 5：到期删除沙箱

通过 `SandboxGatewayClient.release(isolationKey, "delete")` 释放并删除沙箱实例。

### 场景 5b：Agent 读写文件（含 lineRange）

Agent 在沙箱中写入文件并按行范围读取，验证 Agent 工具调用链路。

**需要 LLM API 配置。**

### 场景 6：FS readFile / writeFile

直接调用 `fs.writeFile()` 和 `fs.readFile()` 验证文件读写，包括 head/tail/lineRange/bytes 模式。

### 场景 7：FS listFiles / listDirectories / searchFiles

创建目录结构，验证递归列表和文件搜索功能。

### 场景 8：FS uploadFile / downloadFile

从本地上传文件到沙箱，再从沙箱下载到本地，验证数据传输。

### 场景 9：Shell executeCmdStream / executeCmdBackground

验证流式命令输出和后台命令执行。

### 场景 10：Code executeCode / executeCodeStream

在沙箱中执行 Python 和 JavaScript 代码，验证代码运行和流式输出。

## 预期输出（场景 6-10）

```
=== Sandbox Execution Demo (Agent: DeepAgent) ===

--- Scenario 6: FS readFile / writeFile ---
[Sandbox] Scenario 6: FS readFile / writeFile verification
[Sandbox] writeFile: path=/tmp/sandbox_verify_test.txt size=...
[Sandbox] readFile (full): line1\nline2\nline3\nline4\nline5
[Sandbox] readFile (head=2): line1\nline2
[Sandbox] readFile (tail=2): line4\nline5
[Sandbox] readFile (lineRange=[1,3]): line2\nline3

--- Scenario 7: FS listFiles / listDirectories / searchFiles ---
[Sandbox] Scenario 7: FS listFiles / listDirectories / searchFiles verification
[Sandbox] listFiles (recursive): totalCount=...

--- Scenario 8: FS uploadFile / downloadFile ---
[Sandbox] uploadFile: localPath=... targetPath=/tmp/sandbox_upload_target.txt

--- Scenario 9: Shell executeCmdStream / executeCmdBackground ---
[Sandbox] executeCmdStream total chunks: 3

--- Scenario 10: Code executeCode / executeCodeStream ---
[Sandbox] executeCode (python): stdout=hello_python_sandbox
=== Demo Complete ===
```

## 说明

- 沙箱命令在远程 Linux 环境中执行，与本地 Windows 环境完全隔离。
- `pip install numpy` 只在沙箱生效，本地环境不受影响。
- 场景 1-5 和 5b 需要取消注释才能运行，其中场景 2、4、5b 还需要 LLM API 配置。
- Java 系统工具名采用框架内的 camelCase 形式：`readFile`、`executeCode`、`executeCmd`。
