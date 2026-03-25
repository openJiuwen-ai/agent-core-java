# common 模块 Python / Java API 映射

## 对照范围

- Python: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\common`
- Java: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\common`
- 统计口径:
  - Python 统计模块导出、公共类、公共顶层函数、非 `_` 公共方法
  - Java 统计 `public`/`protected` 类型、`public` 方法、可见的静态工厂/工具方法
- 映射约定:
  - `snake_case -> camelCase`
  - 模块函数 -> Java `static` 方法 / Helper 类
  - `property` / dataclass / Pydantic 字段 -> getter / builder / record 组件
  - `async` -> 同步方法
  - Python `__all__` 包级门面 -> Java 显式导入具体类/静态字段

## 总体结论

- `constants`、`exception`、`security`、`utils` 主体 API 已高度对齐。
- Java `logging` 在第二轮补齐了更多 Python 风格门面能力，新增 `WorkflowStreamEvent`、`validateEvent(...)`、`LoggingDefaults` 等对位入口。
- 当前仍有少量未完全对齐的点，主要集中在 `logging.events` 的自定义字符串事件、`security.SslUtils/UserConfig.setConfigPath` 的部分语义、`utils.SchemaUtils.getSchemaClass()` 与泛化 schema 能力，详见 `docs/FIXED/common_fixed.md`。

## 1. 包级导出与门面

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `openjiuwen.core.common.__all__ = ["BaseCard", "Param"]` | 无同名 package facade，直接使用 `com.openjiuwen.core.common.schema.BaseCard`、`Param` | 包级导出 -> 直接导入类 | 适配映射 | Java 没有 `__init__`/`__all__` 机制 |
| `openjiuwen.core.common.schema.__all__` | `com.openjiuwen.core.common.schema.*` | `BaseCard/Param/ParamType` -> 直接导入对应类 | 适配映射 | Java 另外新增了 `Part` |
| `openjiuwen.core.common.logging.__all__` | `Loggers`、`LoggingUtils`、`LogManager`、`logging.events.*` | 模块全局对象/函数 -> 静态字段与工具类 | 适配映射 | Java 按类型拆分，不保留单一门面模块 |
| `openjiuwen.core.common.logging.default.__all__` | `logging.defaults.*` + `LoggingDefaults` | Python 包级门面 -> Java facade 类 + 具体类 | 适配映射 | Java 新增 `LoggingDefaults` 承载 `config/log_config/configure/configureLog` 门面 |
| `constants.__init__`、`exception.__init__`、`security.__init__`、`utils.__init__` | 同名 package | 无额外 API | 完全映射 | 两侧均无额外门面行为 |

## 2. constants

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `constants.constant` 模块常量 | `constants.Constant` 静态常量类 | `USER_FIELDS`、`QUERY`、`SYSTEM_FIELDS`、`INTERACTION`、`INTERACTIVE_INPUT`、`INPUTS_KEY`、`CONFIG_KEY`、`END_FRAME`、`END_NODE_STREAM`、`LOOP_ID`、`INDEX`、`FINISH_INDEX`、`MAX_COLLECTION_SIZE`、`MAX_EXPRESSION_LENGTH`、`MAX_AST_DEPTH`、`NESTED_LOOP_DEPTH` 一一对应 | 完全映射 | Python 为模块常量，Java 收口到 `Constant` |
| `ControllerType` | `ControllerType` | 枚举值 `ReActController/WorkflowController/Undefined -> REACT_CONTROLLER/WORKFLOW_CONTROLLER/UNDEFINED`；`.value -> getValue()` | 适配映射 | Java 额外提供 `fromValue(String)` |
| `TaskType` | `TaskType` | 枚举值 `PLUGIN/WORKFLOW/MCP/UNDEFINED` 一一对应；`.value -> getValue()` | 完全映射 | Java 额外提供 `fromValue(String)` |

## 3. exception

