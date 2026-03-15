# Runner 模块 Python / Java API 映射

## 对照范围

- Python: `agent-core-python/openjiuwen/core/runner/**`
- Java: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/runner/**`
- 复核时间: `2026-03-14`
- 统计口径:
  - Python 统计公开类、公开方法、公开模块函数
  - Java 统计公开 `class/interface/enum/record` 及其公开方法
- 自动抽取统计:
  - Python: `36` 个模块、`71` 个公开类、`249` 个公开方法、`15` 个模块函数
  - Java: `77` 个源码文件、`88` 个公开类型（含嵌套 `record/interface`）、约 `324` 个公开方法/访问器

## 总体结论

- Java 版 `runner` 主链路已经基本覆盖 Python 版：顶层 `Runner`、本地消息队列、分布式 `drunner`、资源管理器、回调框架都已落地。
- 旧版缺漏清单里曾标记的一批 API，源码中已经补齐，包括 `Runner.distPubsub()/systemReplySub()`、`run*` 的 `envs` 重载、`ReplyTopicSubscription.isActive()`、`ResponseCollector` 取消态 API、`CancelReason/CancelEvent`、`MessageTask`、`ResourceMgr.refreshMcpServer()/getMcpToolInfos()`、`TagMgr.display()`、`ThreadSafeDict`、`MqRemoteClient` 的 `STOP` 语义、`MqServerAdapter` 的取消回写。
- 当前仍然真实存在的差异，主要集中在四类：
  - Callback decorator DSL 还没有 Java 对应入口
  - `ReplyTopicSubscription.registerCollector()` 缺少 Python 版的并发上限保护
  - `MessageSerializer` 没有完全保留 Python 版的递归类型注册、`datetime` 和深度限制语义
  - `MqMessageUtils.buildErrorResponse()` 仍未透传 Python 版错误码

## 包级映射

| Python 包/模块 | Java 包/模块 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.runner.runner` | `com.openjiuwen.core.runner.Runner` / `RunnerImpl` | 基本对齐 | Java 同时保留静态 facade 和实例实现 |
| `openjiuwen.core.runner.runner_config` | `com.openjiuwen.core.runner.*Config` | 完整映射 | 全局配置、分布式配置、MQ 配置、枚举均已落地 |
| `openjiuwen.core.runner.callback` | `com.openjiuwen.core.runner.callback` | 部分映射 | 主框架齐全，decorator DSL 仍缺 |
| `openjiuwen.core.runner.message_queue_*` | `com.openjiuwen.core.runner.mq` | 完整映射 | 本地 MQ 抽象与内存实现均已提供 |
| `openjiuwen.core.runner.drunner` | `com.openjiuwen.core.runner.drunner` | 部分映射 | 主链路齐全，仍有少量行为级差异 |
| `openjiuwen.core.runner.resources_manager` | `com.openjiuwen.core.runner.resourcemanager` | 基本对齐 | 主管理器、Tag、MCP、Tool、ThreadSafeDict 都已存在 |
| `openjiuwen.core.runner.resources_manager.base` | `com.openjiuwen.core.runner.base` | 基本对齐 | Python 类型别名落成 Java 接口、常量类与 `Result<T>` |

## 1. 顶层 Runner 与配置

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Runner` | `Runner` + `RunnerImpl` | `resource_mgr -> resourceMgr()/getResourceMgr()`；`pubsub -> pubsub()/getPubsub()`；`dist_pubsub -> distPubsub()/getDistPubsub()`；`system_reply_sub -> systemReplySub()/getSystemReplySub()`；`callback_framework -> callbackFramework()/getCallbackFramework()`；`set_config/get_config/start/stop/run_* / release -> 同名 camelCase` | 完整映射 | Java `Runner` 为静态 facade，`RunnerImpl` 为实例桥接；`envs` 重载已补齐 |
| `set_runner_config()` / `get_runner_config()` | `RunnerConfig.setRunnerConfig()` / `RunnerConfig.getRunnerConfig()` | 模块函数 -> 静态方法 | 完整映射 | Java 还额外公开 `RunnerConfig.DEFAULT` |
| `RunnerConfig` | `RunnerConfig` | `agent_topic_template/reply_topic_template -> agentTopicTemplate()/replyTopicTemplate()` | 完整映射 | `checkpointer_config` 在 Java 中用 `Map<String, Object>` 承载 |
| `DistributedConfig` | `DistributedConfig` | `get_agent_topic_template/get_reply_topic_template -> getAgentTopicTemplate/getReplyTopicTemplate` | 完整映射 | 请求超时、并发上限、topic 模板字段一致 |
| `MessageQueueConfig` | `MessageQueueConfig` | 字段对应 | 完整映射 | Python dataclass 对应 Java Lombok 配置类 |
| `PulsarConfig` | `PulsarConfig` | 字段对应 | 完整映射 | `url` / `max_workers` -> `url` / `maxWorkers` |
| `MessageQueueType` | `MessageQueueType` | `PULSAR` / `FAKE` | 完整映射 | Java 额外提供 `getValue()` |

## 2. Callback 框架

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AsyncCallbackFramework` | `CallbackFramework` | 属性 `callbacks/chains/circuit_breakers/callback_filters -> getCallbacks/getChains/getCircuitBreakers/getCallbackFilters`；`register/register_sync -> register/registerSync`；`unregister* -> unregister*`；`trigger/trigger_chain/trigger_parallel/trigger_until/trigger_with_timeout/trigger_stream/trigger_delayed/trigger_generator -> 同名 camelCase`；`add_filter/add_global_filter/add_circuit_breaker/add_hook -> 同名 camelCase`；`get_metrics/reset_metrics/get_slow_callbacks/enable_event_history/get_event_history/replay_events/save_state/list_events/list_callbacks/get_statistics -> 同名 camelCase` | 部分映射 | Java 主能力已齐；仍缺 `on()`、`trigger_on_call()`、`emits()`、`emits_stream()`、`emit_around()`、`transform_io()` |
| `create_on_decorator()` / `create_trigger_on_call_decorator()` / `create_emits_decorator()` / `create_emits_stream_decorator()` / `create_emit_around_decorator()` / `create_transform_io_decorator()` / `create_transform_io_by_events_decorator()` | 无直接对应 | Python decorator 工厂 | Python 独有 | Java 回调框架仍以显式注册为主 |
| `CallbackChain` | `CallbackChain` | `add/remove/execute -> add/remove/execute` | 完整映射 | Java 额外公开 `getName()`、`getCallbacks()`、`ExceptionContext` |
| `CallbackInfo` | `CallbackInfo` | 数据模型对位 | 基本对齐 | Java 额外公开 `getCallbackDisplayName()` |
| `CallbackMetrics` | `CallbackMetrics` | `update -> update`；`avg_time/to_dict -> getAvgTime/toMap` | 完整映射 | 命名从 snake_case 调整为 camelCase |
| `ChainContext` | `ChainContext` | `get_last_result/get_all_results/set_metadata/get_metadata/elapsed_time -> getLastResult/getAllResults/setMetadata/getMetadata/getElapsedTime` | 完整映射 | 仅命名风格变化 |
| `ChainResult` | `ChainResult` | 数据模型对位 | 完整映射 | Java 使用 builder 暴露 |
| `FilterResult` | `FilterResult` | Python 数据类 -> Java 静态工厂 `continueResult/skipResult/stopResult/modifyResult` | 基本对齐 | 语义一致，API 形态不同 |
| `FilterAction` / `ChainAction` / `HookType` | 同名枚举 | 枚举值对齐 | 完整映射 | Java 额外提供 `getValue()` |
| `EventFilter` | `EventFilter` | `filter -> filter` | 完整映射 | Java 额外公开 `getName()` |
| `RateLimitFilter` / `ValidationFilter` / `LoggingFilter` / `AuthFilter` / `ParamModifyFilter` / `ConditionalFilter` | 同名类 | `filter -> filter` | 完整映射 | Java 额外有 `ConditionalFilter.ConditionPredicate` 嵌套接口 |
| `CircuitBreakerFilter` | `CircuitBreakerFilter` | `filter/record_success/record_failure/failures -> filter/recordSuccess/recordFailure/getFailures` | 完整映射 | 仅命名风格差异 |

## 3. 本地消息队列

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `QueueMessage` | `QueueMessage` | 字段 -> `get*/set*` | 完整映射 | Java 用 POJO 暴露字段访问器 |
| `InvokeQueueMessage` | `InvokeQueueMessage` | 数据模型 -> `getResponse()` | 完整映射 | Java 以 getter 暴露 |
| `StreamQueueMessage` | `StreamQueueMessage` | 数据模型 -> `getResponse()` | 完整映射 | 同上 |
| `LocalMessageQueue` | `LocalMessageQueue` | `start/stop -> start/stop` | 完整映射 | - |
| `SubscriptionBase` | `SubscriptionBase` | `set_message_handler/activate/deactivate/is_active -> setMessageHandler/activate/deactivate/isActive` | 完整映射 | - |
| `MessageQueueBase` | `MessageQueueBase` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 完整映射 | - |
| `SubscriptionInMemory` | `SubscriptionInMemory` | `set_message_handler/activate/deactivate/is_active/push_message -> setMessageHandler/activate/deactivate/isActive/pushMessage` | 完整映射 | - |
| `MessageQueueInMemory` | `MessageQueueInMemory` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 完整映射 | - |

## 4. 分布式 runner（`drunner`）

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 无直接公开对位 | `DistributedRunner` | `ensureStarted/messageQueue/replySubscription/shutdown/replyTopic/agentTopic` | Java 扩展 | Java 把分布式运行时 holder 单独公开为桥接类 |
| `DMessageType` / `ResultType` | 同名枚举 | 枚举值对齐 | 完整映射 | - |
| `DmqMessage` / `DmqRequestMessage` / `DmqResponseMessage` | 同名类 | Python 数据类 -> Java `get*/set*` | 完整映射 | `body/payload` 双命名在 Java 中都保留了访问器 |
| `MessageQueueFactory` | `MessageQueueFactory` | `create -> create` | 完整映射 | - |
| `FakeMQ` | `FakeMessageQueue` | `start/stop/subscribe/unsubscribe/produce_message -> start/stop/subscribe/unsubscribe/produceMessage` | 完整映射 | Java 复用内存 MQ 实现 |
| `FakeSubscription` | 无直接公开类型 | `set_message_handler/activate/deactivate/push` | Python 独有 | Java 直接复用 `SubscriptionInMemory`，没有单独 `FakeSubscription` |
| `serialize_message()` / `deserialize_message()` | `MessageSerializer.serializeMessage()` / `deserializeMessage()` | 模块函数 -> 静态工具方法 | 部分映射 | Java 公开入口已齐，但未完整保留 Python 的 `TYPE_REGISTRY`、`datetime`、递归深度限制语义 |
| `ReplyTopicSubscription` | `ReplyTopicSubscription` | `activate/deactivate/register_collector/unregister_collector/is_active -> activate/deactivate/registerCollector/unregisterCollector/isActive` | 基本对齐 | Java 额外公开 `getTopic()`；`on_message` 收敛为私有 `onMessage()` |
| `CollectorKey` | `ReplyTopicSubscription.CollectorKey` | 顶层数据类 -> 公共嵌套 `record` | 基本对齐 | 能力在，公开位置变化 |
| `ResponseCollector` | `ResponseCollector` | `is_cancelled/is_expired/is_active/put_message/result/stream/check_message/close -> isCancelled/isExpired/isActive/putMessage/result/stream/checkMessage/close` | 完整映射 | Java 还额外提供 `close(CancelReason)` 重载 |
| `CancelReason` / `CancelEvent` | `CancelReason` / `CancelEvent` | 枚举和值对象对齐 | 完整映射 | 旧文档中这部分已误报，当前源码已补齐 |
| `RemoteClient` | `RemoteClient` | `start/stop/invoke/stream` | 完整映射 | Python 抽象基类 -> Java 接口 |
| `ProtocolEnum` / `RemoteClientConfig` | 同名类/枚举 | 字段/枚举对齐 | 完整映射 | - |
| `MqRemoteClient` | `MqRemoteClient` | `start/stop/invoke/stream` | 完整映射 | Java 已保留提前取消时发送 `STOP` 的语义 |
| `RemoteAgent` | `RemoteAgent` | `invoke/stream -> invoke/stream` | 完整映射 | - |
| `AgentAdapter` | `AgentAdapter` | `start/stop -> start/stop` | 完整映射 | 内部 `_handle_*` 在 Java 中为私有方法 |
| `build_stream_response()` / `build_final_response()` / `build_batch_response()` / `build_error_response()` | `MqMessageUtils.*` | 模块函数 -> 静态工具方法 | 部分映射 | 前三者对齐；`buildErrorResponse()` 目前仍固定写入 `errorCode=-1`，未透传 Python `error.error_code` |
| `MessageTask` | `MessageTask` | 数据类 -> POJO `getMessage()/getTask()` | 完整映射 | 旧文档中这部分已误报，当前源码已存在 |
| `MqServerAdapter` | `MqServerAdapter` | `start/stop -> start/stop` | 完整映射 | Java 已保留 adapter stop 时取消任务并回写错误响应的逻辑 |

## 5. 资源管理与基础类型

| Python API | Java API | 方法/成员映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentProvider` / `AgentGroupProvider` / `WorkflowProvider` / `ModelProvider` | 同名接口 | Python 类型别名 -> Java 函数式接口 | 完整映射 | Java 把类型提示固化成正式接口 |
| `Tag` + `ALL/GLOBAL/ACTIVE/INACTIVE` | `Tag` | 常量对齐 | 完整映射 | Python `Tag=str`；Java 改为常量类 |
| `TagMatchStrategy` / `TagUpdateStrategy` | 同名枚举 | 枚举值对齐 | 完整映射 | Java 额外提供 `getValue()` |
| `Ok` / `Error` / `Result` | `Ok<T>` / `Error<T>` / `Result<T>` | `is_ok/is_err/msg/error -> isOk/isError/getValue/getError` | 基本对齐 | Java 使用 sealed interface 建模，方法命名更显式 |
| `AbstractManager` | `AbstractManager<T>` | 抽象基类 | 完整映射 | 两边都不强调公开方法 |
| `AgentMgr` / `AgentGroupMgr` / `ModelMgr` / `PromptMgr` / `WorkflowMgr` / `SysOperationMgr` | 同名类 | `add_* / get_* / remove_* -> add*/get*/remove*` | 完整映射 | Java `PromptMgr`、`WorkflowMgr` 还额外公开 `PromptEntry` / `WorkflowEntry` |
| `ResourceRegistry` | `ResourceRegistry` | `remove_by_id/tool/prompt/model/workflow/agent/agent_group/sys_operation -> removeById/tool/prompt/model/workflow/agent/agentGroup/sysOperation` | 完整映射 | - |
| `TagMgr` | `TagMgr` | `has_tag/list_tags/has_resource/tag_resource/remove_resource/remove_resource_tags/update_resource_tags/remove_tag/get_tag_resources/find_resources_by_tags/has_resource_tag/get_resources_tags/display -> 同名 camelCase` | 完整映射 | `display()` 已在 Java 中实现 |
| `ToolMgr` | `ToolMgr` | `add_tool/get_tool/get_mcp_tool/get_mcp_tools/get_mcp_tool_id/remove_tool/generate_mcp_tool_id/add_tool_server/get_mcp_server_ids/remove_tool_server/add_sys_operation_tools/remove_sys_operation_tools/get_sys_operation_tool_ids/refresh_tool_server/release -> 同名 camelCase` | 完整映射 | `McpServerResource` / `SysOpToolResource` 在 Java 中变成嵌套公共 `record` |
| `McpServerResource` / `SysOpToolResource` | `ToolMgr.McpServerResource` / `ToolMgr.SysOpToolResource` | 顶层类 -> 嵌套 `record` | 基本对齐 | 能力在，公开位置变化 |
| `ResourceMgr` | `ResourceMgr` | `add/remove/get` 系列、`get_sys_op_tool_cards()`、`get_tool_infos()`、`add/remove/refresh MCP`、`get_mcp_tool_infos()`、`tag` 系列、`release()` -> 同名 camelCase | 完整映射 | Java 还额外提供 `addTools()`、`AgentEntry/WorkflowEntry/ModelEntry/PromptEntry` 等批量桥接 record |
| `ThreadSafeDict` | `ThreadSafeDict` | `get/get_or_set/get_or_create/clear/keys/values -> get/getOrSet/getOrCreate/clear/keys/values` | 部分映射 | Java 已提供线程安全包装，但 `items()/setdefault()/pop()/update()` 没有完全保留为同名直接 API，转为 `snapshot()/remove()/putAll()` 等 |

