# sys_operation 模块 Python / Java API 映射

## 对照范围

- Python: `agent-core-python/openjiuwen/core/sys_operation/**`
- Java: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/sysop/**`
- 本文口径:
  - 以类、公开/受保护方法、配置/结果 DTO 为主
  - Python `snake_case` 到 Java `camelCase` 视为命名适配
  - Python `async def` / `AsyncIterator[...]` 到 Java 同步返回值 / `Iterator<...>` 视为异步模型适配
  - Python 动态属性代理 (`__getattr__`) 到 Java 显式方法 (`getOperation/proxy/toolId`) 视为接口适配

## 总结结论

- Java 版 `sysop` 已经覆盖 Python 版 `sys_operation` 的主体类族: 抽象层、注册表、门面、tool adapter、local/sandbox 实现、配置对象、结果对象、进程辅助类和 sandbox 占位类都已具备对应实现。
- 旧文档中“Java 缺少 `SandboxGateway/ContainerManager/Container/SandboxClient`”“Java 缺少 `safeModelDump`”“Java 没有批量 `getToolIdPrefix`”等结论已不成立，源码均已存在对应实现。
- 当前剩余差异主要不是“类缺失”，而是少量实现语义偏差，集中在 `local/LocalFsOperation.java`，已单独整理到 `docs/FIXED/sys_operation_fixed.md`。

## 一、类级映射

### 1.1 核心抽象与门面

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `OperationMode` | `OperationMode` | 完全映射 | 枚举值均为 `local` / `sandbox`，Java 额外提供 `fromString()`、`getValue()` |
| `BaseOperation` | `BaseOperation` | 完全映射 | 都负责 operation 元信息、工具卡片生成、日志事件拼装 |
| `BaseFsOperation` | `BaseFsOperation` | 完全映射 | 10 个 FS 抽象 API 全部存在 |
| `BaseShellOperation` | `BaseShellOperation` | 完全映射 | `execute_cmd/execute_cmd_stream` 均有对应 |
| `BaseCodeOperation` | `BaseCodeOperation` | 完全映射 | `execute_code/execute_code_stream` 均有对应 |
| `ToolIdProxy` | `ToolIdProxy` | 适配映射 | Python 通过属性访问生成 tool id，Java 改为 `toolId(methodName)` |
| `SysOperationCard` | `SysOperationCard` | 适配映射 | `fs/shell/code` 代理保留；Python `__getattr__` 改为 Java `proxy(opType)` |
| `SysOperation` | `SysOperation` | 适配映射 | 内建 `fs/code/shell` 一致；Python 动态 `__getattr__` 改为 Java `getOperation(name)` |
| `SysOperationToolAdapter` | `SysOperationToolAdapter` | 适配映射 | `extract_tools` 对齐；Java 以 `ToolEntry` record 替代 Python tuple |

### 1.2 注册表与装饰器/注解

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `OperationDef` | `OperationDef` | 完全映射 | 字段、实例化职责一致；Java 额外提供 getter / `equals` / `hashCode` |
| `OperationRegistry` | `OperationRegistry` | 完全映射 | 注册、查询、延迟加载、动态包扫描都已具备 |
| `operation(...)` | `@Operation(...)` | 适配映射 | Python 装饰器在 Java 中落为运行时注解 |
| 无 | `registry.Operation` | Java-only | Java 为承载装饰器语义新增注解类型 |

### 1.3 配置类

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `LocalWorkConfig` | `LocalWorkConfig` | 完全映射 | `shell_allowlist/work_dir` 对应 `shellAllowlist/workDir` |
| `SandboxGatewayConfig` | `SandboxGatewayConfig` | 完全映射 | `gateway_url/params/auth_headers/auth_query_params` 全量对应 |

### 1.4 Local 实现与辅助类

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `local.CodeOperation` | `local.LocalCodeOperation` | 适配映射 | 类名加 `Local` 前缀；执行 API 对齐 |
| `local.FsOperation` | `local.LocalFsOperation` | 适配映射 | 类名加 `Local` 前缀；公开 FS API 对齐 |
| `local.ShellOperation` | `local.LocalShellOperation` | 适配映射 | 类名加 `Local` 前缀；公开 Shell API 对齐 |
| `local.StreamEventType` | `local.StreamEventType` | 完全映射 | `STDOUT/STDERR/EXIT/ERROR` 一致 |
| `local.StreamEvent` | `local.StreamEvent` | 完全映射 | `type/data/timestamp` 全量对应 |
| `local.InvokeData` | `local.InvokeData` | 完全映射 | `stdout/stderr/exit_code/exception` 对应 `stdout/stderr/exitCode/exception` |
| `local.AsyncProcessHandler` | `local.ProcessHandler` | 适配映射 | Python asyncio 处理器映射为 Java 线程 + 阻塞队列实现 |
| `local.OperationUtils` | `local.OperationUtils` | 完全映射 | 临时文件、环境准备、handler 创建均有对应 |
| `local._ListItemsSpec` | 无独立类 | 折叠映射 | Java 将该内部参数模型折叠进 `LocalFsOperation.listItemsInternal(...)` |
| `local._ReadParams` | 无独立类 | 折叠映射 | Java 将参数校验与解析折叠进 `validateReadParams(...)` + `resolvePath(...)` |
| `local._ErrorLogParams` | 无独立类 | 折叠映射 | Java 直接以内联变量和 `buildFsErrorResult(...)` 处理 |
| 无 | `FsConstants` | Java-only | Java 将 Python `fs.py` 的模块级常量提取为专用常量类 |

### 1.5 Sandbox 占位实现

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `sandbox.CodeOperation` | `sandbox.SandboxCodeOperation` | 完全映射 | 两侧均为已注册但未实现的 stub |
| `sandbox.FsOperation` | `sandbox.SandboxFsOperation` | 完全映射 | 两侧均为已注册但未实现的 stub |
| `sandbox.ShellOperation` | `sandbox.SandboxShellOperation` | 完全映射 | 两侧均为已注册但未实现的 stub |
| `sandbox.SandboxGateway` | `sandbox.SandboxGateway` | 完全映射 | 两侧都是占位类 |
| `sandbox.ContainerManager` | `sandbox.ContainerManager` | 完全映射 | 两侧都是占位类 |
| `sandbox.Container` | `sandbox.Container` | 完全映射 | 两侧都是占位类 |
| `sandbox.SandboxClient` | `sandbox.SandboxClient` | 完全映射 | 两侧都是占位类 |

## 二、方法级映射

### 2.1 BaseOperation

| Python API | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(name, mode, description, run_config)` | `BaseOperation(String, OperationMode, String, Object)` | 完全映射 | 构造参数语义一致 |
| `list_tools()` | `listTools()` | 完全映射 | 返回可暴露工具列表 |
| `_safe_model_dump(obj, default=None)` | `safeModelDump(obj, defaultValue)` | 完全映射 | 都是“安全转 Map”的容错辅助方法 |
| `_create_sys_operation_event(...)` | `createSysOperationEvent(..., extras)` | 完全映射 | Java 提供 5 参和 6 参两个重载，`extras` 对应 Python `**kwargs` |
| `_generate_tool_cards(method_names)` | `generateToolCards(methodNames)` | 完全映射 | 都基于方法签名生成 `ToolCard` |
| `name/mode/description` 属性 | `getName()/getMode()/getDescription()` | 适配映射 | Python 属性访问变 Java getter |
| `_run_config` | `getRunConfig()/getLocalConfig()/getSandboxConfig()` | 适配映射 | Java 多了类型化 getter |

### 2.2 BaseFsOperation

| Python API | Java API | 状态 |
| --- | --- | --- |
| `list_tools()` | `listTools()` | 完全映射 |
| `read_file(...)` | `readFile(...)` | 完全映射 |
| `read_file_stream(...)` | `readFileStream(...)` | 完全映射 |
| `write_file(...)` | `writeFile(...)` | 完全映射 |
| `upload_file(...)` | `uploadFile(...)` | 完全映射 |
| `upload_file_stream(...)` | `uploadFileStream(...)` | 完全映射 |
| `download_file(...)` | `downloadFile(...)` | 完全映射 |
| `download_file_stream(...)` | `downloadFileStream(...)` | 完全映射 |
| `list_files(...)` | `listFiles(...)` | 完全映射 |
| `list_directories(...)` | `listDirectories(...)` | 完全映射 |
| `search_files(path, pattern, exclude_patterns=None)` | `searchFiles(path, pattern, excludePatterns)` | 完全映射 |

补充:

- Python `line_range: Tuple[int, int]` 对应 Java `int[] lineRange`
- Python `content: str | bytes` 对应 Java `Object content`
- Python `options: Dict[str, Any]` 对应 Java `Map<String, Object>`

### 2.3 BaseShellOperation

| Python API | Java API | 状态 |
| --- | --- | --- |
| `list_tools()` | `listTools()` | 完全映射 |
| `execute_cmd(command, cwd=None, timeout=300, environment=None, options=None)` | `executeCmd(command, cwd, timeout, environment, options)` | 完全映射 |
| `execute_cmd_stream(command, cwd=None, timeout=300, environment=None, options=None)` | `executeCmdStream(command, cwd, timeout, environment, options)` | 完全映射 |

### 2.4 BaseCodeOperation

| Python API | Java API | 状态 |
| --- | --- | --- |
| `list_tools()` | `listTools()` | 完全映射 |
| `execute_code(code, language="python", timeout=300, environment=None, options=None)` | `executeCode(code, language, timeout, environment, options)` | 完全映射 |
| `execute_code_stream(code, language="python", timeout=300, environment=None, options=None)` | `executeCodeStream(code, language, timeout, environment, options)` | 完全映射 |

### 2.5 注册表、门面与代理

| Python API | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| `OperationDef.create_instance(run_config)` | `OperationDef.createInstance(runConfig)` | 完全映射 | 两侧都负责实例化 operation |
| `OperationRegistry.register(cls, name=..., mode=..., description=...)` | `OperationRegistry.register(Class, name, mode, description)` | 完全映射 | 显式注册 |
| `OperationRegistry.register(cls)` + `@operation(...)` | `OperationRegistry.register(Class)` + `@Operation(...)` | 完全映射 | 注解/装饰器元信息注册 |
| `OperationRegistry.get_operation_info(name, mode)` | `getOperationInfo(name, mode)` | 完全映射 | 查询单个 operation |
| `OperationRegistry.get_supported_operations(mode)` | `getSupportedOperations(mode)` | 完全映射 | 查询支持的 operation 名称 |
| `_load_build_in_operation(mode)` | `loadBuiltInOperations(mode)` | 完全映射 | Java 为私有静态方法 |
| `_discover_package(package_name)` | `discoverPackage(packageName, mode)` | 完全映射 | Java 已具备文件目录 / jar 两种扫描实现 |
| `ToolIdProxy.__getattr__(method_name)` | `ToolIdProxy.toolId(methodName)` | 适配映射 | 动态属性改为显式方法 |
| `SysOperationCard.fs/shell/code` 属性 | `SysOperationCard.fs()/shell()/code()` | 适配映射 | property 改普通方法 |
| `SysOperationCard.__getattr__(name)` | `SysOperationCard.proxy(opType)` | 适配映射 | 自定义 operation 代理 |
| `SysOperationCard.generate_tool_id(...)` | `generateToolId(...)` | 完全映射 | tool id 规则一致 |
| `SysOperation.__getattr__(name)` | `getOperation(name)` | 适配映射 | Python 动态方法改显式查询 |
| `SysOperation._get_operation(name)` | `getOperation(name)` | 适配映射 | Java 将该能力公开化 |
| `SysOperation.mode` | `getMode()` | 适配映射 | 属性改 getter |
| `SysOperationToolAdapter.extract_tools(card, instance)` | `extractTools(card, instance)` | 完全映射 | 都会展开 `SysOperation` 为工具集合 |
| `SysOperationToolAdapter.get_tool_id_prefix(str)` | `getToolIdPrefix(String)` | 完全映射 | 单个 id 前缀 |
| `SysOperationToolAdapter.get_tool_id_prefix(List[str])` | `getToolIdPrefix(List<String>)` | 完全映射 | Java 现已支持批量前缀 |

### 2.6 Local 实现与辅助方法

| Python API | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| `CodeOperation.execute_code` | `LocalCodeOperation.executeCode` | 完全映射 | 公开执行 API 对齐 |
| `CodeOperation.execute_code_stream` | `LocalCodeOperation.executeCodeStream` | 完全映射 | 流式执行 API 对齐 |
| `CodeOperation._get_default_cmd_limit` | `LocalCodeOperation.getDefaultCmdLimit` | 适配映射 | Python 类方法对应 Java 私有辅助方法 |
| `CodeOperation._build_subprocess_cmd` | `LocalCodeOperation.buildSubprocessCmd` | 适配映射 | 子进程命令构建逻辑对应 |
| `ShellOperation.execute_cmd` | `LocalShellOperation.executeCmd` | 完全映射 | 公开执行 API 对齐 |
| `ShellOperation.execute_cmd_stream` | `LocalShellOperation.executeCmdStream` | 完全映射 | 流式执行 API 对齐 |
| `ShellOperation._check_allowlist` | `LocalShellOperation.checkAllowlist` | 适配映射 | 命令白名单检查 |
| `ShellOperation._resolve_cwd` | `LocalShellOperation.resolveCwd` | 适配映射 | 工作目录解析 |
| `ShellOperation._wrap_command_with_buffering` | `LocalShellOperation.wrapCommandWithBuffering` | 适配映射 | 平台缓冲包装 |
| `FsOperation.read_file` | `LocalFsOperation.readFile` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.read_file_stream` | `LocalFsOperation.readFileStream` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.write_file` | `LocalFsOperation.writeFile` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.upload_file` | `LocalFsOperation.uploadFile` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.upload_file_stream` | `LocalFsOperation.uploadFileStream` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.download_file` | `LocalFsOperation.downloadFile` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.download_file_stream` | `LocalFsOperation.downloadFileStream` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.list_files` | `LocalFsOperation.listFiles` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.list_directories` | `LocalFsOperation.listDirectories` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation.search_files` | `LocalFsOperation.searchFiles` | 完全映射 | 公开 FS API 对齐 |
| `FsOperation._resolve_path` | `LocalFsOperation.resolvePath` | 适配映射 | 两侧都承担 work_dir 沙箱路径解析 |
| `FsOperation._apply_permissions` | `LocalFsOperation.applyPermissions` | 适配映射 | 权限应用 |
| `FsOperation._copy_permissions` | `LocalFsOperation.copyPermissions` | 适配映射 | 权限复制 |
| `AsyncProcessHandler.invoke()` | `ProcessHandler.invoke()` | 完全映射 | 一次性收集 stdout/stderr/exit code |
| `AsyncProcessHandler.stream()` | `ProcessHandler.stream()` | 适配映射 | `AsyncGenerator` 对应 `Iterator<StreamEvent>` |
| `OperationUtils.create_tmp_file(...)` | `OperationUtils.createTmpFile(...)` | 完全映射 | 临时文件创建 |
| `OperationUtils.delete_tmp_file(...)` | `OperationUtils.deleteTmpFile(...)` | 完全映射 | 临时文件删除 |
| `OperationUtils.prepare_environment(...)` | `OperationUtils.prepareEnvironment(...)` | 完全映射 | 环境变量合并 |
| `OperationUtils.create_handler(...)` | `OperationUtils.createHandler(...)` | 完全映射 | 处理器创建 |

### 2.7 Sandbox 实现

| Python API | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| `sandbox.CodeOperation.execute_code` | `SandboxCodeOperation.executeCode` | 完全映射 | 两侧都直接抛出“未实现” |
| `sandbox.CodeOperation.execute_code_stream` | `SandboxCodeOperation.executeCodeStream` | 完全映射 | 两侧都直接抛出“未实现” |
| `sandbox.FsOperation.*` 10 个 FS API | `SandboxFsOperation.*` 对应 10 个 FS API | 完全映射 | 两侧均为 stub |
| `sandbox.ShellOperation.execute_cmd` | `SandboxShellOperation.executeCmd` | 完全映射 | 两侧都直接抛出“未实现” |
| `sandbox.ShellOperation.execute_cmd_stream` | `SandboxShellOperation.executeCmdStream` | 完全映射 | 两侧都直接抛出“未实现” |

## 三、配置与结果对象字段映射

### 3.1 配置对象

| Python 类 | Python 字段 | Java 类 | Java 字段 |
| --- | --- | --- | --- |
| `LocalWorkConfig` | `shell_allowlist`, `work_dir` | `LocalWorkConfig` | `shellAllowlist`, `workDir` |
| `SandboxGatewayConfig` | `gateway_url`, `params`, `auth_headers`, `auth_query_params` | `SandboxGatewayConfig` | `gatewayUrl`, `params`, `authHeaders`, `authQueryParams` |

### 3.2 结果基类

| Python | Java | 状态 | 说明 |
| --- | --- | --- | --- |
| `BaseResult[T]` | `BaseResult<T>` | 完全映射 | `code/message/data` 三字段一致 |
| `build_operation_error_result(...)` | `BaseResult.buildOperationErrorResult(...)` | 适配映射 | Python 模块函数对应 Java 静态工厂方法，两种重载均已提供 |

### 3.3 Code 结果对象

| Python 类 | Java 类 | 字段映射 / 说明 |
| --- | --- | --- |
| `ExecuteCodeData` | `ExecuteCodeData` | `code_content -> codeContent`, `language -> language`, `exit_code -> exitCode`, `stdout -> stdout`, `stderr -> stderr` |
| `ExecuteCodeChunkData` | `ExecuteCodeChunkData` | `text -> text`, `type -> type`, `chunk_index -> chunkIndex`, `exit_code -> exitCode`, `metadata -> metadata` |
| `ExecuteCodeResult` | `ExecuteCodeResult` | 结果包装类，无额外公开 API |
| `ExecuteCodeStreamResult` | `ExecuteCodeStreamResult` | 结果包装类，无额外公开 API |

### 3.4 Shell 结果对象

| Python 类 | Java 类 | 字段映射 / 说明 |
| --- | --- | --- |
| `ExecuteCmdData` | `ExecuteCmdData` | `command -> command`, `cwd -> cwd`, `exit_code -> exitCode`, `stdout -> stdout`, `stderr -> stderr` |
| `ExecuteCmdChunkData` | `ExecuteCmdChunkData` | `text -> text`, `type -> type`, `chunk_index -> chunkIndex`, `exit_code -> exitCode`, `metadata -> metadata` |
| `ExecuteCmdResult` | `ExecuteCmdResult` | 结果包装类，无额外公开 API |
| `ExecuteCmdStreamResult` | `ExecuteCmdStreamResult` | 结果包装类，无额外公开 API |

### 3.5 FS 结果对象

| Python 类 | Java 类 | 字段映射 / 说明 |
| --- | --- | --- |
| `ReadFileData` | `ReadFileData` | `path -> path`, `content -> content`, `mode -> mode` |
| `ReadFileChunkData` | `ReadFileChunkData` | `path -> path`, `chunk_content -> chunkContent`, `mode -> mode`, `chunk_size -> chunkSize`, `chunk_index -> chunkIndex`, `is_last_chunk -> lastChunk` |
| `WriteFileData` | `WriteFileData` | `path -> path`, `size -> size`, `mode -> mode` |
| `UploadFileData` | `UploadFileData` | `local_path -> localPath`, `target_path -> targetPath`, `size -> size` |
| `UploadFileChunkData` | `UploadFileChunkData` | `local_path -> localPath`, `target_path -> targetPath`, `chunk_size -> chunkSize`, `chunk_index -> chunkIndex`, `is_last_chunk -> lastChunk` |
| `DownloadFileData` | `DownloadFileData` | `source_path -> sourcePath`, `local_path -> localPath`, `size -> size` |
| `DownloadFileChunkData` | `DownloadFileChunkData` | `source_path -> sourcePath`, `local_path -> localPath`, `chunk_size -> chunkSize`, `chunk_index -> chunkIndex`, `is_last_chunk -> lastChunk` |
| `FileSystemItem` | `FileSystemItem` | `name -> name`, `path -> path`, `size -> size`, `modified_time -> modifiedTime`, `is_directory -> directory`, `type -> type` |
| `FileSystemData` | `FileSystemData` | `total_count -> totalCount`, `list_items -> listItems`, `root_path -> rootPath`, `recursive -> recursive`, `max_depth -> maxDepth` |
| `SearchFilesData` | `SearchFilesData` | `total_matches -> totalMatches`, `matching_files -> matchingFiles`, `search_path -> searchPath`, `search_pattern -> searchPattern`, `exclude_patterns -> excludePatterns` |
| `ReadFileResult` | `ReadFileResult` | 结果包装类，无额外公开 API |
| `ReadFileStreamResult` | `ReadFileStreamResult` | 结果包装类，无额外公开 API |
| `WriteFileResult` | `WriteFileResult` | 结果包装类，无额外公开 API |
| `UploadFileResult` | `UploadFileResult` | 结果包装类，无额外公开 API |
| `UploadFileStreamResult` | `UploadFileStreamResult` | 结果包装类，无额外公开 API |
| `DownloadFileResult` | `DownloadFileResult` | 结果包装类，无额外公开 API |
| `DownloadFileStreamResult` | `DownloadFileStreamResult` | 结果包装类，无额外公开 API |
| `ListFilesResult` | `ListFilesResult` | 结果包装类，无额外公开 API |
| `ListDirsResult` | `ListDirsResult` | 结果包装类，无额外公开 API |
| `SearchFilesResult` | `SearchFilesResult` | 结果包装类，无额外公开 API |

## 四、需要特别说明的适配点

### 4.1 动态代理到显式调用

- Python 可以写 `card.fs.read_file`、`card.browser.navigate`、`sys_op.custom_op()`
- Java 对应写法为:
  - `card.fs().toolId("readFile")`
  - `card.proxy("browser").toolId("navigate")`
  - `sysOp.getOperation("customOp")`

### 4.2 异步模型适配

- Python `async def foo(...) -> Result` 对应 Java `foo(...) -> Result`
- Python `AsyncIterator<Result>` 对应 Java `Iterator<Result>`
- 这属于运行时模型适配，不计为缺漏

### 4.3 Java 侧额外补出的承接类型

- `FsConstants`
- `registry.Operation`
- `SysOperationToolAdapter.ToolEntry`

## 五、复核后的真实差异

- 当前不存在“整个类族缺失”的问题。
- 当前真实差异主要是 `LocalFsOperation` 的几个实现语义偏差:
  - `uploadFileStream(...)` 的 `lastChunk` 计算不完全对齐 Python
  - `resolvePath(...)` 没有完整保留 Python 的 `resolve + relative_to + sanitize` 语义
  - 文本模式 `readFile/readFileStream` 在部分场景下没有完整保留 Python 的原始换行语义

详见 `docs/FIXED/sys_operation_fixed.md`。
