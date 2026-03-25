# Runner 模块 Python / Java API 映射（复核版）

## 对照范围

- Python: `agent-core-python/openjiuwen/core/runner/**`
- Java: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/runner/**`
- 复核时间: `2026-03-16`
- 复核方式:
  - 先检查既有 `runner.md` / `runner_fixed.md`
  - 再逐层核对 `Runner`、`callback`、`mq`、`drunner`、`resourcemanager` 源码
  - 对旧文档中的争议项逐一做源码确认，不沿用旧结论

## 总体结论

- Java 版 `runner` 主链路已经与 Python 版基本对齐，且旧文档里标记为缺失的多项 API 实际已经补齐。
- 已确认在 Java 中存在且不应再计入缺漏的能力包括：
  - `Runner.distPubsub()` / `Runner.systemReplySub()`
  - `runWorkflow*` / `runAgent*` / `runAgentGroup*` 的 `envs` 重载
  - `CallbackFramework.registerSync()`、`triggerDelayed()`、`triggerGenerator()`、`saveState()`
  - `CallbackFramework.on()`、`triggerOnCall()`、`emits()`、`emitsStream()`、`emitAround()`、`transformIo()`、`transformIoByEvents()`
  - `ReplyTopicSubscription.isActive()` 与 `registerCollector()` 的并发上限校验
  - `ResponseCollector.isCancelled()` / `isExpired()` / `isActive()` / `checkMessage()`
  - `CancelReason` / `CancelEvent`
  - `MessageTask`
  - `MessageSerializer` 的递归深度控制、时间类型处理、可注册类型表接口
  - `TagMgr.display()`、`ThreadSafeDict.items()` / `setdefault()` / `pop()` / `update()`
- 当前真实差异已经收敛到少量行为级或表面 API 级别问题，不再是主链路缺口。

## 包级映射

| Python 包/模块 | Java 包/模块 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.runner.runner` | `com.openjiuwen.core.runner.Runner` / `RunnerImpl` | 基本对齐 | Java 使用静态 facade + 实例实现，Python 使用类属性代理 `_RunnerImpl` |
| `openjiuwen.core.runner.runner_config` | `com.openjiuwen.core.runner.*Config` | 完整映射 | `RunnerConfig`、`DistributedConfig`、`MessageQueueConfig`、`PulsarConfig`、`MessageQueueType` 均已落地 |
| `openjiuwen.core.runner.callback` | `com.openjiuwen.core.runner.callback` | 基本对齐 | 主框架、链路、过滤器、DSL 包装方法均存在 |
| `openjiuwen.core.runner.message_queue_*` | `com.openjiuwen.core.runner.mq` | 完整映射 | 本地消息队列抽象和内存实现已对齐 |
| `openjiuwen.core.runner.drunner` | `com.openjiuwen.core.runner.drunner` | 基本对齐 | 分布式消息、远端调用、服务端适配均已落地 |
| `openjiuwen.core.runner.resources_manager` | `com.openjiuwen.core.runner.resourcemanager` | 基本对齐 | 资源管理器、Tag、Tool、MCP、线程安全字典都已提供 |
| `openjiuwen.core.runner.resources_manager.base` | `com.openjiuwen.core.runner.base` | 基本对齐 | Python 类型别名 / dataclass 在 Java 中收敛为接口、常量类、枚举和 `Result<T>` |

## 1. 顶层 Runner 与配置

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `_RunnerImpl` + `Runner` | `RunnerImpl` + `Runner` | `resource_mgr -> getResourceMgr()/resourceMgr()`；`pubsub -> getPubsub()/pubsub()`；`dist_pubsub -> getDistPubsub()/distPubsub()`；`system_reply_sub -> getSystemReplySub()/systemReplySub()`；`callback_framework -> getCallbackFramework()/callbackFramework()`；`set_config/get_config/start/stop/run_* / release -> 同名 camelCase` | 完整映射 | Java `Runner` 为静态代理，`RunnerImpl` 为公开实例实现 |
| `run_workflow` / `run_workflow_streaming` | `runWorkflow` / `runWorkflowStreaming` | `workflow/inputs/session/context/envs` 参数对应 | 完整映射 | Java 已提供带 `envs` 与不带 `envs` 的双重入口 |
| `run_agent` / `run_agent_streaming` | `runAgent` / `runAgentStreaming` | `agent/inputs/session/context/envs` 参数对应 | 基本对齐 | Java 用反射桥接 agent 实例调用；Python 针对 `RemoteAgent` / `LegacyBaseAgent` 有显式分支 |
| `run_agent_group` / `run_agent_group_streaming` | `runAgentGroup` / `runAgentGroupStreaming` | 参数组对应 | 基本对齐 | Java 保留同名主入口 |
| `set_runner_config()` / `get_runner_config()` | `RunnerConfig.setRunnerConfig()` / `RunnerConfig.getRunnerConfig()` | 模块函数 -> 静态方法 | 完整映射 | Java 额外公开 `RunnerConfig.DEFAULT` |
| `RunnerConfig` | `RunnerConfig` | `agent_topic_template/reply_topic_template -> agentTopicTemplate()/replyTopicTemplate()` | 完整映射 | Python `checkpointer_config` 在 Java 中为 `Map<String, Object>` |
| `DistributedConfig` | `DistributedConfig` | `get_agent_topic_template/get_reply_topic_template -> getAgentTopicTemplate/getReplyTopicTemplate` | 完整映射 | 并发上限、topic 模板、message queue 配置字段均存在 |
| `MessageQueueConfig` | `MessageQueueConfig` | 字段同构 | 完整映射 | - |
| `PulsarConfig` | `PulsarConfig` | 字段同构 | 完整映射 | - |
| `MessageQueueType` | `MessageQueueType` | `PULSAR` / `FAKE` | 完整映射 | Java 额外提供 `getValue()` |

## 2. Callback 框架

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AsyncCallbackFramework` | `CallbackFramework` | `callbacks/chains/circuit_breakers/callback_filters -> getCallbacks/getChains/getCircuitBreakers/getCallbackFilters` | 完整映射 | 属性访问改为 getter |
| `register()` / `register_sync()` | `register()` / `registerSync()` | `event/callback/priority/once/namespace/tags/filters/rollback_handler/error_handler/max_retries/retry_delay/timeout` -> 同名 camelCase 参数 | 完整映射 | Python `register()` 是 async，Java 为同步实现 |
| `unregister()` / `unregister_namespace()` / `unregister_by_tags()` / `unregister_event()` | `unregister()` / `unregisterNamespace()` / `unregisterByTags()` / `unregisterEvent()` | 同名映射 | 完整映射 | - |
| `trigger()` / `trigger_chain()` / `trigger_parallel()` / `trigger_until()` / `trigger_with_timeout()` / `trigger_stream()` / `trigger_delayed()` / `trigger_generator()` | `trigger()` / `triggerChain()` / `triggerParallel()` / `triggerUntil()` / `triggerWithTimeout()` / `triggerStream()` / `triggerDelayed()` / `triggerGenerator()` | 同名映射 | 完整映射 | Java 返回同步集合/迭代器；Python 主体为 async |
| `add_filter()` / `add_global_filter()` / `add_circuit_breaker()` / `add_hook()` | `addFilter()` / `addGlobalFilter()` / `addCircuitBreaker()` / `addHook()` | 同名映射 | 完整映射 | - |
| `get_metrics()` / `reset_metrics()` / `get_slow_callbacks()` / `enable_event_history()` / `get_event_history()` / `replay_events()` / `save_state()` / `list_events()` / `list_callbacks()` / `get_statistics()` | `getMetrics()` / `resetMetrics()` / `getSlowCallbacks()` / `enableEventHistory()` / `getEventHistory()` / `replayEvents()` / `saveState()` / `listEvents()` / `listCallbacks()` / `getStatistics()` | 同名映射 | 完整映射 | - |
| `on()` | `on()` | Python 装饰器注册 -> Java DSL 风格回调注册 | 基本对齐 | Java 返回 `CallbackInfo`，不是语言级 decorator |
| `trigger_on_call()` | `triggerOnCall()` | 装饰器 -> 包装函数 | 基本对齐 | Java 以 `Function<Map<String,Object>, Object>` 包装 |
| `emits()` | `emits()` | 装饰器 -> 包装函数 | 基本对齐 | 语义一致 |
| `emits_stream()` | `emitsStream()` | async generator 装饰器 -> `Iterator/Iterable` 包装 | 基本对齐 | Java 面向同步迭代器 |
| `emit_around()` | `emitAround()` | 前后置事件包装 | 基本对齐 | 语义一致 |
| `transform_io()` | `transformIo()` / `transformIoByEvents()` | 直接 callable 模式 / event 模式均有对应 | 基本对齐 | Java 将两种模式拆成两个公开方法 |
| `create_on_decorator()` / `create_trigger_on_call_decorator()` / `create_emits_decorator()` / `create_emits_stream_decorator()` / `create_emit_around_decorator()` / `create_transform_io_decorator()` / `create_transform_io_by_events_decorator()` | `CallbackFramework.on()` / `triggerOnCall()` / `emits()` / `emitsStream()` / `emitAround()` / `transformIo()` / `transformIoByEvents()` | Python 模块函数 -> Java 成员方法 | 部分映射 | 功能存在，但 Java 没有单独的 `decorator.py` 工具模块 |
| `CallbackChain` | `CallbackChain` | `add/remove/execute -> add/remove/execute` | 完整映射 | Java 额外公开 `ExceptionContext` |
| `CallbackInfo` | `CallbackInfo` | 数据模型对位 | 完整映射 | Java 额外公开 `callbackName` / `getCallbackDisplayName()` |
| `CallbackMetrics` | `CallbackMetrics` | `update/to_dict/avg_time -> update/toMap/getAvgTime` | 完整映射 | 命名差异 |
| `ChainContext` | `ChainContext` | `get_last_result/get_all_results/set_metadata/get_metadata/elapsed_time -> getLastResult/getAllResults/setMetadata/getMetadata/getElapsedTime` | 完整映射 | - |
| `ChainResult` | `ChainResult` | 数据模型对位 | 完整映射 | - |
| `FilterResult` | `FilterResult` | Python 数据类 -> Java builder + 静态工厂 `continueResult/skipResult/stopResult/modifyResult` | 基本对齐 | API 形态差异，语义一致 |
| `FilterAction` / `ChainAction` / `HookType` | 同名枚举 | 枚举值对齐 | 完整映射 | Java 额外提供 `getValue()` |
| `EventFilter` / `RateLimitFilter` / `CircuitBreakerFilter` / `ValidationFilter` / `LoggingFilter` / `AuthFilter` / `ParamModifyFilter` / `ConditionalFilter` | 同名类 | `filter()` 主方法同构 | 完整映射 | Java `ConditionalFilter` 额外暴露 `ConditionPredicate` |

## 3. 本地消息队列

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `QueueMessage` | `QueueMessage` | 字段 -> `get*/set*` | 完整映射 | - |
| `InvokeQueueMessage` | `InvokeQueueMessage` | 数据模型对位 | 完整映射 | - |
| `StreamQueueMessage` | `StreamQueueMessage` | 数据模型对位 | 完整映射 | - |
| `LocalMessageQueue` | `LocalMessageQueue` | `start/stop -> start/stop` | 完整映射 | - |
| `SubscriptionBase` | `SubscriptionBase` | `set_message_handler/activate/deactivate/is_active -> setMessageHandler/activate/deactivate/isActive` | 完整映射 | - |
| `MessageQueueBase` | `MessageQueueBase` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 完整映射 | - |
| `SubscriptionInMemory` | `SubscriptionInMemory` | `set_message_handler/activate/deactivate/is_active/push_message -> setMessageHandler/activate/deactivate/isActive/pushMessage` | 完整映射 | - |
| `MessageQueueInMemory` | `MessageQueueInMemory` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 完整映射 | - |

## 4. 分布式 runner（drunner）

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `DMessageType` / `ResultType` | 同名枚举 | 枚举值对齐 | 完整映射 | - |
| `DmqMessage` / `DmqRequestMessage` / `DmqResponseMessage` | 同名类 | 数据字段 -> `get*/set*` | 完整映射 | Java 同时保留 `body` / `payload` 兼容访问方式 |
| `MessageQueueFactory` | `MessageQueueFactory` | `create -> create` | 完整映射 | - |
| `FakeMQ` | `FakeMessageQueue` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 基本对齐 | Java 用 `MessageQueueInMemory` 委托实现 |
| `FakeSubscription` | 无单独公开类型 | `set_message_handler/activate/deactivate/push` | Python 独有 | Java 直接复用 `SubscriptionInMemory`，未单独建 `FakeSubscription` |
| `serialize_message()` / `deserialize_message()` | `MessageSerializer.serializeMessage()` / `deserializeMessage()` | 模块函数 -> 静态方法 | 基本对齐 | Java 已支持递归深度、`datetime`、可注册类型表；但默认内置类型注册仍弱于 Python |
| `TYPE_REGISTRY` | `registerType()` / `unregisterType()` / `getTypeRegistry()` | 全局字典 -> 注册接口 | 部分映射 | Java 提供机制，但默认未预置 Python 侧那批类型注册 |
| `ReplyTopicSubscription` | `ReplyTopicSubscription` | `activate/deactivate/register_collector/unregister_collector/is_active -> activate/deactivate/registerCollector/unregisterCollector/isActive` | 基本对齐 | Java 额外公开 `getTopic()`；Python 支持 topic 缺省自动推导 |
| `CollectorKey` | `ReplyTopicSubscription.CollectorKey` | 顶层 dataclass -> 公共嵌套 `record` | 基本对齐 | 能力在，公开位置变化 |
| `ResponseCollector` | `ResponseCollector` | `is_cancelled/is_expired/is_active/put_message/result/stream/check_message/close -> isCancelled/isExpired/isActive/putMessage/result/stream/checkMessage/close` | 基本对齐 | Java 额外提供 `close(CancelReason)`；远端错误异常类型与 Python 不同 |
| `CancelReason` / `CancelEvent` | `CancelReason` / `CancelEvent` | 枚举和值对象对齐 | 完整映射 | - |
| `RemoteClient` | `RemoteClient` | `start/stop/invoke/stream` | 完整映射 | Python 为抽象基类，Java 为接口 |
| `ProtocolEnum` / `RemoteClientConfig` | 同名枚举 / 类 | 字段与枚举对齐 | 完整映射 | - |
| `MqRemoteClient` | `MqRemoteClient` | `start/stop/invoke/stream` | 完整映射 | Java 保留提前取消时发送 `STOP` 的逻辑 |
| `RemoteAgent` | `RemoteAgent` | `invoke/stream -> invoke/stream` | 完整映射 | - |
| `AgentAdapter` | `AgentAdapter` | `start/stop -> start/stop` | 完整映射 | 内部处理函数在 Java 中收敛为私有方法 |
| `MessageTask` | `MessageTask` | Python dataclass -> Java POJO | 完整映射 | 两边都用于保存 request + in-flight task |
| `build_stream_response()` / `build_final_response()` / `build_batch_response()` / `build_error_response()` | `MqMessageUtils.*` | 模块函数 -> 静态方法 | 基本对齐 | Java `buildErrorResponse()` 已透传 `BaseError.getCode()` |
| `MqServerAdapter` | `MqServerAdapter` | `start/stop` 主链路对位 | 完整映射 | Java 同样保留任务取消和错误回写逻辑 |

## 5. 资源管理与基础类型

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentProvider` / `AgentGroupProvider` / `WorkflowProvider` / `ModelProvider` | 同名接口 | Python 类型别名 -> Java 函数式接口 | 完整映射 | - |
| `Tag` + `ALL/GLOBAL/ACTIVE/INACTIVE` | `Tag` | 常量对齐 | 完整映射 | Python `Tag = str`；Java 改为常量类 |
| `TagMatchStrategy` / `TagUpdateStrategy` | 同名枚举 | 枚举值对齐 | 完整映射 | Java 额外提供 `getValue()` |
| `Ok` / `Error` / `Result` | `Ok<T>` / `Error<T>` / `Result<T>` | `is_ok/is_err/msg/error -> isOk/isError/getValue/getError` | 基本对齐 | Java 使用 sealed interface |
| `AbstractManager` | `AbstractManager<T>` | 抽象基类对位 | 完整映射 | - |
| `AgentMgr` / `AgentGroupMgr` / `ModelMgr` / `PromptMgr` / `WorkflowMgr` / `SysOperationMgr` | 同名类 | `add_* / get_* / remove_* -> add*/get*/remove*` | 完整映射 | Java 额外公开批量 entry record |
| `ResourceRegistry` | `ResourceRegistry` | `remove_by_id` + `tool/prompt/model/workflow/agent/agent_group/sys_operation` -> `removeById` + `tool/prompt/model/workflow/agent/agentGroup/sysOperation` | 完整映射 | - |
| `TagMgr` | `TagMgr` | `has_tag/list_tags/has_resource/tag_resource/remove_resource/remove_resource_tags/update_resource_tags/remove_tag/get_tag_resources/find_resources_by_tags/has_resource_tag/get_resources_tags/display -> 同名 camelCase` | 完整映射 | `display()` 与 `display(boolean)` 均已在 Java 实现 |
| `ToolMgr` | `ToolMgr` | `add_tool/get_tool/get_mcp_tool/get_mcp_tools/get_mcp_tool_id/remove_tool/generate_mcp_tool_id/add_tool_server/get_mcp_server_ids/remove_tool_server/add_sys_operation_tools/remove_sys_operation_tools/get_sys_operation_tool_ids/refresh_tool_server/release -> 同名 camelCase` | 完整映射 | `McpServerResource` / `SysOpToolResource` 在 Java 中为嵌套 `record` |
| `McpServerResource` / `SysOpToolResource` | `ToolMgr.McpServerResource` / `ToolMgr.SysOpToolResource` | 顶层类 -> 嵌套 `record` | 基本对齐 | 公开位置变化 |
| `ResourceMgr` | `ResourceMgr` | `add/remove/get`、`get_sys_op_tool_cards()`、`get_tool_infos()`、`add/remove/refresh MCP`、`get_mcp_tool_infos()`、tag 相关 API、`release()` -> 同名 camelCase | 完整映射 | Java 额外提供 `addAgents/addWorkflows/addModels/addPrompts` 等批量接口 |
| `ThreadSafeDict` | `ThreadSafeDict` | `get/get_or_set/get_or_create/clear/keys/values/items/setdefault/pop/update -> get/getOrSet/getOrCreate/clear/keys/values/items/setdefault/pop/update` | 基本对齐 | Java 还额外公开 `put/getOrDefault/containsKey/size/snapshot/putAll` |

