# SysOp 模块 API 文档

> 包路径：`com.openjiuwen.core.sysop`

SysOp 模块提供系统操作能力，包括 Shell 命令执行、代码执行、文件系统操作，支持本地（Local）和沙箱（Sandbox）两种运行模式，并通过注解驱动的注册机制进行操作发现和管理。

---

## 目录

- [1. 核心抽象类](#1-核心抽象类)
- [2. 门面与卡片](#2-门面与卡片)
- [3. 配置（config）](#3-配置config)
- [4. 注册中心（registry）](#4-注册中心registry)
- [5. 本地实现（local）](#5-本地实现local)
- [6. 沙箱实现（sandbox）](#6-沙箱实现sandbox)
- [7. 结果模型（result）](#7-结果模型result)

---

## 1. 核心抽象类

### 1.1 OperationMode（枚举）

操作运行模式枚举。

**包路径**：`com.openjiuwen.core.sysop`

| 枚举值 | 字符串值 | 说明 |
|--------|----------|------|
| `LOCAL` | `"local"` | 本地模式 |
| `SANDBOX` | `"sandbox"` | 沙箱模式 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `String` | 获取字符串值 |
| `fromString(String text)` | `OperationMode` | 大小写不敏感解析（静态） |

### 1.2 BaseOperation（抽象类）

所有系统操作的基类。

**包路径**：`com.openjiuwen.core.sysop`

**构造方法**：
```java
protected BaseOperation(String name, OperationMode mode, String description, Object runConfig)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getName()` | `String` | 获取操作名称 |
| `getMode()` | `OperationMode` | 获取运行模式 |
| `getDescription()` | `String` | 获取描述 |
| `getRunConfig()` | `Object` | 获取运行配置 |
| `getLocalConfig()` | `LocalWorkConfig` | 获取本地配置（受保护） |
| `getSandboxConfig()` | `SandboxGatewayConfig` | 获取沙箱配置（受保护） |
| `listTools()` | `List<ToolCard>` | 列出工具卡片（抽象） |
| `generateToolCards(List<String> methodNames)` | `List<ToolCard>` | 从方法名生成工具卡片（受保护） |
| `buildInputSchema(Method method)` | `Map<String, Object>` | 构建输入 Schema（受保护） |
| `buildParameterSchema(Parameter parameter)` | `Map<String, Object>` | 构建参数 Schema（受保护） |
| `buildArrayItemSchema(Class<?> parameterType, Type genericType)` | `Map<String, Object>` | 构建数组项 Schema（受保护） |

### 1.3 BaseShellOperation（抽象类）

Shell 操作基类。

**包路径**：`com.openjiuwen.core.sysop`  
**继承**：`BaseOperation`

**构造方法**：
```java
protected BaseShellOperation(String name, OperationMode mode, String description, Object runConfig)
```

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCmdResult` | 执行 Shell 命令 |
| `executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCmdStreamResult>` | 流式执行 Shell 命令 |

**具体方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `listTools()` | `List<ToolCard>` | 返回 executeCmd、executeCmdStream 工具卡片 |

### 1.4 BaseCodeOperation（抽象类）

代码执行操作基类。

**包路径**：`com.openjiuwen.core.sysop`  
**继承**：`BaseOperation`

**构造方法**：
```java
protected BaseCodeOperation(String name, OperationMode mode, String description, Object runConfig)
```

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCodeResult` | 执行代码 |
| `executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCodeStreamResult>` | 流式执行代码 |

**具体方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `listTools()` | `List<ToolCard>` | 返回 executeCode、executeCodeStream 工具卡片 |

### 1.5 BaseFsOperation（抽象类）

文件系统操作基类。

**包路径**：`com.openjiuwen.core.sysop`  
**继承**：`BaseOperation`

**构造方法**：
```java
protected BaseFsOperation(String name, OperationMode mode, String description, Object runConfig)
```

**抽象方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `ReadFileResult` | 读取文件 |
| `readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `Iterator<ReadFileStreamResult>` | 流式读取文件 |
| `writeFile(String path, String content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | `WriteFileResult` | 写入文件 |
| `uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `UploadFileResult` | 上传文件 |
| `uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<UploadFileStreamResult>` | 流式上传文件 |
| `downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `DownloadFileResult` | 下载文件 |
| `downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<DownloadFileStreamResult>` | 流式下载文件 |
| `listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | `ListFilesResult` | 列出文件 |
| `listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | `ListDirsResult` | 列出目录 |
| `searchFiles(String path, String pattern, List<String> excludePatterns)` | `SearchFilesResult` | 搜索文件 |

**具体方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `listTools()` | `List<ToolCard>` | 返回全部 10 个文件操作工具卡片 |

### 1.6 FsConstants

文件系统常量。

**包路径**：`com.openjiuwen.core.sysop`

| 常量名 | 类型 | 值 | 说明 |
|--------|------|-----|------|
| `DEFAULT_READ_CHUNK_SIZE` | `int` | `0` | 默认读取块大小 |
| `DEFAULT_UPLOAD_CHUNK_SIZE` | `int` | `0` | 默认上传块大小 |
| `DEFAULT_DOWNLOAD_CHUNK_SIZE` | `int` | `0` | 默认下载块大小 |
| `DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE` | `int` | `1048576` | 默认流式下载块大小（1MB） |
| `DEFAULT_UPLOAD_STREAM_CHUNK_SIZE` | `int` | `1048576` | 默认流式上传块大小（1MB） |
| `DEFAULT_READ_STREAM_CHUNK_SIZE` | `int` | `8192` | 默认流式读取块大小 |
| `TAIL_CHUNK_SIZE` | `int` | `1024` | 尾部读取块大小 |

---

## 2. 门面与卡片

### 2.1 SysOperation

系统操作门面类，统一管理 Shell、Code、Fs 三种操作。

**包路径**：`com.openjiuwen.core.sysop`

**构造方法**：
```java
SysOperation(SysOperationCard card)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fs()` | `BaseFsOperation` | 获取文件系统操作 |
| `code()` | `BaseCodeOperation` | 获取代码执行操作 |
| `shell()` | `BaseShellOperation` | 获取 Shell 操作 |
| `getOperation(String name)` | `BaseOperation` | 按名称获取操作（不存在返回 null） |
| `getMode()` | `OperationMode` | 获取运行模式 |

### 2.2 SysOperationCard

系统操作卡片配置类。

**包路径**：`com.openjiuwen.core.sysop`  
**继承**：`BaseCard`  
**注解**：`@Data`, `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@EqualsAndHashCode(callSuper = true)`

| 字段 | 类型 | 说明 |
|------|------|------|
| `mode` | `OperationMode` | 运行模式 |
| `workConfig` | `LocalWorkConfig` | 本地运行配置 |
| `gatewayConfig` | `SandboxGatewayConfig` | 沙箱网关配置 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `validateMode(String modeValue)` | `OperationMode` | 验证并解析模式（静态） |
| `generateToolId(String cardId, String opType, String methodName)` | `String` | 生成工具 ID `{cardId}.{opType}.{methodName}`（静态） |
| `fs()` | `ToolIdProxy` | 获取文件系统工具 ID 代理 |
| `shell()` | `ToolIdProxy` | 获取 Shell 工具 ID 代理 |
| `code()` | `ToolIdProxy` | 获取代码工具 ID 代理 |
| `proxy(String opType)` | `ToolIdProxy` | 获取指定类型的工具 ID 代理 |

### 2.3 ToolIdProxy

工具 ID 代理，生成格式化的工具标识符。

**包路径**：`com.openjiuwen.core.sysop`

**构造方法**：
```java
ToolIdProxy(String cardId, String opType)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toolId(String methodName)` | `String` | 返回 `{cardId}.{opType}.{methodName}` |
| `getCardId()` | `String` | 获取卡片 ID |
| `getOpType()` | `String` | 获取操作类型 |

### 2.4 SysOperationToolAdapter

系统操作工具适配器，将操作方法转换为 `LocalFunction`。

**包路径**：`com.openjiuwen.core.sysop`

**内部记录**：
```java
record ToolEntry(String toolId, LocalFunction localFunction)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `extractTools(SysOperationCard card, SysOperation instance)` | `List<ToolEntry>` | 提取全部工具（静态） |
| `getToolIdPrefix(String sysOperationId)` | `String` | 获取工具 ID 前缀（静态） |

---

## 3. 配置（config）

### 3.1 LocalWorkConfig

本地运行配置。

**包路径**：`com.openjiuwen.core.sysop.config`  
**注解**：`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `shellAllowlist` | `List<String>` | echo, ls, dir, cd, pwd, python, python3, pip, pip3, npm, node, git, cat, type, mkdir, md, rm, rd, cp, copy, mv, move, grep, find, curl, wget, ps, df, ping | Shell 命令白名单 |
| `workDir` | `String` | `null` | 工作目录 |

### 3.2 SandboxGatewayConfig

沙箱网关配置。

**包路径**：`com.openjiuwen.core.sysop.config`  
**注解**：`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `gatewayUrl` | `String` | `""` | 网关 URL |
| `params` | `Map<String, Object>` | `new HashMap<>()` | 参数 |
| `authHeaders` | `Map<String, String>` | `new HashMap<>()` | 认证头 |
| `authQueryParams` | `Map<String, String>` | `new HashMap<>()` | 认证查询参数 |

---

## 4. 注册中心（registry）

### 4.1 @Operation（注解）

标注操作实现类，用于自动发现和注册。

**包路径**：`com.openjiuwen.core.sysop.registry`  
**目标**：`ElementType.TYPE`  
**生命周期**：`RetentionPolicy.RUNTIME`

| 属性 | 类型 | 说明 |
|------|------|------|
| `name()` | `String` | 操作唯一标识 |
| `mode()` | `OperationMode` | 运行模式 |
| `description()` | `String` | 描述（默认空） |

### 4.2 OperationDef

操作定义，包含操作类元信息。

**包路径**：`com.openjiuwen.core.sysop.registry`

**构造方法**：
```java
OperationDef(Class<? extends BaseOperation> cls, String name, OperationMode mode, String description)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createInstance(Object runConfig)` | `BaseOperation` | 创建操作实例（先尝试 4 参数构造器，再回退 1 参数） |
| `getCls()` | `Class<? extends BaseOperation>` | 获取操作类 |
| `getDescription()` | `String` | 获取描述 |
| `getName()` | `String` | 获取名称 |
| `getMode()` | `OperationMode` | 获取模式 |

### 4.3 OperationRegistry

操作注册中心，按模式注册和查找操作。

**包路径**：`com.openjiuwen.core.sysop.registry`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `register(Class<? extends BaseOperation> operationCls, String name, OperationMode mode, String description)` | `void` | 注册操作（静态） |
| `register(Class<? extends BaseOperation> operationCls)` | `void` | 使用 @Operation 注解注册（静态） |
| `getOperationInfo(String name, OperationMode mode)` | `Optional<OperationDef>` | 查找操作定义（静态） |
| `getSupportedOperations(OperationMode mode)` | `List<String>` | 获取指定模式支持的操作名（排序，静态） |
| `clear()` | `void` | 清空注册表（测试用，静态） |

---

## 5. 本地实现（local）

### 5.1 LocalShellOperation

本地 Shell 操作实现。

**包路径**：`com.openjiuwen.core.sysop.local`  
**继承**：`BaseShellOperation`  
**注解**：`@Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation")`

**构造方法**：`LocalShellOperation(Object runConfig)`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCmdResult` | 执行 Shell 命令 |
| `executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCmdStreamResult>` | 流式执行 Shell 命令 |

### 5.2 LocalCodeOperation

本地代码执行操作实现。

**包路径**：`com.openjiuwen.core.sysop.local`  
**继承**：`BaseCodeOperation`  
**注解**：`@Operation(name = "code", mode = OperationMode.LOCAL, description = "local code operation")`

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `WINDOWS_CMD_LIMIT` | `8000` | Windows 命令长度限制 |
| `UNIX_CMD_LIMIT` | `100000` | Unix 命令长度限制 |

**构造方法**：`LocalCodeOperation(Object runConfig)`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCodeResult` | 执行代码 |
| `executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCodeStreamResult>` | 流式执行代码 |

### 5.3 LocalFsOperation

本地文件系统操作实现。

**包路径**：`com.openjiuwen.core.sysop.local`  
**继承**：`BaseFsOperation`  
**注解**：`@Operation(name = "fs", mode = OperationMode.LOCAL, description = "local fs operation")`

**构造方法**：`LocalFsOperation(Object runConfig)`

实现 `BaseFsOperation` 的全部 10 个抽象方法。

### 5.4 ProcessHandler

进程处理器，管理外部进程的执行和输出。

**包路径**：`com.openjiuwen.core.sysop.local`

**构造方法**：
```java
ProcessHandler(Process process, int chunkSize, Charset encoding, int overallTimeoutSeconds)
ProcessHandler(Process process)  // 默认：chunkSize=1024, UTF-8, timeout=300s
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke()` | `InvokeData` | 一次性执行（与 stream 互斥） |
| `stream()` | `Iterator<StreamEvent>` | 流式执行（与 invoke 互斥） |

### 5.5 InvokeData

进程调用结果数据。

**包路径**：`com.openjiuwen.core.sysop.local`  
**注解**：`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `stdout` | `String` | 标准输出 |
| `stderr` | `String` | 标准错误 |
| `exitCode` | `int` | 退出码 |
| `exception` | `Exception` | 异常 |

### 5.6 StreamEvent

流式进程事件。

**包路径**：`com.openjiuwen.core.sysop.local`  
**注解**：`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `StreamEventType` | 事件类型 |
| `data` | `String` | 事件数据 |
| `timestamp` | `Instant` | 时间戳（默认 `Instant.now()`） |

### 5.7 StreamEventType（枚举）

流事件类型枚举。

| 枚举值 | 字符串值 | 说明 |
|--------|----------|------|
| `STDOUT` | `"stdout"` | 标准输出 |
| `STDERR` | `"stderr"` | 标准错误 |
| `EXIT` | `"exit"` | 进程退出 |
| `ERROR` | `"error"` | 错误 |

### 5.8 OperationUtils

操作工具类。

**包路径**：`com.openjiuwen.core.sysop.local`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createTmpFile(String fileContent, String fileSuffix)` | `String` | 创建临时文件并返回绝对路径（静态） |
| `deleteTmpFile(String filePath)` | `boolean` | 删除临时文件（静态） |
| `prepareEnvironment(Map<String, String> customEnv)` | `Map<String, String>` | 合并系统环境变量与自定义变量（静态） |
| `createHandler(Process process, int chunkSize, Charset encoding, int timeout)` | `ProcessHandler` | 创建进程处理器（静态） |
| `createHandler(Process process)` | `ProcessHandler` | 使用默认参数创建处理器（静态） |

---

## 6. 沙箱实现（sandbox）

> 当前版本中沙箱操作为占位实现，所有方法均抛出 `UnsupportedOperationException`。

### 6.1 SandboxShellOperation

沙箱 Shell 操作（占位）。

**包路径**：`com.openjiuwen.core.sysop.sandbox`  
**继承**：`BaseShellOperation`  
**注解**：`@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation")`

### 6.2 SandboxCodeOperation

沙箱代码执行操作（占位）。

**包路径**：`com.openjiuwen.core.sysop.sandbox`  
**继承**：`BaseCodeOperation`  
**注解**：`@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation")`

### 6.3 SandboxFsOperation

沙箱文件系统操作（占位）。

**包路径**：`com.openjiuwen.core.sysop.sandbox`  
**继承**：`BaseFsOperation`  
**注解**：`@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")`

---

## 7. 结果模型（result）

### 7.1 BaseResult\<T\>（抽象类）

操作结果泛型基类。

**包路径**：`com.openjiuwen.core.sysop.result`  
**注解**：`@Data`, `@SuperBuilder`, `@NoArgsConstructor`  
**类型参数**：`<T>` — 数据类型

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 状态码 |
| `message` | `String` | 消息 |
| `data` | `T` | 数据 |

**构造方法**：
```java
protected BaseResult(int code, String message, T data)
```

**静态方法**：

| 方法签名 | 说明 |
|----------|------|
| `buildOperationErrorResult(StatusCode errorType, Map<String, String> msgFormatKwargs, ResultFactory<R> resultFactory, T data)` | 构建错误结果 |
| `buildOperationErrorResult(StatusCode errorType, String execution, String errorMsg, ResultFactory<R> resultFactory, Object data)` | 构建错误结果（便捷版） |

**内部接口**：`@FunctionalInterface interface ResultFactory<R> extends Supplier<R>`

### 7.2 命令执行结果

#### ExecuteCmdResult

Shell 命令执行结果。

**继承**：`BaseResult<ExecuteCmdData>`

#### ExecuteCmdData

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `command` | `String` | - | 执行的命令 |
| `cwd` | `String` | `"."` | 工作目录 |
| `exitCode` | `Integer` | - | 退出码 |
| `stdout` | `String` | `""` | 标准输出 |
| `stderr` | `String` | `""` | 标准错误 |

#### ExecuteCmdStreamResult

Shell 命令流式结果。

**继承**：`BaseResult<ExecuteCmdChunkData>`

#### ExecuteCmdChunkData

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `text` | `String` | `""` | 文本块 |
| `type` | `String` | - | 块类型（stdout/stderr） |
| `chunkIndex` | `int` | - | 块索引 |
| `exitCode` | `Integer` | - | 退出码 |
| `metadata` | `Map<String, Object>` | - | 元数据 |

### 7.3 代码执行结果

#### ExecuteCodeResult

代码执行结果。

**继承**：`BaseResult<ExecuteCodeData>`

#### ExecuteCodeData

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `codeContent` | `String` | - | 代码内容 |
| `language` | `String` | - | 编程语言 |
| `exitCode` | `Integer` | - | 退出码 |
| `stdout` | `String` | `""` | 标准输出 |
| `stderr` | `String` | `""` | 标准错误 |

#### ExecuteCodeStreamResult

代码流式执行结果。

**继承**：`BaseResult<ExecuteCodeChunkData>`

#### ExecuteCodeChunkData

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `text` | `String` | `""` | 文本块 |
| `type` | `String` | - | 块类型 |
| `chunkIndex` | `int` | - | 块索引 |
| `exitCode` | `Integer` | - | 退出码 |
| `metadata` | `Map<String, Object>` | - | 元数据 |

### 7.4 文件读取结果

#### ReadFileResult

文件读取结果。

**继承**：`BaseResult<ReadFileData>`

#### ReadFileData

| 字段 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | 文件路径 |
| `content` | `String` | 文件内容 |
| `mode` | `String` | 读取模式 |

#### ReadFileStreamResult

文件流式读取结果。

**继承**：`BaseResult<ReadFileChunkData>`

#### ReadFileChunkData

| 字段 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | 文件路径 |
| `chunkContent` | `String` | 块内容 |
| `mode` | `String` | 读取模式 |
| `chunkSize` | `int` | 块大小 |
| `chunkIndex` | `int` | 块索引 |
| `lastChunk` | `boolean` | 是否为最后一块 |

### 7.5 文件写入结果

#### WriteFileResult

文件写入结果。

**继承**：`BaseResult<WriteFileData>`

#### WriteFileData

| 字段 | 类型 | 说明 |
|------|------|------|
| `path` | `String` | 文件路径 |
| `size` | `int` | 写入大小 |
| `mode` | `String` | 写入模式 |

### 7.6 文件上传结果

#### UploadFileResult

文件上传结果。

**继承**：`BaseResult<UploadFileData>`

#### UploadFileData

| 字段 | 类型 | 说明 |
|------|------|------|
| `localPath` | `String` | 本地路径 |
| `targetPath` | `String` | 目标路径 |
| `size` | `long` | 文件大小 |

#### UploadFileStreamResult

文件流式上传结果。

**继承**：`BaseResult<UploadFileChunkData>`

#### UploadFileChunkData

| 字段 | 类型 | 说明 |
|------|------|------|
| `localPath` | `String` | 本地路径 |
| `targetPath` | `String` | 目标路径 |
| `chunkSize` | `int` | 块大小 |
| `chunkIndex` | `int` | 块索引 |
| `lastChunk` | `boolean` | 是否为最后一块 |

### 7.7 文件下载结果

#### DownloadFileResult

文件下载结果。

**继承**：`BaseResult<DownloadFileData>`

#### DownloadFileData

| 字段 | 类型 | 说明 |
|------|------|------|
| `sourcePath` | `String` | 源路径 |
| `localPath` | `String` | 本地路径 |
| `size` | `long` | 文件大小 |

#### DownloadFileStreamResult

文件流式下载结果。

**继承**：`BaseResult<DownloadFileChunkData>`

#### DownloadFileChunkData

| 字段 | 类型 | 说明 |
|------|------|------|
| `sourcePath` | `String` | 源路径 |
| `localPath` | `String` | 本地路径 |
| `chunkSize` | `int` | 块大小 |
| `chunkIndex` | `int` | 块索引 |
| `lastChunk` | `boolean` | 是否为最后一块 |

### 7.8 文件列表结果

#### ListFilesResult / ListDirsResult

文件/目录列表结果。

**继承**：`BaseResult<FileSystemData>`

#### FileSystemData

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalCount` | `int` | 总数 |
| `listItems` | `List<FileSystemItem>` | 项目列表 |
| `rootPath` | `String` | 根路径 |
| `recursive` | `boolean` | 是否递归 |
| `maxDepth` | `Integer` | 最大深度 |

#### FileSystemItem

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 名称 |
| `path` | `String` | 路径 |
| `size` | `long` | 大小 |
| `modifiedTime` | `String` | 修改时间 |
| `directory` | `boolean` | 是否为目录 |
| `type` | `String` | 类型 |

### 7.9 文件搜索结果

#### SearchFilesResult

文件搜索结果。

**继承**：`BaseResult<SearchFilesData>`

#### SearchFilesData

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalMatches` | `int` | 匹配总数 |
| `matchingFiles` | `List<FileSystemItem>` | 匹配文件列表 |
| `searchPath` | `String` | 搜索路径 |
| `searchPattern` | `String` | 搜索模式 |
| `excludePatterns` | `List<String>` | 排除模式列表 |