### 3.1 状态码模板与工厂函数

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `StatusCodeTemplate` + `generate_status_code(...)` | `StatusCodeTemplate` + `StatusCodeTemplate.generate(...)` | dataclass 字段 `name/code_suggestion/message_template/exception_semantic` -> record 组件 `name()/codeSuggestion()/messageTemplate()/exceptionSemantic()` | 完全映射 | Python 模块函数在 Java 中变为 record 静态工厂 |
| `ErrorMessageTemplate` + `generate_error_message_template(...)` | `ErrorMessageTemplate` + `ErrorMessageTemplate.generate(...)` | `template/params` -> record 组件；`generate_error_message_template -> generate` | 完全映射 | `with_reason -> withReason` |
| `StatusCodeSpec` + `generate_status_code_spec(...)` + `render_enum_member(...)` | `StatusCodeSpec` + `StatusCodeSpec.fromTemplate(...)` + `renderEnumMember()` | `generate_status_code_spec -> fromTemplate`；`render_enum_member -> renderEnumMember()` | 完全映射 | 生成逻辑位置从模块函数迁到 record |
| `StatusCode` | `StatusCode` | `code -> getCode()`；`errmsg -> getErrmsg()` | 完全映射 | Python `Enum` 属性对应 Java getter |
| `status_mapping.resolve_exception_class` | `StatusMapping.resolveExceptionFactory()` / `resolveException()` | `resolve_exception_class -> resolveExceptionFactory`（返回工厂）或 `resolveException`（直接建异常） | 适配映射 | Java 提供了更细的两层 API |
| `status_mapping.build_status_exception_map` | `StatusMapping.buildStatusExceptionMap()` | 同名语义映射 | 完全映射 | 返回类型从 `Type` 映射变成异常工厂映射 |
| `build_error` | `ErrorHelper.buildError(...)` | `build_error(status, msg, details, cause, **kwargs) -> buildError(StatusCode, ...)` | 完全映射 | `**kwargs` 在 Java 中映射为 `Map<String,Object>` / `String... kvPairs` |
| `raise_error` | `ErrorHelper.raiseError(...)` | 同名语义映射 | 完全映射 | Java 直接 `throw` |
| `system_error` | `ErrorHelper.systemError(...)` | 同名语义映射 | 完全映射 | - |
| `validate_error` | `ErrorHelper.validateError(...)` | 同名语义映射 | 完全映射 | - |
| `terminate` | `ErrorHelper.terminate(...)` | 同名语义映射 | 完全映射 | - |

### 3.2 异常基类与子类

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `BaseError` | `BaseError` | `to_dict -> toMap()`；`to_json -> toJson()`；`__str__ -> toString()`；`status/code/params/details/message -> getStatus()/getCode()/getParams()/getDetails()/getMessage()` | 完全映射 | 第二轮已补齐 `toJson()` |
| `FrameworkError` | `FrameworkError` | 类名 1:1；`recoverable/fatal` 语义由 `defaultRecoverable()/defaultFatal()` 承载 | 完全映射 | - |
| `ConfigurationError` | `ConfigurationError` | 类名 1:1 | 完全映射 | - |
| `ValidationError` | `ValidationError` | 类名 1:1；恢复/终止语义通过覆写默认方法体现 | 完全映射 | - |
| `ExecutionError` | `ExecutionError` | 类名 1:1 | 完全映射 | - |
| `ApplicationError` | `ApplicationError` | 类名 1:1 | 完全映射 | - |
| `ExternalServiceError` | `ExternalServiceError` | 类名 1:1 | 完全映射 | - |
| `ExternalDataError` | `ExternalDataError` | 类名 1:1 | 完全映射 | - |
| `Termination` | `Termination` | 类名 1:1 | 完全映射 | - |
| `RunnerTermination` | `RunnerTermination` | `RunnerTermination(reason, status, **kwargs) -> RunnerTermination(String reason, StatusCode, Map)`；`reason -> getReason()` | 完全映射 | Java 用构造器重载承载 `params` |
| `WorkflowError` | `WorkflowError` | 类名 1:1 | 完全映射 | - |
| `ComponentError` | `ComponentError` | 类名 1:1 | 完全映射 | - |
| `AgentError` | `AgentError` | 类名 1:1 | 完全映射 | - |
| `RunnerError` | `RunnerError` | 类名 1:1 | 完全映射 | - |
| `GraphError` | `GraphError` | 类名 1:1 | 完全映射 | - |
| `ModelError` | `ModelError` | 类名 1:1 | 完全映射 | - |
| `ToolError` | `ToolError` | `card() -> getCard()`；`card` 合并进 `details` 的行为在 Java `mergeCardDetails(...)` 中实现 | 完全映射 | Java 还会复制 `BaseCard` |
| `ContextError` | `ContextError` | 类名 1:1 | 完全映射 | - |
| `ToolchainError` | `ToolchainError` | 类名 1:1 | 完全映射 | - |
| `SessionError` | `SessionError` | 类名 1:1 | 完全映射 | - |
| `SysOperationError` | `SysOperationError` | 类名 1:1 | 完全映射 | - |
| `GuardrailError` | `GuardrailError` | 类名 1:1 | 完全映射 | - |