## 6. Java 侧额外公开的桥接 API

| Java API | Python 对位 | 说明 |
| --- | --- | --- |
| `RunnerImpl` | Python `_RunnerImpl` | Python 实现类是内部约定，Java 直接作为公开类暴露 |
| `DistributedRunner` | Python 无单独公开类型 | Java 将分布式运行态 holder 单独建类 |
| `MessageSerializer.registerType()` / `unregisterType()` / `getTypeRegistry()` | Python 直接访问 `TYPE_REGISTRY` | Java 把类型表访问封装为显式 API |
| `ResourceMgr.AgentEntry` / `WorkflowEntry` / `ModelEntry` / `PromptEntry` | Python 批量 tuple / list 参数 | Java 用 `record` 明确批量输入结构 |
| `PromptMgr.PromptEntry` / `WorkflowMgr.WorkflowEntry` | Python 无同名公开类型 | Java 为批量注册做显式建模 |
| `ThreadSafeDict.snapshot()` / `getOrDefault()` / `put()` / `containsKey()` / `size()` | Python `MutableMapping` 魔术方法/字典内建 | Java 对应为显式方法 |

## 7. 当前仍未完全 1:1 对齐的点

| 类别 | 差异项 | Python 现状 | Java 现状 | 判断 |
| --- | --- | --- | --- | --- |
| MessageSerializer 默认注册 | `TYPE_REGISTRY` 预置类型 | Python 默认已注册 `OutputSchema`、`CustomSchema`、`TraceSchema`、`InteractionOutput`、`WorkflowOutput`、`DmqRequestMessage`、`DmqResponseMessage` | Java 仅提供 `registerType()` 机制，默认未发现同等预注册 | 真实差异 |
| MessageSerializer 自动类标记 | `model_dump()` + `__class__` | Python 会为 Pydantic / BaseModel 负载自动写入 `__class__` | Java `serializePayload()` 不会为一般 POJO 自动补 `__class__` 标记 | 真实差异 |
| ReplyTopicSubscription 构造便利性 | 缺省 topic | Python 可省略 topic，自动从 `RunnerConfig.reply_topic_template()` 推导 | Java 当前构造器要求显式传入 topic | 低优先级差异 |
| Fake MQ 公开类型 | `FakeSubscription` | Python 公开独立 helper 类型 | Java 仅公开 `FakeMessageQueue`，订阅直接复用 `SubscriptionInMemory` | 低优先级 API 差异 |
| ResponseCollector 远端错误异常形态 | `check_message()` 异常对象 | Python 会构造带 `error_code/error_msg` 的结构化错误 | Java 当前抛 `IllegalStateException("Remote error code: msg")` | 行为差异 |
| decorator 工具模块 | `callback/decorator.py` | Python 公开 `create_*_decorator()` 系列模块函数 | Java 功能收敛到 `CallbackFramework` 成员方法，没有独立 helper 模块 | 表面 API 差异 |

## 8. 不应再计入缺漏的项

- `Runner.dist_pubsub` / `Runner.system_reply_sub` 已由 `Runner.distPubsub()` / `Runner.systemReplySub()` 公开。
- `runWorkflow*`、`runAgent*`、`runAgentGroup*` 的 `envs` 参数重载已存在。
- `CallbackFramework.registerSync()`、`triggerDelayed()`、`triggerGenerator()`、`saveState()` 已实现。
- `CallbackFramework.on()`、`triggerOnCall()`、`emits()`、`emitsStream()`、`emitAround()`、`transformIo()`、`transformIoByEvents()` 已实现。
- `ReplyTopicSubscription.isActive()` 与 collector 并发上限保护已实现。
- `CancelReason`、`CancelEvent`、`ResponseCollector.isCancelled()/isExpired()/isActive()/checkMessage()` 已实现。
- `MessageTask` 在 Python 与 Java 两边都已存在。
- `MqRemoteClient` 已保留提前取消时发送 `STOP` 的逻辑。
- `MqServerAdapter` 已保留 adapter stop 时取消任务并回写错误响应的逻辑。
- `TagMgr.display()` 与 `ThreadSafeDict.items()/setdefault()/pop()/update()` 已在 Java 中实现。
