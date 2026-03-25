# SysOp 模块 API 文档

> 包路径：`com.openjiuwen.core.sysop`

系统操作注册、本地执行、沙箱控制与工具适配。基于 `sysop` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `57` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.sysop` | 11 |
| `com.openjiuwen.core.sysop.config` | 2 |
| `com.openjiuwen.core.sysop.local` | 8 |
| `com.openjiuwen.core.sysop.registry` | 3 |
| `com.openjiuwen.core.sysop.result` | 30 |
| `com.openjiuwen.core.sysop.sandbox` | 3 |

## `com.openjiuwen.core.sysop`

公开类型：`11`

### `BaseCodeOperation`

- 类型：`class`
- 声明：`public abstract class BaseCodeOperation extends BaseOperation`
- 说明：Base code operation \u2014 abstract class for code execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseCodeOperation(String name, OperationMode mode, String description, Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<ToolCard> listTools()` | `List<ToolCard>` | - |
| `public abstract ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCodeResult` | Execute arbitrary code. |
| `public abstract Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCodeStreamResult>` | Execute arbitrary code with streaming output. |

### `BaseFsOperation`

- 类型：`class`
- 声明：`public abstract class BaseFsOperation extends BaseOperation`
- 说明：Base file system operation \u2014 abstract class for FS operations.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseFsOperation(String name, OperationMode mode, String description, Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<ToolCard> listTools()` | `List<ToolCard>` | - |
| `public abstract ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `ReadFileResult` | Read a file with specified mode and parameters. |
| `public abstract Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `Iterator<ReadFileStreamResult>` | Read a file with streaming output. |
| `public abstract WriteFileResult writeFile(String path, String content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | `WriteFileResult` | Write content to a file. |
| `public abstract UploadFileResult uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `UploadFileResult` | Upload a file from local to target path. |
| `public abstract Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<UploadFileStreamResult>` | Upload a file with streaming. |
| `public abstract DownloadFileResult downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `DownloadFileResult` | Download a file from source to local path. |
| `public abstract Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<DownloadFileStreamResult>` | Download a file with streaming. |
| `public abstract ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | `ListFilesResult` | List files under the specified path. |
| `public abstract ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | `ListDirsResult` | List directories under the specified path. |
| `public abstract SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns)` | `SearchFilesResult` | Search files under the specified path. |

### `BaseOperation`

- 类型：`class`
- 声明：`public abstract class BaseOperation`
- 说明：Base class for all system operations (file, code, shell, etc.).

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseOperation(String name, OperationMode mode, String description, Object runConfig)` | Create a base operation. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public OperationMode getMode()` | `OperationMode` | - |
| `public String getDescription()` | `String` | - |
| `protected LocalWorkConfig getLocalConfig()` | `LocalWorkConfig` | Get the run configuration as LocalWorkConfig. |
| `protected SandboxGatewayConfig getSandboxConfig()` | `SandboxGatewayConfig` | Get the run configuration as SandboxGatewayConfig. |
| `protected Object getRunConfig()` | `Object` | - |
| `public abstract List<ToolCard> listTools()` | `List<ToolCard>` | Retrieve a list of tool cards describing available operations. |
| `protected List<ToolCard> generateToolCards(List<String> methodNames)` | `List<ToolCard>` | Generate tool cards for the specified method names using reflection. |
| `protected SysOperationEvent createSysOperationEvent(LogEventType eventType, String methodName, Map<String, Object> methodParams, Map<String, Object> methodResult, Double methodExecTimeMs)` | `SysOperationEvent` | Create a SysOperationEvent for logging. |

### `BaseShellOperation`

- 类型：`class`
- 声明：`public abstract class BaseShellOperation extends BaseOperation`
- 说明：Base shell operation \u2014 abstract class for shell command execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseShellOperation(String name, OperationMode mode, String description, Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<ToolCard> listTools()` | `List<ToolCard>` | - |
| `public abstract ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCmdResult` | Execute a shell command. |
| `public abstract Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCmdStreamResult>` | Execute a shell command with streaming output. |

### `FsConstants`

- 类型：`class`
- 声明：`public final class FsConstants`
- 说明：Constants for file system operations \u2014 chunk sizes and limits.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_READ_CHUNK_SIZE` | `int` | `public static final` | `0` | Default chunk sizes (0 = unlimited). |
| `DEFAULT_UPLOAD_CHUNK_SIZE` | `int` | `public static final` | `0` | - |
| `DEFAULT_DOWNLOAD_CHUNK_SIZE` | `int` | `public static final` | `0` | - |
| `DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE` | `int` | `public static final` | `1024 * 1024` | Default streaming chunk sizes. |
| `DEFAULT_UPLOAD_STREAM_CHUNK_SIZE` | `int` | `public static final` | `1024 * 1024` | - |
| `DEFAULT_READ_STREAM_CHUNK_SIZE` | `int` | `public static final` | `8192` | - |
| `TAIL_CHUNK_SIZE` | `int` | `public static final` | `1024` | Tail read chunk size. |

### `OperationMode`

- 类型：`enum`
- 声明：`public enum OperationMode`
- 说明：Enum for operation mode.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `LOCAL` | `new OperationMode("local")` | - |
| `SANDBOX` | `new OperationMode("sandbox")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static OperationMode fromString(String text)` | `OperationMode` | Parse a string value to OperationMode (case-insensitive). |

### `SysOperation`

- 类型：`class`
- 声明：`public class SysOperation`
- 说明：SysOperation \u2014 facade for accessing system operations.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SysOperation(SysOperationCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseFsOperation fs()` | `BaseFsOperation` | Get the file system operation instance. |
| `public BaseCodeOperation code()` | `BaseCodeOperation` | Get the code execution operation instance. |
| `public BaseShellOperation shell()` | `BaseShellOperation` | Get the shell command operation instance. |
| `public BaseOperation getOperation(String name)` | `BaseOperation` | Get an operation by name. |
| `public OperationMode getMode()` | `OperationMode` | - |

### `SysOperationCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class SysOperationCard extends BaseCard`
- 说明：Configuration card for system operations.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `mode` | `OperationMode` | `private` | `-` | Running mode: local or sandbox. |
| `workConfig` | `LocalWorkConfig` | `private` | `-` | Local work config (required when mode is LOCAL). |
| `gatewayConfig` | `SandboxGatewayConfig` | `private` | `-` | Sandbox gateway config (required when mode is SANDBOX). |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static OperationMode validateMode(String modeValue)` | `OperationMode` | Validate that mode is a valid OperationMode. |
| `public ToolIdProxy fs()` | `ToolIdProxy` | Get the ToolIdProxy for file system operations. |
| `public ToolIdProxy shell()` | `ToolIdProxy` | Get the ToolIdProxy for shell operations. |
| `public ToolIdProxy code()` | `ToolIdProxy` | Get the ToolIdProxy for code operations. |
| `public ToolIdProxy proxy(String opType)` | `ToolIdProxy` | Get a ToolIdProxy for a custom operation type. |
| `public static String generateToolId(String cardId, String opType, String methodName)` | `String` | Centralized tool ID generation for SysOperation methods. |

### `SysOperationToolAdapter`

- 类型：`class`
- 声明：`public final class SysOperationToolAdapter`
- 说明：Adapter for converting SysOperation to LocalFunction tools.
- 嵌套公开类型：`SysOperationToolAdapter.ToolEntry`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static List<ToolEntry> extractTools(SysOperationCard card, SysOperation instance)` | `List<ToolEntry>` | Extract all tools from SysOperation and wrap them as LocalFunction instances. |
| `public static String getToolIdPrefix(String sysOperationId)` | `String` | Get tool ID prefix for a sys operation. |

### `SysOperationToolAdapter.ToolEntry`

- 类型：`record`
- 声明：`public record ToolEntry(String toolId, LocalFunction localFunction)`
- 说明：A tuple of (toolId, LocalFunction).
- 宿主类型：`SysOperationToolAdapter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `toolId` | `String` | `private final` | `-` | - |
| `localFunction` | `LocalFunction` | `private final` | `-` | - |

### `ToolIdProxy`

- 类型：`class`
- 声明：`public class ToolIdProxy`
- 说明：A helper for generating tool IDs via method calls.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ToolIdProxy(String cardId, String opType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String toolId(String methodName)` | `String` | Generate a tool ID for a method name. |
| `public String getCardId()` | `String` | - |
| `public String getOpType()` | `String` | - |

## `com.openjiuwen.core.sysop.config`

公开类型：`2`

### `LocalWorkConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class LocalWorkConfig`
- 说明：Local working configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `shellAllowlist` | `List<String>` | `private` | `Arrays.asList("echo", "ls", "dir", "cd", "pwd", "python", "python3", "pip", "pip3", "npm", "node", "git", "cat", "type", "mkdir", "md", "rm", "rd", "cp", "copy", "mv", "move", "grep", "find", "curl", "wget", "ps", "df", "ping")` | List of allowed command prefixes. |
| `workDir` | `String` | `private` | `-` | Local working directory path. |

### `SandboxGatewayConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class SandboxGatewayConfig`
- 说明：Remote sandbox gateway connection configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `gatewayUrl` | `String` | `private` | `""` | Remote sandbox gateway service endpoint. |
| `params` | `Map<String, Object>` | `private` | `new HashMap<>()` | Global request parameters. |
| `authHeaders` | `Map<String, String>` | `private` | `new HashMap<>()` | Authentication HTTP headers. |
| `authQueryParams` | `Map<String, String>` | `private` | `new HashMap<>()` | Authentication query parameters. |

## `com.openjiuwen.core.sysop.local`

公开类型：`8`

### `InvokeData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class InvokeData`
- 说明：Structured return model for one-time subprocess execution via `invoke()` method.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `stdout` | `String` | `private` | `-` | Complete standard output string captured from the subprocess execution. |
| `stderr` | `String` | `private` | `-` | Complete standard error string captured from the subprocess execution. |
| `exitCode` | `int` | `private` | `-` | Exit code returned by the subprocess (0 for success, non-zero for errors). |
| `exception` | `Exception` | `private` | `-` | Exception captured during subprocess execution, if any. |

### `LocalCodeOperation`

- 类型：`class`
- 声明：`@Operation(name = "code", mode = OperationMode.LOCAL, description = "local code operation") public class LocalCodeOperation extends BaseCodeOperation`
- 说明：Local code execution operation using ProcessBuilder.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LocalCodeOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCodeResult` | - |
| `public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCodeStreamResult>` | - |

### `LocalFsOperation`

- 类型：`class`
- 声明：`@Operation(name = "fs", mode = OperationMode.LOCAL, description = "local fs operation") public class LocalFsOperation extends BaseFsOperation`
- 说明：Local file system operation using Java NIO.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LocalFsOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `ReadFileResult` | - |
| `public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `Iterator<ReadFileStreamResult>` | - |
| `public WriteFileResult writeFile(String path, String content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | `WriteFileResult` | - |
| `public UploadFileResult uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `UploadFileResult` | - |
| `public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<UploadFileStreamResult>` | - |
| `public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `DownloadFileResult` | - |
| `public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<DownloadFileStreamResult>` | - |
| `public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | `ListFilesResult` | - |
| `public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | `ListDirsResult` | - |
| `public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns)` | `SearchFilesResult` | - |

### `LocalShellOperation`

- 类型：`class`
- 声明：`@Operation(name = "shell", mode = OperationMode.LOCAL, description = "local shell operation") public class LocalShellOperation extends BaseShellOperation`
- 说明：Local shell command execution operation.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LocalShellOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCmdResult` | - |
| `public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCmdStreamResult>` | - |

### `OperationUtils`

- 类型：`class`
- 声明：`public final class OperationUtils`
- 说明：Utility class for common subprocess operation helper methods.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String createTmpFile(String fileContent, String fileSuffix)` | `String` | Create a unique temporary file and write content to it. |
| `public static boolean deleteTmpFile(String filePath)` | `boolean` | Delete the specified temporary file. |
| `public static Map<String, String> prepareEnvironment(Map<String, String> customEnv)` | `Map<String, String>` | Create a merged environment dictionary for subprocess execution. |
| `public static ProcessHandler createHandler(Process process, int chunkSize, Charset encoding, int timeout)` | `ProcessHandler` | Factory method to create a ProcessHandler instance. |
| `public static ProcessHandler createHandler(Process process)` | `ProcessHandler` | Factory method to create a ProcessHandler with defaults. |

### `ProcessHandler`

- 类型：`class`
- 声明：`public class ProcessHandler`
- 说明：Handler for monitoring Java subprocess output and state.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ProcessHandler(Process process, int chunkSize, Charset encoding, int overallTimeoutSeconds)` | - |
| `public ProcessHandler(Process process)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public InvokeData invoke()` | `InvokeData` | One-time execution to get structured subprocess result. |
| `public Iterator<StreamEvent> stream()` | `Iterator<StreamEvent>` | Create an iterator for streaming process output events. |

### `StreamEvent`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class StreamEvent`
- 说明：Data model for process stream events.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `StreamEventType` | `private` | `-` | Type of the stream event. |
| `data` | `String` | `private` | `-` | Event payload data: stdout/stderr = text output string, exit = integer exit code as string, error = error message string. |
| `timestamp` | `Instant` | `private` | `Instant.now()` | UTC timestamp when the event was created. |

### `StreamEventType`

- 类型：`enum`
- 声明：`public enum StreamEventType`
- 说明：Enumeration of stream event types for process output monitoring.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `STDOUT` | `new StreamEventType("stdout")` | - |
| `STDERR` | `new StreamEventType("stderr")` | - |
| `EXIT` | `new StreamEventType("exit")` | - |
| `ERROR` | `new StreamEventType("error")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

## `com.openjiuwen.core.sysop.registry`

公开类型：`3`

### `Operation`

- 类型：`annotation`
- 声明：`@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface Operation`
- 说明：Annotation for registering a class as an operation in the OperationRegistry.
- 注解：`@Target`、`@Retention`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `OperationDef`

- 类型：`class`
- 声明：`public class OperationDef`
- 说明：Definition and factory for an operation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OperationDef(Class<? extends BaseOperation> cls, String name, OperationMode mode, String description)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseOperation createInstance(Object runConfig)` | `BaseOperation` | Create an operation instance with the given configuration. |
| `public Class<? extends BaseOperation> getCls()` | `Class<? extends BaseOperation>` | - |
| `public String getDescription()` | `String` | - |
| `public String getName()` | `String` | - |
| `public OperationMode getMode()` | `OperationMode` | - |
| `public boolean equals(Object o)` | `boolean` | - |
| `public int hashCode()` | `int` | - |

### `OperationRegistry`

- 类型：`class`
- 声明：`public final class OperationRegistry`
- 说明：Operation registry that manages operation definitions.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void register(Class<? extends BaseOperation> operationCls, String name, OperationMode mode, String description)` | `void` | Register an operation. |
| `public static void register(Class<? extends BaseOperation> operationCls)` | `void` | Register an operation class that has the Operation annotation. |
| `public static Optional<OperationDef> getOperationInfo(String name, OperationMode mode)` | `Optional<OperationDef>` | Get operation information for the given name and mode. |
| `public static List<String> getSupportedOperations(OperationMode mode)` | `List<String>` | Get list of supported operation names for the given mode. |

## `com.openjiuwen.core.sysop.result`

公开类型：`30`

### `BaseResult`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor public abstract class BaseResult<T>`
- 说明：Base result class for all sys_operation results.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`
- 嵌套公开类型：`BaseResult.ResultFactory`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `code` | `int` | `private` | `-` | Status code: 0 = success, non-0 = failure. |
| `message` | `String` | `private` | `-` | Message details. |
| `data` | `T` | `private` | `-` | Business data (returned only on success). |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseResult(int code, String message, T data)` | Explicit all-args constructor for subclass super() calls. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static <T, R extends BaseResult<T>>R buildOperationErrorResult(StatusCode errorType, Map<String, String> msgFormatKwargs, ResultFactory<R> resultFactory, T data)` | `R` | Create a standardized error result object with specified error type and formatted message. |
| `public static <T, R extends BaseResult<?>>R buildOperationErrorResult(StatusCode errorType, String execution, String errorMsg, ResultFactory<R> resultFactory, Object data)` | `R` | Convenience overload for simple execution/error_msg formatting. |

### `BaseResult.ResultFactory`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface ResultFactory<R> extends Supplier<R>`
- 说明：Factory interface for creating typed result instances (no-arg supplier).
- 宿主类型：`BaseResult`
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `DownloadFileChunkData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DownloadFileChunkData`
- 说明：Data structure for chunked download file.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sourcePath` | `String` | `private` | `-` | - |
| `localPath` | `String` | `private` | `-` | - |
| `chunkSize` | `int` | `private` | `-` | - |
| `chunkIndex` | `int` | `private` | `-` | - |
| `lastChunk` | `boolean` | `private` | `-` | - |

### `DownloadFileData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DownloadFileData`
- 说明：Data structure for download file operation.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sourcePath` | `String` | `private` | `-` | - |
| `localPath` | `String` | `private` | `-` | - |
| `size` | `long` | `private` | `-` | - |

### `DownloadFileResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class DownloadFileResult extends BaseResult<DownloadFileData>`
- 说明：Result type for download file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DownloadFileResult(int code, String message, DownloadFileData data)` | - |

### `DownloadFileStreamResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData>`
- 说明：Result type for streaming download file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data)` | - |

### `ExecuteCmdChunkData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ExecuteCmdChunkData`
- 说明：Data structure for chunked shell command output.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `text` | `String` | `private` | `""` | Raw content of the output chunk. |
| `type` | `String` | `private` | `-` | Type of the output chunk: "stdout" or "stderr". |
| `chunkIndex` | `int` | `private` | `-` | Index of current chunk (starting from 0). |
| `exitCode` | `Integer` | `private` | `-` | Command exit code. |
| `metadata` | `Map<String, Object>` | `private` | `-` | Data for command. |

### `ExecuteCmdData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ExecuteCmdData`
- 说明：Data structure for shell command execution.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `command` | `String` | `private` | `-` | Original shell command executed. |
| `cwd` | `String` | `private` | `"."` | Current working directory. |
| `exitCode` | `Integer` | `private` | `-` | Command exit code. |
| `stdout` | `String` | `private` | `""` | Standard output stream. |
| `stderr` | `String` | `private` | `""` | Standard error stream. |

### `ExecuteCmdResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ExecuteCmdResult extends BaseResult<ExecuteCmdData>`
- 说明：Result type for shell command execution.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExecuteCmdResult(int code, String message, ExecuteCmdData data)` | - |

### `ExecuteCmdStreamResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData>`
- 说明：Result type for streaming shell command execution.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data)` | - |

### `ExecuteCodeChunkData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ExecuteCodeChunkData`
- 说明：Data structure for chunked code execution output.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `text` | `String` | `private` | `""` | Raw content of the output chunk. |
| `type` | `String` | `private` | `-` | Type of the output chunk: "stdout" or "stderr". |
| `chunkIndex` | `int` | `private` | `-` | Index of current chunk (starting from 0). |
| `exitCode` | `Integer` | `private` | `-` | Execution exit code. |
| `metadata` | `Map<String, Object>` | `private` | `-` | Data for execution. |

### `ExecuteCodeData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ExecuteCodeData`
- 说明：Code execution result data model.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `codeContent` | `String` | `private` | `-` | Original code executed. |
| `language` | `String` | `private` | `-` | Programming language of the original code. |
| `exitCode` | `Integer` | `private` | `-` | Execution exit code. |
| `stdout` | `String` | `private` | `""` | Standard output stream. |
| `stderr` | `String` | `private` | `""` | Standard error stream. |

### `ExecuteCodeResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ExecuteCodeResult extends BaseResult<ExecuteCodeData>`
- 说明：Result type for code execution.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExecuteCodeResult(int code, String message, ExecuteCodeData data)` | - |

### `ExecuteCodeStreamResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData>`
- 说明：Result type for streaming code execution.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ExecuteCodeStreamResult(int code, String message, ExecuteCodeChunkData data)` | - |

### `FileSystemData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class FileSystemData`
- 说明：Data structure for list files and list directories.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `totalCount` | `int` | `private` | `-` | Total number of items. |
| `listItems` | `List<FileSystemItem>` | `private` | `-` | List of file/directory details. |
| `rootPath` | `String` | `private` | `-` | Original input directory path. |
| `recursive` | `boolean` | `private` | `-` | Actual recursive status used. |
| `maxDepth` | `Integer` | `private` | `-` | Actual maximum recursion depth used. |

### `FileSystemItem`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class FileSystemItem`
- 说明：Base model for file/directory common properties.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private` | `-` | Name of the file/directory. |
| `path` | `String` | `private` | `-` | Full absolute path of the file/directory. |
| `size` | `long` | `private` | `-` | Size in bytes. |
| `modifiedTime` | `String` | `private` | `-` | Last modification time (ISO format). |
| `directory` | `boolean` | `private` | `-` | Whether the item is a directory. |
| `type` | `String` | `private` | `-` | File extension (only for files). |

### `ListDirsResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ListDirsResult extends BaseResult<FileSystemData>`
- 说明：Result type for list directories operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ListDirsResult(int code, String message, FileSystemData data)` | - |

### `ListFilesResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ListFilesResult extends BaseResult<FileSystemData>`
- 说明：Result type for list files operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ListFilesResult(int code, String message, FileSystemData data)` | - |

### `ReadFileChunkData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ReadFileChunkData`
- 说明：Data structure for chunked file read.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `path` | `String` | `private` | `-` | File path of the read file. |
| `chunkContent` | `String` | `private` | `-` | Current chunk content. |
| `mode` | `String` | `private` | `-` | File read mode: "text" or "bytes". |
| `chunkSize` | `int` | `private` | `-` | Size of each chunk (in bytes). |
| `chunkIndex` | `int` | `private` | `-` | Index of current chunk (starting from 0). |
| `lastChunk` | `boolean` | `private` | `-` | Whether current chunk is the last one. |

### `ReadFileData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ReadFileData`
- 说明：Data structure for read file operation.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `path` | `String` | `private` | `-` | File path of the read file. |
| `content` | `String` | `private` | `-` | File content (text string or binary bytes represented as string). |
| `mode` | `String` | `private` | `-` | File read mode: "text" or "bytes". |

### `ReadFileResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ReadFileResult extends BaseResult<ReadFileData>`
- 说明：Result type for read file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReadFileResult(int code, String message, ReadFileData data)` | - |

### `ReadFileStreamResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class ReadFileStreamResult extends BaseResult<ReadFileChunkData>`
- 说明：Result type for streaming read file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReadFileStreamResult(int code, String message, ReadFileChunkData data)` | - |

### `SearchFilesData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class SearchFilesData`
- 说明：Data structure for search files.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `totalMatches` | `int` | `private` | `-` | Total number of matching files. |
| `matchingFiles` | `List<FileSystemItem>` | `private` | `-` | List of matching files. |
| `searchPath` | `String` | `private` | `-` | Original base path used for the search. |
| `searchPattern` | `String` | `private` | `-` | Original search pattern used. |
| `excludePatterns` | `List<String>` | `private` | `-` | Original exclude patterns used. |

### `SearchFilesResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class SearchFilesResult extends BaseResult<SearchFilesData>`
- 说明：Result type for search files operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SearchFilesResult(int code, String message, SearchFilesData data)` | - |

### `UploadFileChunkData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class UploadFileChunkData`
- 说明：Data structure for chunked upload file.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `localPath` | `String` | `private` | `-` | - |
| `targetPath` | `String` | `private` | `-` | - |
| `chunkSize` | `int` | `private` | `-` | - |
| `chunkIndex` | `int` | `private` | `-` | - |
| `lastChunk` | `boolean` | `private` | `-` | - |

### `UploadFileData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class UploadFileData`
- 说明：Data structure for upload file operation.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `localPath` | `String` | `private` | `-` | - |
| `targetPath` | `String` | `private` | `-` | - |
| `size` | `long` | `private` | `-` | - |

### `UploadFileResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class UploadFileResult extends BaseResult<UploadFileData>`
- 说明：Result type for upload file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public UploadFileResult(int code, String message, UploadFileData data)` | - |

### `UploadFileStreamResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class UploadFileStreamResult extends BaseResult<UploadFileChunkData>`
- 说明：Result type for streaming upload file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public UploadFileStreamResult(int code, String message, UploadFileChunkData data)` | - |

### `WriteFileData`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class WriteFileData`
- 说明：Data structure for write file operation.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `path` | `String` | `private` | `-` | File path of the write file. |
| `size` | `int` | `private` | `-` | File content size in bytes. |
| `mode` | `String` | `private` | `-` | File write mode: "text" or "bytes". |

### `WriteFileResult`

- 类型：`class`
- 声明：`@SuperBuilder @NoArgsConstructor public class WriteFileResult extends BaseResult<WriteFileData>`
- 说明：Result type for write file operation.
- 注解：`@SuperBuilder`、`@NoArgsConstructor`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WriteFileResult(int code, String message, WriteFileData data)` | - |

## `com.openjiuwen.core.sysop.sandbox`

公开类型：`3`

### `SandboxCodeOperation`

- 类型：`class`
- 声明：`@Operation(name = "code", mode = OperationMode.SANDBOX, description = "sandbox code operation") public class SandboxCodeOperation extends BaseCodeOperation`
- 说明：Sandbox code operation stub \u2014 not yet implemented.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SandboxCodeOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCodeResult` | - |
| `public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCodeStreamResult>` | - |

### `SandboxFsOperation`

- 类型：`class`
- 声明：`@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation") public class SandboxFsOperation extends BaseFsOperation`
- 说明：Sandbox file system operation stub \u2014 not yet implemented.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SandboxFsOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `ReadFileResult` | - |
| `public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail, int[] lineRange, String encoding, int chunkSize, Map<String, Object> options)` | `Iterator<ReadFileStreamResult>` | - |
| `public WriteFileResult writeFile(String path, String content, String mode, boolean prependNewline, boolean appendNewline, boolean createIfNotExist, String permissions, String encoding, Map<String, Object> options)` | `WriteFileResult` | - |
| `public UploadFileResult uploadFile(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `UploadFileResult` | - |
| `public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<UploadFileStreamResult>` | - |
| `public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `DownloadFileResult` | - |
| `public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath, boolean overwrite, boolean createParentDirs, boolean preservePermissions, int chunkSize, Map<String, Object> options)` | `Iterator<DownloadFileStreamResult>` | - |
| `public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, List<String> fileTypes, Map<String, Object> options)` | `ListFilesResult` | - |
| `public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth, String sortBy, boolean sortDescending, Map<String, Object> options)` | `ListDirsResult` | - |
| `public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns)` | `SearchFilesResult` | - |

### `SandboxShellOperation`

- 类型：`class`
- 声明：`@Operation(name = "shell", mode = OperationMode.SANDBOX, description = "sandbox shell operation") public class SandboxShellOperation extends BaseShellOperation`
- 说明：Sandbox shell operation stub \u2014 not yet implemented.
- 注解：`@Operation`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SandboxShellOperation(Object runConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `ExecuteCmdResult` | - |
| `public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | `Iterator<ExecuteCmdStreamResult>` | - |