## 4. logging

### 4.1 核心入口

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LazyLogger` | `LazyLogger` | Python `__getattr__` 延迟初始化 -> Java 实现 `LoggerProtocol` 并在首次调用时解析真实 logger | 适配映射 | 同样是惰性装配 |
| `LoggerProtocol` | `LoggerProtocol` | `debug/info/warning/error/critical/exception/log/set_level/add_handler/remove_handler/add_filter/remove_filter/get_config/reconfigure/logger -> debug/info/warning/error/critical/exception/log/setLevel/addHandler/removeHandler/addFilter/removeFilter/getConfig/reconfigure/logger` | 完全映射 | Java 额外提供默认 `warn(...)` |
| `LogManager` | `LogManager` | `set_default_logger_class -> setDefaultLoggerFactory()`；`initialize -> initialize()`；`register_logger -> registerLogger()`；`get_logger -> getLogger()`；`get_all_loggers -> getAllLoggers()`；`reset -> reset()` | 适配映射 | Python 传类；Java 传工厂接口 |
| `set_session_id` / `get_session_id` | `LoggingUtils.setSessionId()` / `getSessionId()` | 同名语义映射 | 完全映射 | Java 额外提供 `clearSessionId()` |
| `get_log_max_bytes` / `normalize_and_validate_log_path` | `LoggingUtils.getLogMaxBytes()` / `normalizeAndValidateLogPath()` | 同名语义映射 | 完全映射 | - |
| 模块级 logger 实例 | `Loggers` 静态字段 | `logger -> COMMON`；`interface_logger -> INTERFACE`；`performance_logger -> PERFORMANCE`；`prompt_builder_logger -> PROMPT_BUILDER`；`agent_logger -> AGENT`；`multi_agent_logger -> MULTI_AGENT`；`workflow_logger -> WORKFLOW`；`session_logger -> SESSION`；`controller_logger -> CONTROLLER`；`runner_logger -> RUNNER`；`sys_operation_logger -> SYS_OPERATION`；`llm_logger -> LLM`；`tool_logger -> TOOL`；`prompt_logger -> PROMPT`；`store_logger -> STORE`；`memory_logger -> MEMORY`；`retrieval_logger -> RETRIEVAL`；`context_engine_logger -> CONTEXT_ENGINE`；`graph_logger -> GRAPH`；`operator_logger -> OPERATOR`；`mcp_logger -> MCP` | 适配映射 | Python 用模块变量，Java 用静态常量 |

### 4.2 defaults 子包

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `default.constant.DEFAULT_LOG_CONFIG` / `DEFAULT_INNER_LOG_CONFIG` | `DefaultLogConstants.defaultLogConfig()` / `defaultInnerLogConfig()` | 模块常量 -> 静态工厂方法 | 适配映射 | Java 同时暴露默认文件名/级别常量 |
| `ConfigManager` | `ConfigManager` | `reload -> reload()`；`get -> get()`；`config -> getConfig()` | 完全映射 | - |
| `config` / `configure(config_path)` | `LoggingDefaults.config()` / `LoggingDefaults.configure(configPath)` | 全局配置对象与重载入口 -> 静态 facade 方法 | 适配映射 | 第二轮已新增 `LoggingDefaults` 门面 |
| `ConfigDict` | 无同名类型 | `config.get(...) -> LoggingDefaults.config().get(...)` | 部分映射 | 保留了主要访问能力，但没有独立 `dict` 包装类型 |
| `LogConfig` | `LogConfig` | `reload -> reload()`；`get_common_config -> getCommonConfig()`；`get_interface_config -> getInterfaceConfig()`；`get_prompt_builder_config -> getPromptBuilderConfig()`；`get_performance_config -> getPerformanceConfig()`；`get_custom_config -> getCustomConfig()`；`get_all_configs -> getAllConfigs()` | 完全映射 | Java `getCustomConfig` 无 `**kwargs` 扩展参数 |
| `log_config` / `configure_log(config_path)` | `LoggingDefaults.logConfig()` / `LoggingDefaults.configureLog(configPath)` | 全局 logConfig 对象与热更新入口 -> 静态 facade 方法 | 适配映射 | 第二轮已新增 `LoggingDefaults` 门面 |
| `DefaultLogger` | `DefaultLogger` | `debug/info/warning/error/critical/exception/log/set_level/add_handler/remove_handler/add_filter/remove_filter/get_config/reconfigure/logger -> debug/info/warning/error/critical/exception/log/setLevel/addHandler/removeHandler/addFilter/removeFilter/getConfig/reconfigure/logger` | 完全映射 | Java 额外公开 `logEvent(...)` 用于结构化事件直写 |
| `SafeRotatingFileHandler` | 无同名公开类 | 无直接映射 | 缺失 | Java 依赖 SLF4J/Logback，不再暴露轮转 handler 类型 |
| `ContextFilter` | 无同名公开类 | 无直接映射 | 缺失 | MDC 注入逻辑内嵌在 Java `DefaultLogger` 中 |

### 4.3 events 子包

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LogEventType` | `LogEventType` | `.value -> getValue()` | 完全映射 | Java 额外提供 `fromValue(String)` |
| `LogLevel` | `LogLevel` | `.value -> getValue()` | 完全映射 | - |
| `ModuleType` | `ModuleType` | `.value -> getValue()` | 完全映射 | - |
| `EventStatus` | `EventStatus` | `.value -> getValue()` | 完全映射 | - |
| `BaseLogEvent` | `BaseLogEvent` | `to_dict -> toMap()` | 完全映射 | Python dataclass 字段 -> Java Lombok 字段/getter/builder |
| `AgentEvent` | `AgentEvent` | 字段 1:1；通过 builder/getter 与 `toMap()` 暴露 | 完全映射 | - |
| `WorkflowEvent` | `WorkflowEvent` | 字段 1:1；`component_id` 驱动组件级事件语义 | 完全映射 | - |
| `LLMEvent` | `LLMEvent` | 字段 1:1 | 完全映射 | - |
| `ToolEvent` | `ToolEvent` | 字段 1:1 | 完全映射 | - |
| `StoreEvent` | `StoreEvent` | 字段 1:1 | 完全映射 | - |
| `MemoryEvent` | `MemoryEvent` | 字段 1:1 | 完全映射 | - |
| `SessionEvent` | `SessionEvent` | 字段 1:1 | 完全映射 | - |
| `ContextEvent` | `ContextEvent` | 字段 1:1 | 完全映射 | - |
| `RetrievalEvent` | `RetrievalEvent` | 字段 1:1 | 完全映射 | - |
| `PerformanceEvent` | `PerformanceEvent` | 字段 1:1 | 完全映射 | - |
| `UserInteractionEvent` | `UserInteractionEvent` | 字段 1:1 | 完全映射 | - |
| `SystemEvent` | `SystemEvent` | 字段 1:1 | 完全映射 | - |
| `SysOperationEvent` | `SysOperationEvent` | 字段 1:1 | 完全映射 | - |
| `StreamEvent` | `StreamEvent` | 字段 1:1；`to_dict -> toMap()` | 完全映射 | - |
| `WorkflowStreamEvent` | `WorkflowStreamEvent` | 字段 `workflow_id/workflow_name/component_id/component_name/component_type_str` -> 对应 getter/builder/`toMap()` 字段 | 完全映射 | 第二轮已补齐 |
| `GraphEvent` | `GraphEvent` | 字段 1:1 | 完全映射 | - |
| `RunnerEvent` | `RunnerEvent` | 字段 1:1 | 完全映射 | Python 虽未标注 dataclass，但公开字段语义已对齐 |
| `register_event_class` / `unregister_event_class` | `EventClassRegistry.register()` / `unregister()` | 同名语义映射 | 完全映射 | - |
| `get_event_class` | `EventClassRegistry.getFactory()` | `事件类 -> 事件工厂 Supplier` | 适配映射 | Java 返回工厂，不直接返回 `Class` |
| `create_log_event(event_type, **kwargs)` | `EventClassRegistry.createEvent(LogEventType, Map)` / `createEvent(String)` | Python 一步创建并注入字段 -> Java 新增按属性注入的创建入口 | 部分映射 | 第二轮已补齐大部分能力；但 `String + properties`、自定义字符串事件落字段、`snake_case` 属性名仍未完全对齐 |
| `validate_event` | `EventClassRegistry.validateEvent()` | 同名语义映射 | 完全映射 | 第二轮已补齐 |
| `sanitize_event_for_logging` | `EventSanitizer.sanitizeEventForLogging()` | 同名语义映射 | 完全映射 | Java 还公开 `REDACTED` 和默认敏感字段常量 |