## 6. Java 侧额外公开的桥接 API

| Java API | Python 对位 | 说明 |
| --- | --- | --- |
| `RunnerImpl` | Python `_RunnerImpl` | Python 实现类是内部类，Java 版把实例实现单独公开 |
| `DistributedRunner` | Python `Runner.dist_pubsub` / `Runner.system_reply_sub` / topic 模板组合语义 | Java 新增的分布式运行时 holder |
| `MessageSerializer` | Python `serialize_message()/deserialize_message()` | Java 把模块函数收敛到工具类 |
| `MqMessageUtils` | Python `build_*_response()` | Java 把模块函数收敛到工具类 |
| `ResourceMgr.AgentEntry` / `WorkflowEntry` / `ModelEntry` / `PromptEntry` | Python 批量 tuple 参数 | Java 用公开 `record` 明确承载批量添加参数 |
| `PromptMgr.PromptEntry` / `WorkflowMgr.WorkflowEntry` | Python 无同名公开类型 | Java 为批量输入单独建模 |

## 7. 当前仍未完全对齐的点

| 类别 | 仍未对齐项 | Python 现状 | Java 现状 | 判断 |
| --- | --- | --- | --- | --- |
| Callback DSL | `on()`、`trigger_on_call()`、`emits()`、`emits_stream()`、`emit_around()`、`transform_io()` | `AsyncCallbackFramework` 直接提供 decorator DSL | `CallbackFramework` 仅提供显式注册/触发 API | 真实缺口 |
| Callback decorator 工厂 | `create_*_decorator()` 系列 | `callback/decorator.py` 中公开 | Java 无等价公开工具 | 真实缺口 |
| ReplyTopicSubscription 并发保护 | `register_collector()` 的 `max_request_concurrency` 校验 | Python 在注册前检查 collector 数量上限 | Java `registerCollector()` 未做并发上限判断 | 真实缺口 |
| Message 序列化 | 递归类型注册、`datetime`、深度限制 | Python `TYPE_REGISTRY` + `MAX_RECURSE_DEPTH` + `datetime` 自定义序列化 | Java 仅做通用 JSON 映射 | 行为缺口 |
| MQ 错误码透传 | `build_error_response()` | Python 写入 `error.error_code` 与 `error.message` | Java 当前固定 `errorCode=-1` | 行为缺口 |
| Fake MQ helper | `FakeSubscription` | Python 公开单独类 | Java 直接复用 `SubscriptionInMemory` | 低优先级 API 缺口 |
| ThreadSafeDict 精确表面 | `items()/setdefault()/pop()/update()` | Python 公开这些容器方法 | Java 用 `snapshot()/remove()/putAll()` 等替代 | 低优先级 API 差异 |

## 8. 不应再计入缺漏的项

- `Runner.dist_pubsub` / `Runner.system_reply_sub` 已由 `Runner.distPubsub()` / `Runner.systemReplySub()` 公开。
- `Runner.runWorkflow* / runAgent* / runAgentGroup*` 的 `envs` 重载已经存在。
- `CallbackFramework.registerSync()`、`triggerDelayed()`、`triggerGenerator()`、`saveState()` 已实现。
- `ReplyTopicSubscription.isActive()`、`CancelReason`、`CancelEvent`、`ResponseCollector.isCancelled()/isExpired()/isActive()/checkMessage()` 已实现。
- `MessageTask` 已在 `drunner/server_adapter/MessageTask.java` 中落地。
- `ResourceMgr.refreshMcpServer()`、`getMcpToolInfos()` 已实现。
- `TagMgr.display()` 与 `ThreadSafeDict` 类型本身已经存在。
- `MqRemoteClient` 已保留提前取消时发送 `STOP` 的逻辑。
- `MqServerAdapter` 已保留 adapter stop 时取消任务并回写错误响应的逻辑。
