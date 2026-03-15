# common 模块缺漏复核清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\common`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\common`
- 本文只记录“Java 相对 Python 仍未完全对齐的公开 API / 可见语义差异”
- 默认不计入缺漏:
  - `snake_case -> camelCase`
  - `property/dataclass -> getter/builder/record`
  - `async -> 同步方法`
  - Python 模块函数收口到 Java `static` helper

## 第二轮复核结论

- 第二轮复查显示，上一轮标记的核心缺口中已有多项被补齐。
- 本轮确认已补齐的重点:
  - `BaseError.toJson()`
  - `logging.events.WorkflowStreamEvent`
  - `EventClassRegistry.validateEvent(...)`
  - `EventClassRegistry.createEvent(LogEventType, Map)` 与 `createEvent(String)`
  - `logging.defaults.LoggingDefaults`，用于承接 `config` / `log_config` / `configure` / `configure_log`
  - `UserConfig.setSensitive(boolean)` 与公开 `getSensitivePathsList()`
  - `SchemaUtils.removeNoneValues(...)` 与 `formatWithSchema(..., skipNoneValue, skipValidate)`
- 当前剩余问题已经从“缺类/缺方法”为主，收敛为“部分 API 仍未完全对齐”与“若干公开语义差异”。

## 第二轮已修复项

| 状态 | 位置 | 上轮问题 | 本轮现状 |
| --- | --- | --- | --- |
| `FIXED` | `exception.BaseError` | 缺少 `toJson()` | 已新增 `toJson()` |
| `FIXED` | `logging.events.WorkflowStreamEvent` | Java 缺少对位类型 | 已新增 `WorkflowStreamEvent` |
| `FIXED` | `logging.events.validate_event()` | 无独立校验入口 | 已新增 `validateEvent(BaseLogEvent)` |
| `FIXED` | `logging.defaults` 全局门面 | 无 `config/log_config/configure/configure_log` 对位入口 | 已新增 `LoggingDefaults.config()/logConfig()/configure()/configureLog()` |
| `FIXED` | `security.UserConfig.set_is_sensitive()` | 无运行时敏感开关 setter | 已新增 `setSensitive(boolean)` |
| `FIXED` | `security.UserConfig.get_sensitive_paths_list()` | 仅私有实例方法 | 已改为公开 `getSensitivePathsList()` |
| `FIXED` | `utils.SchemaUtils.remove_none_values()` | 无对位清理入口 | 已新增 `removeNoneValues(...)` |
| `FIXED` | `utils.SchemaUtils.format_with_schema(..., skip_none_value=...)` | 无 `skipNoneValue` 入口 | 已新增四参 `formatWithSchema(...)` |

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `logging.events.create_log_event(event_type: str, **kwargs)` | 支持 `LogEventType | str`，并把字符串事件类型直接写入 `event.event_type`，同时支持一步注入字段 | Java 虽新增 `createEvent(String)` 与 `createEvent(LogEventType, Map)`，但 `BaseLogEvent.eventType` 仍是 `LogEventType`，无法完整承载 Python 的“自定义字符串事件类型”；同时没有 `createEvent(String, Map)` 对位入口 | 自定义事件注册场景仍无法完整按 Python API 迁移 |
| `P1` | `utils.SchemaUtils.get_schema_class()` | 支持 `JSON Schema -> Pydantic model` | Java 仍无公开 `getSchemaClass(...)` | 缺少反向“schema 转类/模型”能力 |
| `P1` | `utils.SchemaUtils` 参数泛化能力 | Python `format_with_schema/validate_with_schema` 接受 `Any` 数据和 `Dict | BaseModel` schema | Java 仍只接受 `Map<String,Object>` 数据与 `Map<String,Object>` schema，`getSchemaDict()` 也只做 `Class<?> -> schema` | Python 的非对象根 schema、类 schema 输入、双向转换能力仍未完整迁移 |
| `P1` | `security.SslUtils.create_ssl_adapter()` | 直接返回 `requests.adapters.HTTPAdapter` | Java 仍无对位 API，仅保留 `SSLContext` 能力 | Python HTTP 客户端接入点无法原样迁移 |
| `P1` | `security.SslUtils.get_ssl_config()` 语义 | 当 verify 开启且证书环境变量缺失时，抛出 `COMMON_SSL_CERT_INVALID` | Java `getSslConfig(...)` 当前返回 `[true, null]`，不抛错 | 调用方若依赖提前失败语义，Java 行为会更晚暴露错误 |
| `P2` | `security.UserConfig.set_config_path()` 语义 | Python 会做 `_resolve_and_check()`，要求配置文件位于当前 root 内，并抛出结构化业务异常 | Java `setConfigPath()` 仅 `toAbsolutePath().normalize()`，不校验是否位于 root 内，已初始化时抛 `IllegalStateException` 而非 `BaseError` | 路径约束与异常语义仍未完全一致 |
| `P2` | `logging.default.ConfigDict` | 暴露独立字典包装对象，支持 `config.get(...)`、`config()`、`refresh()` | Java 已用 `LoggingDefaults.config()` 补足主要访问入口，但没有独立 `ConfigDict` 类型及 `refresh()` 对位方法 | 少量依赖 `ConfigDict` 具体类型/调用方式的代码不能原样迁移 |
| `P2` | `logging.default.SafeRotatingFileHandler` | 公开安全轮转 handler 类型 | Java 仍无同名公开类 | 外部若要直接复用 handler 类型，仍无对位 API |
| `P2` | `logging.default.ContextFilter` | 公开上下文过滤器类型 | Java 仍无同名公开类 | 外部扩展 logging filter 的方式仍与 Python 不同 |
| `P2` | `logging.events.create_log_event(..., **kwargs)` 属性名语义 | Python 直接使用 dataclass 的 `snake_case` 字段名 | Java `createEvent(LogEventType, Map)` 通过 setter 反射写值，实际更偏向 `camelCase` key | 迁移时如果沿用 Python 字段名，属性注入可能被忽略 |
| `P3` | `security.UserConfig.get_sensitive_paths_list()` 返回值语义 | Python 返回 `copy()` | Java 返回内部缓存列表本身 | 若调用方假设每次返回副本并尝试修改，Java 语义不同 |

