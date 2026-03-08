# Common 模块 API 文档

> 包路径：`com.openjiuwen.core.common`

Common 模块提供框架级别的基础设施支持，包括常量定义、统一异常体系、日志系统、通用数据模式、安全工具以及常用工具类。

---

## 目录

- [1. 常量（constants）](#1-常量constants)
- [2. 异常体系（exception）](#2-异常体系exception)
- [3. 日志系统（logging）](#3-日志系统logging)
- [4. 数据模式（schema）](#4-数据模式schema)
- [5. 安全工具（security）](#5-安全工具security)
- [6. 工具类（utils）](#6-工具类utils)

---

## 1. 常量（constants）

### 1.1 Constant

全局常量定义类（不可实例化）。

**包路径**：`com.openjiuwen.core.common.constants`

| 常量名 | 类型 | 说明 |
|--------|------|------|
| `USER_FIELDS` | `String` | 用户字段标识 |
| `QUERY` | `String` | 查询字段标识 |
| `SYSTEM_FIELDS` | `String` | 系统字段标识 |
| `INTERACTION` | `String` | 交互字段标识 |
| `INTERACTIVE_INPUT` | `String` | 交互输入标识 |
| `INPUTS_KEY` | `String` | 输入键名 |
| `CONFIG_KEY` | `String` | 配置键名 |
| `END_FRAME` | `String` | 结束帧标识 |
| `END_NODE_STREAM` | `String` | 节点流结束标识 |
| `LOOP_ID` | `String` | 循环 ID 标识 |
| `INDEX` | `String` | 索引标识 |
| `FINISH_INDEX` | `String` | 结束索引标识 |
| `MAX_COLLECTION_SIZE` | `int` | 最大集合大小（100,000） |
| `MAX_EXPRESSION_LENGTH` | `int` | 最大表达式长度（5,000） |
| `MAX_AST_DEPTH` | `int` | 最大 AST 深度（50） |
| `NESTED_LOOP_DEPTH` | `int` | 嵌套循环深度（1） |

### 1.2 ControllerType

Agent 控制器类型枚举。

**包路径**：`com.openjiuwen.core.common.constants`

| 枚举值 | 说明 |
|--------|------|
| `REACT_CONTROLLER` | React 控制器 |
| `WORKFLOW_CONTROLLER` | 工作流控制器 |
| `UNDEFINED` | 未定义 |

**方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `String` | 获取枚举字符串值 |
| `fromValue(String value)` | `ControllerType` | 根据字符串值解析枚举（静态） |

### 1.3 TaskType

任务类型枚举。

**包路径**：`com.openjiuwen.core.common.constants`

| 枚举值 | 说明 |
|--------|------|
| `PLUGIN` | 插件任务 |
| `WORKFLOW` | 工作流任务 |
| `MCP` | MCP 任务 |
| `UNDEFINED` | 未定义 |

**方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `String` | 获取枚举字符串值 |
| `fromValue(String value)` | `TaskType` | 根据字符串值解析枚举（静态） |

---

## 2. 异常体系（exception）

### 2.1 BaseError

框架统一异常基类，支持模板化消息渲染。

**包路径**：`com.openjiuwen.core.common.exception`  
**继承**：`RuntimeException`

**构造方法**：
```java
BaseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)
BaseError(StatusCode status, String msg, Object details, Throwable cause)
BaseError(StatusCode status, Map<String, Object> params)
BaseError(StatusCode status)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toMap()` | `Map<String, Object>` | 转换为 Map 表示 |
| `getStatus()` | `StatusCode` | 获取状态码 |
| `getCode()` | `int` | 获取错误码数值 |
| `getParams()` | `Map<String, Object>` | 获取模板参数 |
| `getDetails()` | `Object` | 获取错误详情 |
| `getTemplateMessage()` | `String` | 获取模板消息 |
| `getMessage()` | `String` | 获取渲染后的错误消息 |
| `isRecoverable()` | `boolean` | 是否可恢复 |
| `isFatal()` | `boolean` | 是否致命 |

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `renderMessage(StatusCode status, Map<String, Object> params)` | `String` | 渲染错误消息模板 |
| `formatTemplate(String template, Map<String, Object> params)` | `String` | 格式化模板字符串 |

### 2.2 StatusCode

统一状态/错误码枚举，覆盖所有子系统（150+ 状态码）。

**包路径**：`com.openjiuwen.core.common.exception`

**代表性枚举值**：`SUCCESS`、`ERROR`、`WORKFLOW_EXECUTE_TIMEOUT`、`WORKFLOW_COMPONENT_EXECUTION_ERROR`、`AGENT_CONTROLLER_INVOKE_CALL_FAILED`、`COMPONENT_LLM_INVOKE_CALL_FAILED`、`TOOL_CARD_INVALID`、`MODEL_CALL_FAILED` 等。

**方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getCode()` | `int` | 获取数字错误码 |
| `getErrmsg()` | `String` | 获取错误消息模板 |

### 2.3 ErrorHelper

异常创建工厂及便捷方法（不可实例化）。

**包路径**：`com.openjiuwen.core.common.exception`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `buildError(StatusCode status)` | `BaseError` | 构建异常实例 |
| `buildError(StatusCode status, String... kvPairs)` | `BaseError` | 使用键值对构建异常 |
| `buildError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | `BaseError` | 完整参数构建异常 |
| `raiseError(StatusCode status)` | `void` | 抛出执行异常 |
| `raiseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | `void` | 完整参数抛出异常 |
| `systemError(StatusCode status)` | `void` | 抛出系统（框架）异常 |
| `systemError(StatusCode status, Throwable cause, Map<String, Object> params)` | `void` | 带原因抛出系统异常 |
| `validateError(StatusCode status)` | `void` | 抛出验证异常 |
| `validateError(StatusCode status, Throwable cause, Map<String, Object> params)` | `void` | 带原因抛出验证异常 |
| `terminate(StatusCode status)` | `void` | 抛出终止异常 |
| `terminate(StatusCode status, Map<String, Object> params)` | `void` | 带参数抛出终止异常 |

### 2.4 异常类继承体系

```
RuntimeException
└── BaseError                         (可恢复=false, 致命=false)
    ├── ExecutionError                (可恢复=true, 致命=false)
    │   ├── AgentError
    │   ├── ApplicationError
    │   ├── ComponentError
    │   ├── ContextError
    │   ├── ExternalDataError
    │   ├── ExternalServiceError
    │   ├── GraphError
    │   ├── ModelError
    │   ├── RunnerError
    │   ├── SessionError
    │   ├── SysOperationError
    │   ├── ToolchainError
    │   ├── ToolError                 (携带 BaseCard 引用)
    │   └── WorkflowError
    ├── FrameworkError                (可恢复=false, 致命=true)
    │   └── ConfigurationError
    ├── ValidationError               (可恢复=false, 致命=false)
    │   └── GuardrailError
    └── Termination                   (非错误控制流终止)
        └── RunnerTermination         (携带 reason 字段)
```

### 2.5 StatusCodeTemplate

状态码模板记录（Record）。

**包路径**：`com.openjiuwen.core.common.exception`

**字段**：`name`、`codeSuggestion`、`messageTemplate`、`exceptionSemantic`

**静态方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `generate(String scope, String subject, String failureType, String detail)` | `StatusCodeTemplate` | 生成状态码模板 |
| `generate(String scope, String subject, String failureType)` | `StatusCodeTemplate` | 生成状态码模板（无详情） |

### 2.6 StatusMapping

根据状态码解析异常类（不可实例化）。

**包路径**：`com.openjiuwen.core.common.exception`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `resolveExceptionFactory(StatusCode status)` | `Function<StatusCode, BaseError>` | 获取异常工厂函数 |
| `resolveException(StatusCode status)` | `BaseError` | 根据状态码生成异常实例 |
| `buildStatusExceptionMap()` | `Map<StatusCode, Function<StatusCode, BaseError>>` | 构建状态码到异常工厂的完整映射 |

---

## 3. 日志系统（logging）

### 3.1 LoggerProtocol

标准日志接口，定义所有日志实现的公共契约。

**包路径**：`com.openjiuwen.core.common.logging`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `debug(String msg, Object... args)` | `void` | 调试级别日志 |
| `info(String msg, Object... args)` | `void` | 信息级别日志 |
| `warning(String msg, Object... args)` | `void` | 警告级别日志 |
| `error(String msg, Object... args)` | `void` | 错误级别日志 |
| `critical(String msg, Object... args)` | `void` | 严重级别日志 |
| `exception(String msg, Throwable t, Object... args)` | `void` | 异常日志 |
| `log(int level, String msg, Object... args)` | `void` | 指定级别日志 |
| `setLevel(int level)` | `void` | 设置日志级别 |
| `getConfig()` | `Map<String, Object>` | 获取日志配置 |
| `reconfigure(Map<String, Object> config)` | `void` | 重新配置日志 |

### 3.2 LazyLogger

延迟初始化日志包装器，实现 `LoggerProtocol`。

**包路径**：`com.openjiuwen.core.common.logging`

**构造方法**：
```java
LazyLogger(Supplier<LoggerProtocol> getter)
```

### 3.3 Loggers

预定义模块级日志实例（懒加载单例，不可实例化）。

**包路径**：`com.openjiuwen.core.common.logging`

| 静态字段 | 类型 | 说明 |
|----------|------|------|
| `COMMON` | `LoggerProtocol` | 通用日志 |
| `INTERFACE` | `LoggerProtocol` | 接口日志 |
| `PERFORMANCE` | `LoggerProtocol` | 性能日志 |
| `PROMPT_BUILDER` | `LoggerProtocol` | Prompt 构建日志 |
| `AGENT` | `LoggerProtocol` | Agent 日志 |
| `MULTI_AGENT` | `LoggerProtocol` | 多 Agent 日志 |
| `WORKFLOW` | `LoggerProtocol` | 工作流日志 |
| `SESSION` | `LoggerProtocol` | 会话日志 |
| `CONTROLLER` | `LoggerProtocol` | 控制器日志 |
| `RUNNER` | `LoggerProtocol` | Runner 日志 |
| `SYS_OPERATION` | `LoggerProtocol` | 系统操作日志 |
| `LLM` | `LoggerProtocol` | 大模型日志 |
| `TOOL` | `LoggerProtocol` | 工具日志 |
| `PROMPT` | `LoggerProtocol` | Prompt 日志 |
| `STORE` | `LoggerProtocol` | 存储日志 |
| `MEMORY` | `LoggerProtocol` | 记忆日志 |
| `RETRIEVAL` | `LoggerProtocol` | 检索日志 |
| `CONTEXT_ENGINE` | `LoggerProtocol` | 上下文引擎日志 |
| `GRAPH` | `LoggerProtocol` | 图日志 |
| `OPERATOR` | `LoggerProtocol` | 操作符日志 |
| `MCP` | `LoggerProtocol` | MCP 日志 |

### 3.4 LogManager

日志管理器，负责日志创建、注册和检索（不可实例化）。

**包路径**：`com.openjiuwen.core.common.logging`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setDefaultLoggerFactory(LoggerFactory factory)` | `void` | 设置默认日志工厂 |
| `initialize()` | `void` | 初始化日志系统（同步） |
| `registerLogger(String logType, LoggerProtocol logger)` | `void` | 注册日志实例 |
| `getLogger(String logType)` | `LoggerProtocol` | 获取指定类型日志 |
| `getAllLoggers()` | `Map<String, LoggerProtocol>` | 获取所有日志实例 |
| `reset()` | `void` | 重置日志系统（同步） |

### 3.5 LoggingUtils

日志工具类，提供链路追踪 ID 上下文管理（不可实例化）。

**包路径**：`com.openjiuwen.core.common.logging`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setSessionId(String traceId)` | `void` | 设置会话追踪 ID |
| `getSessionId()` | `String` | 获取会话追踪 ID |
| `clearSessionId()` | `void` | 清除会话追踪 ID |
| `getLogMaxBytes(Object maxBytesConfig)` | `int` | 获取日志最大字节数 |
| `normalizeAndValidateLogPath(Object pathValue)` | `String` | 规范化并验证日志路径 |

### 3.6 日志事件系统（logging.events）

#### BaseLogEvent

日志事件基类（Lombok `@Data`）。

**包路径**：`com.openjiuwen.core.common.logging.events`

**主要字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventId` | `String` | 事件唯一 ID |
| `eventType` | `LogEventType` | 事件类型 |
| `logLevel` | `LogLevel` | 日志级别 |
| `timestamp` | `Instant` | 时间戳 |
| `moduleType` | `ModuleType` | 模块类型 |
| `sessionId` | `String` | 会话 ID |
| `traceId` | `String` | 追踪 ID |
| `status` | `EventStatus` | 事件状态 |
| `message` | `String` | 消息内容 |
| `metadata` | `Map<String, Object>` | 元数据 |

**方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toMap()` | `Map<String, Object>` | 转换为 Map |

#### LogEventType

日志事件类型枚举，包含 60+ 事件类型。

**代表性枚举值**：`AGENT_START`、`WORKFLOW_EXECUTE_START`、`LLM_CALL_START`、`TOOL_CALL_START`、`SESSION_CREATE`、`CONTEXT_ADD_MESSAGE`、`GRAPH_VERTEX_INIT`、`RUNNER_START` 等。

#### LogLevel

日志级别枚举：`DEBUG`、`INFO`、`WARNING`、`ERROR`、`CRITICAL`。

#### ModuleType

模块类型枚举：`AGENT`、`WORKFLOW`、`WORKFLOW_COMPONENT`、`LLM`、`TOOL`、`STORE`、`MEMORY`、`SESSION`、`CONTEXT`、`RETRIEVAL`、`SYSTEM`、`USER`、`SYS_OPERATION`。

#### EventStatus

事件状态枚举：`SUCCESS`、`FAILURE`、`PENDING`、`TIMEOUT`、`CANCELLED`。

#### 具体事件类

所有事件类均继承 `BaseLogEvent`：

| 事件类 | 核心字段 |
|--------|----------|
| `AgentEvent` | `agentType`、`inputData`、`outputData`、`iterationCount`、`executionTimeMs` |
| `ContextEvent` | `messageType`、`messageContent`、`messageRole`、`contextSize` |
| `GraphEvent` | `graphId`、`nodeId`、`nodeName`、`inputs`、`outputs` |
| `LLMEvent` | `modelName`、`modelProvider`、`query`、`messages`、`tools`、`temperature`、`latencyMs` |
| `MemoryEvent` | `memoryType`、`operation`、`memoryId`、`query` |
| `PerformanceEvent` | `metricName`、`metricValue`、`metricUnit` |
| `RunnerEvent` | `runnerId`、`inputs`、`outputs`、`resourceType` |
| `SessionEvent` | `sessionType`、`userId`、`agentId`、`workflowId` |
| `StoreEvent` | `tableName`、`dataNum` |
| `StreamEvent` | `streamType`、`chunkIndex`、`streamId` |
| `SysOperationEvent` | `operationName`、`operationMode`、`methodName`、`methodExecTimeMs` |
| `ToolEvent` | `toolName`、`toolType`、`arguments`、`result`、`executionTimeMs` |
| `WorkflowEvent` | `workflowId`、`workflowName`、`componentId`、`executionTimeMs` |

#### EventSanitizer

日志脱敏工具，对敏感字段进行脱敏处理（不可实例化）。

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `sanitizeEventForLogging(BaseLogEvent event)` | `Map<String, Object>` | 使用默认敏感字段脱敏 |
| `sanitizeEventForLogging(BaseLogEvent event, List<String> sensitiveFields)` | `Map<String, Object>` | 使用自定义敏感字段脱敏 |

### 3.7 默认日志实现（logging.defaults）

#### DefaultLogger

默认日志实现，基于 SLF4J + Logback。

**包路径**：`com.openjiuwen.core.common.logging.defaults`

**构造方法**：
```java
DefaultLogger(String logType, Map<String, Object> config)
```

#### LogConfig

日志配置管理，支持从 YAML 加载按日志类型的配置。

**包路径**：`com.openjiuwen.core.common.logging.defaults`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getCommonConfig()` | `Map<String, Object>` | 获取通用日志配置 |
| `getInterfaceConfig()` | `Map<String, Object>` | 获取接口日志配置 |
| `getPerformanceConfig()` | `Map<String, Object>` | 获取性能日志配置 |
| `getCustomConfig(String logType)` | `Map<String, Object>` | 获取自定义日志配置 |
| `getAllConfigs()` | `Map<String, Map<String, Object>>` | 获取所有日志配置 |

---

## 4. 数据模式（schema）

### 4.1 BaseCard

所有卡片（Card）实体的根基类。

**包路径**：`com.openjiuwen.core.common.schema`

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 唯一标识（默认 UUID 十六进制） |
| `name` | `String` | 名称 |
| `description` | `String` | 描述 |

**方法**：
| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toolInfo()` | `Object` | 获取工具信息表示 |
| `copy()` | `BaseCard` | 副本复制 |

### 4.2 Param

参数定义类，支持嵌套结构。

**包路径**：`com.openjiuwen.core.common.schema`

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 参数名称 |
| `description` | `String` | 参数描述 |
| `type` | `ParamType` | 参数类型 |
| `required` | `boolean` | 是否必填 |
| `defaultValue` | `Object` | 默认值 |
| `items` | `Param` | 数组元素定义 |
| `properties` | `List<Param>` | 对象属性定义 |

**工厂方法**：

| 方法签名 | 说明 |
|----------|------|
| `Param.string(name, description, required)` | 创建字符串参数 |
| `Param.string(name, description, required, defaultValue)` | 创建带默认值的字符串参数 |
| `Param.bool(name, description, required)` | 创建布尔参数 |
| `Param.integer(name, description, required)` | 创建整数参数 |
| `Param.number(name, description, required)` | 创建数字参数 |
| `Param.array(name, description, required, items)` | 创建数组参数 |
| `Param.object(name, description, required, properties)` | 创建对象参数 |

### 4.3 ParamType

参数类型枚举。

| 枚举值 | 说明 |
|--------|------|
| `STRING` | 字符串 |
| `BOOLEAN` | 布尔 |
| `INTEGER` | 整数 |
| `NUMBER` | 数字 |
| `ARRAY` | 数组 |
| `OBJECT` | 对象 |

---

## 5. 安全工具（security）

### 5.1 JsonUtils

安全的 JSON 序列化/反序列化工具（基于 Jackson）。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `safeJsonLoads(String json, Class<T> type, T defaultValue)` | `<T> T` | 安全反序列化（带默认值） |
| `safeJsonLoads(String json, Class<T> type)` | `<T> T` | 安全反序列化 |
| `safeJsonDumps(Object obj, String defaultValue)` | `String` | 安全序列化（带默认值） |
| `safeJsonDumps(Object obj)` | `String` | 安全序列化 |
| `getMapper()` | `ObjectMapper` | 获取 Jackson ObjectMapper 实例 |

### 5.2 PathChecker

路径敏感性检查器（线程安全单例）。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getInstance()` | `PathChecker` | 获取单例实例（静态） |
| `isSensitivePath(String path)` | `boolean` | 检查路径是否敏感（静态） |
| `checkSensitive(String path)` | `boolean` | 实例方法：检查敏感路径 |

### 5.3 SslUtils

SSL 工具类，用于严格的 HTTPS 通信。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createStrictSslContext(String sslCertPath)` | `SSLContext` | 创建严格的 SSL 上下文 |
| `getSslConfig(String verifySwitchEnv, String sslCertEnv, List<String> triggerValues, boolean urlIsHttps)` | `Object[]` | 获取 SSL 配置信息 |

### 5.4 UrlUtils

URL 验证与代理工具（SSRF 防护）。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `checkUrlIsValid(String url)` | `void` | 校验 URL 合法性 |
| `getGlobalProxyUrl(String url)` | `String` | 获取全局代理 URL |
| `getGlobalProxies(String url)` | `Map<String, String>` | 获取全局代理配置 |
| `shouldBypassProxy(String url)` | `boolean` | 判断是否应绕过代理 |

### 5.5 UserConfig

用户安全配置（线程安全单例）。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setConfigPath(Path path)` | `void` | 设置配置文件路径（静态） |
| `getConfig()` | `UserConfig` | 获取配置单例（静态） |
| `isSensitive()` | `boolean` | 是否启用敏感路径检查（静态） |
| `getSensitivePaths()` | `List<String>` | 获取敏感路径列表（静态） |
| `reset()` | `void` | 重置配置（静态、同步） |

### 5.6 ExceptionUtils

异常工具类。

**包路径**：`com.openjiuwen.core.common.security`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `formatValidationError(Throwable t)` | `String` | 格式化验证异常信息 |
| `getRootCause(Throwable t)` | `Throwable` | 获取根因异常 |

---

## 6. 工具类（utils）

### 6.1 DictUtils

嵌套 Map（字典）操作工具（不可实例化）。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createNestedMap(String path, Object value, String separator)` | `Object` | 根据路径创建嵌套 Map |
| `createNestedMap(String path, Object value)` | `Object` | 使用默认分隔符创建嵌套 Map |
| `flattenMap(Map<String, Object> data)` | `Map<String, Object>` | 扁平化嵌套 Map |
| `extractLeafNodes(Object data, List<String> currentPath)` | `List<Map.Entry<List<String>, Object>>` | 提取叶子节点 |
| `formatPath(List<String> path)` | `String` | 格式化路径 |
| `rebuildMapFromPaths(Iterable<...> pathValuePairs)` | `Map<String, Object>` | 根据路径值对重建 Map |

### 6.2 HashUtil

SHA-256 哈希生成工具。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `generateKey(String apiKey, String apiBase, String modelProvider)` | `String` | 根据凭证生成哈希键 |
| `generateKey(String apiKey, String apiBase)` | `String` | 根据凭证生成哈希键（无 provider） |

### 6.3 MessageUtils

消息上下文管理工具。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `shouldAddUserMessage(String query, ContextEngine contextEngine, Session session)` | `boolean` | 判断是否应添加用户消息 |
| `addUserMessage(Object query, ContextEngine contextEngine, Session session)` | `void` | 添加用户消息 |
| `addAiMessage(AssistantMessage msg, ContextEngine contextEngine, Session session)` | `void` | 添加 AI 消息 |
| `addToolMessage(ToolMessage msg, ContextEngine contextEngine, Session session)` | `void` | 添加工具消息 |
| `addWorkflowMessage(BaseMessage msg, String workflowId, ContextEngine contextEngine, Session session)` | `void` | 添加工作流消息 |
| `getChatHistory(ContextEngine contextEngine, Session session, int maxRounds)` | `List<BaseMessage>` | 获取聊天历史 |

### 6.4 SchemaUtils

JSON Schema 验证与数据格式化工具。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `formatWithSchema(Map<String, Object> data, Map<String, Object> schema)` | `Map<String, Object>` | 按 Schema 格式化数据 |
| `validateWithSchema(Map<String, Object> data, Map<String, Object> schema)` | `void` | 按 Schema 验证数据 |
| `getSchemaDict(Class<?> clazz)` | `Map<String, Object>` | 获取类的 Schema 字典 |

### 6.5 SingletonSupport

通用线程安全单例支持（抽象类）。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getInstance(Class<T> clazz, Supplier<T> factory)` | `<T> T` | 获取或创建单例（静态） |
| `reset(Class<?> clazz)` | `void` | 重置指定类型的单例（静态） |

### 6.6 IpUtils

本机 IP 地址发现工具。

**包路径**：`com.openjiuwen.core.common.utils`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getLocalIp()` | `String` | 获取本机 IP 地址（静态） |