## 5. schema

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `BaseCard` | `BaseCard` | `tool_info -> toolInfo()`；`str -> toString()`；字段 `id/name/description` -> getter/setter/builder | 完全映射 | Java 额外提供 `copy()` |
| `ParamType` | `ParamType` | 枚举值 `String/Boolean/Integer/Number/Array/Object -> STRING/BOOLEAN/INTEGER/NUMBER/ARRAY/OBJECT`；`.value -> getValue()` | 适配映射 | Java 额外提供 `fromValue(String)` |
| `Param` | `Param` | 字段 `name/description/type/required/default/items/properties -> getName()/getDescription()/getType()/isRequired()/getDefaultValue()/getItems()/getProperties()`；`string -> string()`；`boolean -> bool()`；`integer -> integer()`；`number -> number()`；`array -> array()`；`object -> object()` | 适配映射 | `validate_type_specific_fields()` 的职责在 Java 私有构造器 `validate()` 中完成；`boolean` 因 Java 关键字改为 `bool` |
| 无 Python 对位 | `Part` | Java-only 类型 | Java 扩展 | `common.schema` 在 Java 中新增 |

## 6. security

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ExceptionUtils` | `ExceptionUtils` | `format_validation_error -> formatValidationError()` | 完全映射 | Java 额外提供 `getRootCause()` |
| `JsonUtils` | `JsonUtils` | `safe_json_loads -> safeJsonLoads()`；`safe_json_dumps -> safeJsonDumps()` | 完全映射 | Java 额外提供重载和 `getMapper()` |
| `PathChecker` | `PathChecker` | `__new__/__init__` 单例 -> `getInstance()`；`is_sensitive_path(self, path) -> checkSensitive()/isSensitivePath()`；模块函数 `is_sensitive_path(path) -> PathChecker.isSensitivePath(path)` | 适配映射 | Java 将实例方法与静态便捷方法都保留 |
| `SslUtils` | `SslUtils` | `get_ssl_config -> getSslConfig()`；`create_strict_ssl_context -> createStrictSslContext()` | 部分映射 | Python `create_ssl_adapter()` 无直接 Java 对位；Java 额外提供 `createInsecureSslContext()` |
| `UrlUtils` | `UrlUtils` | `check_url_is_valid -> checkUrlIsValid()`；`get_global_proxy_url -> getGlobalProxyUrl()`；`get_global_proxies -> getGlobalProxies()`；`should_bypass_proxy -> shouldBypassProxy()` | 完全映射 | - |
| `UserConfig` | `UserConfig` | `set_config_path -> setConfigPath()`；`get_config -> getConfig()`；`is_sensitive -> isSensitive()`；`get_sensitive_paths -> getSensitivePaths()`；`set_is_sensitive -> setSensitive()`；`get_sensitive_paths_list -> getSensitivePathsList()` | 适配映射 | 第二轮已补齐运行期开关与公开路径列表；`setConfigPath()` 的路径校验语义仍与 Python 略有差异 |

## 7. utils

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `dict_utils.create_nested_dict` | `DictUtils.createNestedMap` | 同名语义映射 | 完全映射 | Java 提供重载 |
| `dict_utils.flatten_dict` | `DictUtils.flattenMap` | 同名语义映射 | 完全映射 | - |
| `dict_utils.extract_leaf_nodes` | `DictUtils.extractLeafNodes` | 同名语义映射 | 完全映射 | - |
| `dict_utils.format_path` | `DictUtils.formatPath` | 同名语义映射 | 完全映射 | - |
| `dict_utils.rebuild_dict_from_paths` | `DictUtils.rebuildMapFromPaths` | 同名语义映射 | 完全映射 | - |
| `dict_utils.rebuild_dict` | `DictUtils.rebuildDict` | 同名语义映射 | 完全映射 | - |
| `hash_util.generate_key` | `HashUtil.generateKey` | 同名语义映射 | 完全映射 | Java 额外提供重载 |
| `ip_utils.get_local_ip` | `IpUtils.getLocalIp` | 同名语义映射 | 完全映射 | - |
| `MessageUtils` | `MessageUtils` | `should_add_user_message -> shouldAddUserMessage()`；`add_user_message -> addUserMessage()`；`add_ai_message -> addAiMessage()`；`add_tool_message -> addToolMessage()`；`add_workflow_message -> addWorkflowMessage()`；`get_chat_history -> getChatHistory()` | 适配映射 | Python 为 `async staticmethod`，Java 为同步静态方法 |
| `SchemaUtils` | `SchemaUtils` | `format_with_schema -> formatWithSchema()`；`remove_none_values -> removeNoneValues()`；`validate_with_schema -> validateWithSchema()`；`get_schema_dict -> getSchemaDict()` | 部分映射 | 第二轮已补齐 `removeNoneValues()` 和 `skipNoneValue` 参数；`get_schema_class()` 及 Python 的泛型 schema/model 双向能力仍未完全保留 |
| `Singleton` 元类 | `SingletonSupport` | `__call__` 单例构造 -> `SingletonSupport.getInstance(clazz, factory)` | 适配映射 | Python 元类语义在 Java 中改为通用单例基类/工具 |

## 8. 结论

- Java `common` 模块已覆盖 Python `common` 模块的大部分核心类型与主流程 API。
- 第二轮补齐后，`BaseError.toJson()`、`WorkflowStreamEvent`、`validateEvent()`、`LoggingDefaults`、`UserConfig.setSensitive()/getSensitivePathsList()`、`SchemaUtils.removeNoneValues()` 已完成对位。
- 当前最需要继续追齐的是 `logging.events` 的自定义字符串事件完整建模、`security.SslUtils` 与 `UserConfig.setConfigPath()` 的细粒度语义、`utils.SchemaUtils.getSchemaClass()` 与更泛化的 schema/model 转换能力。