## 第二轮建议优先级

1. `logging.events`
   - 优先补 `createEvent(String, Map<String, Object>)`。
   - 若要真正兼容 Python 自定义事件，需允许事件对象承载字符串型 `event_type`，而不只是 `LogEventType` 枚举。
   - 如需兼容 Python kwargs，建议同时接受 `snake_case` 和 `camelCase` 属性名。
2. `utils.SchemaUtils`
   - 补公开 `getSchemaClass(...)` 或等价“schema -> 可校验对象模型”入口。
   - 评估是否需要支持非 `Map` 根数据和 `Class`/模型类型作为 schema 输入。
3. `security`
   - 让 `SslUtils.getSslConfig(...)` 在 verify 开启且证书缺失时与 Python 一样直接抛错。
   - 让 `UserConfig.setConfigPath(...)` 补上 root containment 校验，并统一异常语义。
4. `logging.defaults`
   - 如果需要严格贴近 Python API，再考虑补 `ConfigDict` / `SafeRotatingFileHandler` / `ContextFilter` 对位类型。

## 语言/框架适配差异

以下差异已确认存在，但更偏向语言模型不同，不建议默认按缺陷处理:

- Python `common.__init__`、`schema.__init__`、`logging.__init__`、`logging.default.__init__` 的 `__all__` 门面，在 Java 中改成显式导入具体类/静态字段。
- `Param.boolean()` 在 Java 中只能以 `bool()` 命名，因为 `boolean` 是关键字。
- Python `Singleton` 元类在 Java 中改为 `SingletonSupport.getInstance(...)`。
- Python logging 的模块级全局 logger，在 Java 中改为 `Loggers` 常量。
- Python `SslUtils.create_ssl_adapter()` 属于 `requests` 栈特有能力；Java 更自然的承载方式是 `SSLContext`。

## 已确认不缺的部分

- `constants.Constant` 与 Python 常量模块字段已对齐。
- 异常体系主干类均已存在: `FrameworkError`、`ValidationError`、`ExecutionError`、`ApplicationError`、`ExternalServiceError`、`ExternalDataError`、`Termination`、`RunnerTermination`、`WorkflowError`、`ComponentError`、`AgentError`、`RunnerError`、`GraphError`、`ModelError`、`ToolError`、`ContextError`、`ToolchainError`、`SessionError`、`SysOperationError`、`GuardrailError`。
- `logging` 主干类型已存在: `LazyLogger`、`LoggerProtocol`、`LogManager`、`Loggers`、`DefaultLogger`、`BaseLogEvent` 及大多数事件子类。
- `logging.events.WorkflowStreamEvent`、`EventClassRegistry.validateEvent(...)` 已在第二轮补齐。
- `logging.defaults.LoggingDefaults` 已在第二轮补齐 Python 风格全局门面。
- `security` 主干类型已存在: `ExceptionUtils`、`JsonUtils`、`PathChecker`、`SslUtils`、`UrlUtils`、`UserConfig`。
- `security.UserConfig.setSensitive(boolean)` 与公开 `getSensitivePathsList()` 已在第二轮补齐。
- `utils` 主干类型已存在: `DictUtils`、`HashUtil`、`IpUtils`、`MessageUtils`、`SchemaUtils`、`SingletonSupport`。
- `SchemaUtils.removeNoneValues(...)` 与 `skipNoneValue` 入口已在第二轮补齐。
