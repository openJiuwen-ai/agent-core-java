# Runner 模块 API 文档

> 包路径：`com.openjiuwen.core.runner`

Runner 模块负责统一运行入口、资源注册与查找、回调框架、轻量消息队列以及基于标签的资源组织能力。该模块也是 `application`、`singleagent`、`multiagent` 等高层模块的公共执行底座。

---

## 目录

- [1. 运行入口与配置](#1-运行入口与配置)
- [2. 基础结果与标签模型](#2-基础结果与标签模型)
- [3. 回调框架（callback）](#3-回调框架callback)
- [4. 消息队列（mq）](#4-消息队列mq)
- [5. 资源管理（resourcemanager）](#5-资源管理resourcemanager)

---

## 1. 运行入口与配置

### 1.1 Runner

全局静态运行入口，对单例 `RunnerImpl` 进行代理。

**包路径**：`com.openjiuwen.core.runner`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `resourceMgr()` | `ResourceMgr` | 获取全局资源管理器 |
| `pubsub()` | `LocalMessageQueue` | 获取本地发布订阅对象 |
| `callbackFramework()` | `CallbackFramework` | 获取全局回调框架 |
| `setConfig(RunnerConfig config)` | `void` | 设置全局配置 |
| `getConfig()` | `RunnerConfig` | 获取当前配置 |
| `start()` | `boolean` | 启动 Runner 和关联组件 |
| `stop()` | `boolean` | 停止 Runner 并释放资源 |
| `runWorkflow(Object workflow, Object inputs, Object session, ModelContext context)` | `Object` | 执行工作流 |
| `runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行工作流 |
| `runAgent(Object agent, Object inputs, Object session, ModelContext context)` | `Object` | 执行单智能体 |
| `runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行单智能体 |
| `runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context)` | `Object` | 执行多智能体分组 |
| `runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行多智能体分组 |
| `release(String sessionId)` | `void` | 释放会话相关资源 |

### 1.2 RunnerImpl

Runner 实际实现，负责创建 Session、调度 Workflow/Agent/AgentGroup，并管理生命周期。

**包路径**：`com.openjiuwen.core.runner`

**构造方法**
```java
RunnerImpl()
RunnerImpl(String runnerId, RunnerConfig config)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getResourceMgr()` | `ResourceMgr` | 获取资源管理器 |
| `getPubsub()` | `LocalMessageQueue` | 获取本地发布订阅对象 |
| `getCallbackFramework()` | `CallbackFramework` | 获取回调框架 |
| `setConfig(RunnerConfig config)` | `void` | 设置 Runner 配置 |
| `getConfig()` | `RunnerConfig` | 获取当前配置 |
| `start()` / `stop()` | `boolean` | 启停 Runner |
| `runWorkflow(...)` | `Object` | 解析工作流实例并创建工作流 Session |
| `runWorkflowStreaming(...)` | `Iterator<Object>` | 流式执行工作流 |
| `runAgent(...)` | `Object` | 解析 Agent、创建 `AgentSessionApi` 并执行 |
| `runAgentStreaming(...)` | `Iterator<Object>` | 流式执行 Agent，并在流结束后自动 `postRun()` |
| `runAgentGroup(...)` | `Object` | 执行 AgentGroup |
| `runAgentGroupStreaming(...)` | `Iterator<Object>` | 流式执行 AgentGroup |
| `release(String sessionId)` | `void` | 调用默认 Checkpointer 释放会话 |
| `generateWorkflowKey(String workflowId, String workflowVersion)` | `String` | 生成 `workflowId_version` 键 |

### 1.3 RunnerConfig

Runner 全局配置。

**包路径**：`com.openjiuwen.core.runner`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `distributedMode` | `boolean` | `true` | 是否启用分布式模式 |
| `distributedConfig` | `DistributedConfig` | 默认构造 | 分布式配置 |
| `envPrefix` | `String` | `""` | 主题名前缀 |
| `instanceId` | `String` | 随机 UUID | 实例 ID |
| `checkpointerConfig` | `Map<String, Object>` | - | Checkpointer 配置 |
| `DEFAULT` | `RunnerConfig` | 静态常量 | 默认配置（非分布式 + fake MQ） |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `agentTopicTemplate()` | `String` | 获取补齐环境前缀后的 Agent 主题模板 |
| `replyTopicTemplate()` | `String` | 获取补齐环境前缀后的回复主题模板 |
| `setRunnerConfig(RunnerConfig config)` | `void` | 设置全局静态配置 |
| `getRunnerConfig()` | `RunnerConfig` | 获取全局静态配置 |

### 1.4 DistributedConfig / MessageQueueConfig / PulsarConfig / MessageQueueType

Runner 分布式与消息队列配置模型。

**包路径**：`com.openjiuwen.core.runner`

**DistributedConfig 字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `requestTimeout` | `double` | `30.0` | 请求超时时间 |
| `maxRequestConcurrency` | `int` | `10000` | 最大并发请求数 |
| `messageQueueConfig` | `MessageQueueConfig` | 默认构造 | MQ 配置 |
| `agentTopicTemplate` | `String` | `openjiuwen.single_agent.{agent_id}.{version}` | Agent 主题模板 |
| `replyTopicTemplate` | `String` | `openjiuwen.reply.runner.{instance_id}` | 回复主题模板 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getAgentTopicTemplate(String envPrefix)` | `String` | 获取带前缀的 Agent 主题模板 |
| `getReplyTopicTemplate(String envPrefix)` | `String` | 获取带前缀的回复主题模板 |

**MessageQueueConfig 字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `type` | `String` | `MessageQueueType.PULSAR.getValue()` | 队列类型 |
| `pulsarConfig` | `PulsarConfig` | - | Pulsar 配置 |

**PulsarConfig 字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `url` | `String` | - | Pulsar 服务地址 |
| `maxWorkers` | `int` | `8` | 最大工作线程数 |

**MessageQueueType 枚举值**

| 枚举值 | 说明 |
|--------|------|
| `PULSAR` | Pulsar 队列 |
| `FAKE` | 伪造/本地占位队列 |

---

## 2. 基础结果与标签模型

### 2.1 Result / Ok / Error

类型安全的结果封装接口与成功/失败实现。

**包路径**：`com.openjiuwen.core.runner.base`

| 类型 | 方法/字段 | 说明 |
|------|-----------|------|
| `Result<T>` | `isOk()`, `isError()`, `getValue()`, `getError()` | 统一结果接口 |
| `Ok<T>` | `Ok(T value)` | 成功结果实现 |
| `Error<T>` | `Error(Exception error)` | 失败结果实现 |

### 2.2 Tag / TagMatchStrategy / TagUpdateStrategy

标签常量与标签匹配/更新策略。

**包路径**：`com.openjiuwen.core.runner.base`

| 类型 | 关键内容 | 说明 |
|------|----------|------|
| `Tag` | `ALL`, `GLOBAL`, `ACTIVE`, `INACTIVE` | 内建标签常量 |
| `TagMatchStrategy` | `ALL`, `ANY` | 查询资源时的标签匹配策略 |
| `TagUpdateStrategy` | `MERGE`, `REPLACE` | 更新资源标签时的策略 |

### 2.3 Provider 接口

用于资源管理器延迟实例化的 Provider 抽象，全部继承 `Supplier`。

**包路径**：`com.openjiuwen.core.runner.base`

| 类型 | 泛型/返回值 | 说明 |
|------|-------------|------|
| `AgentProvider<T>` | `Supplier<T>` | Agent 提供器 |
| `AgentGroupProvider<T>` | `Supplier<T>` | AgentGroup 提供器 |
| `ModelProvider` | `Supplier<Model>` | 模型提供器 |
| `WorkflowProvider` | `Supplier<Workflow>` | 工作流提供器 |

---

## 3. 回调框架（callback）

### 3.1 CallbackFramework

通用事件回调框架，支持优先级、过滤器、链式执行、指标统计、超时与历史回放。

**包路径**：`com.openjiuwen.core.runner.callback`

**构造方法**
```java
CallbackFramework()
CallbackFramework(boolean enableMetrics, boolean enableLogging)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `register(...)` | `CallbackInfo` | 注册回调，完整重载支持优先级、一次性执行、过滤器、回滚、超时与重试 |
| `unregister(String event, Function<Map<String, Object>, Object> callback)` | `void` | 注销单个回调 |
| `unregisterNamespace(String namespace)` | `void` | 按命名空间批量注销 |
| `unregisterByTags(Set<String> tags)` | `void` | 按标签批量注销 |
| `unregisterEvent(String event)` | `void` | 清空事件下全部回调/链/过滤器 |
| `trigger(String event, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | 触发事件并按优先级执行 |
| `trigger(String event, Map<String, Object> kwargs)` | `List<Object>` | 仅传 kwargs 触发 |
| `trigger(String event)` | `List<Object>` | 无参触发 |
| `triggerChain(String event, Object[] args, Map<String, Object> kwargs)` | `ChainResult` | 以链模式执行回调 |
| `triggerParallel(String event, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | 并行执行回调 |
| `triggerUntil(String event, Predicate<Object> condition, Object[] args, Map<String, Object> kwargs)` | `Object` | 执行到满足条件为止 |
| `triggerWithTimeout(String event, double timeoutSeconds, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | 带超时触发 |
| `triggerStream(String event, Iterator<?> inputStream, Object[] args, Map<String, Object> kwargs)` | `Iterator<Object>` | 按输入流逐项触发 |
| `addFilter(String event, EventFilter filter)` | `void` | 为指定事件增加过滤器 |
| `addGlobalFilter(EventFilter filter)` | `void` | 增加全局过滤器 |
| `addCircuitBreaker(String event, CallbackInfo callback, int failureThreshold, double timeout)` | `void` | 为回调挂接断路器 |
| `addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook)` | `void` | 注册生命周期 Hook |
| `getMetrics(...)` / `resetMetrics()` | `Map<String, Map<String, Object>>` / `void` | 获取或重置指标 |
| `getSlowCallbacks(double threshold)` | `List<Map<String, Object>>` | 获取慢回调列表 |
| `enableEventHistory(boolean enabled)` | `void` | 开关事件历史记录 |
| `getEventHistory(String event, Long since)` | `List<Map<String, Object>>` | 获取事件历史 |
| `replayEvents(Long since)` | `void` | 回放历史事件 |
| `listEvents(String namespace)` | `List<String>` | 列出事件名 |
| `listCallbacks(String event)` | `List<Map<String, Object>>` | 列出事件下回调 |
| `getStatistics()` | `Map<String, Object>` | 获取整体统计信息 |

### 3.2 CallbackInfo / CallbackMetrics

回调元数据与性能指标模型。

**包路径**：`com.openjiuwen.core.runner.callback`

| 类型 | 关键字段/方法 | 说明 |
|------|---------------|------|
| `CallbackInfo` | `callback`, `priority`, `once`, `enabled`, `namespace`, `tags`, `maxRetries`, `retryDelay`, `timeout`, `callbackName`, `getCallbackDisplayName()` | 注册回调时的元信息 |
| `CallbackMetrics` | `update(double executionTime, boolean isError)`, `getAvgTime()`, `toMap()` | 统计调用次数、耗时、错误率 |

### 3.3 CallbackChain / ChainContext / ChainResult / ChainAction

链式回调执行模型。

**包路径**：`com.openjiuwen.core.runner.callback`

| 类型 | 关键方法/字段 | 说明 |
|------|---------------|------|
| `CallbackChain` | `add(...)`, `remove(...)`, `execute(ChainContext context)` | 管理链式回调执行与回滚 |
| `CallbackChain.ExceptionContext` | `exception`, `chainContext` | 错误处理器上下文 |
| `ChainContext` | `event`, `initialArgs`, `initialKwargs`, `results`, `metadata`, `getLastResult()`, `getAllResults()`, `setMetadata(...)`, `getMetadata(...)`, `getElapsedTime()` | 链执行共享上下文 |
| `ChainResult` | `action`, `result`, `context`, `error` | 链执行结果 |
| `ChainAction` | `CONTINUE`, `BREAK`, `RETRY`, `ROLLBACK` | 链控制动作 |

### 3.4 EventFilter / FilterResult / FilterAction / HookType

过滤器与 Hook 抽象。

**包路径**：`com.openjiuwen.core.runner.callback`

| 类型 | 关键方法/值 | 说明 |
|------|-------------|------|
| `EventFilter` | `filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | 过滤器基类 |
| `FilterResult` | `continueResult()`, `continueResult(args, kwargs)`, `skipResult(...)`, `stopResult(...)`, `modifyResult(...)` | 过滤结果封装 |
| `FilterAction` | `CONTINUE`, `STOP`, `SKIP`, `MODIFY` | 过滤动作 |
| `HookType` | `BEFORE`, `AFTER`, `ERROR`, `CLEANUP` | Hook 类型 |

### 3.5 内置过滤器

**包路径**：`com.openjiuwen.core.runner.callback`

| 类型 | 构造/API | 说明 |
|------|----------|------|
| `AuthFilter` | `AuthFilter(String requiredRole)` | 基于 `user_role` 的角色校验 |
| `CircuitBreakerFilter` | `CircuitBreakerFilter(...)`, `recordSuccess(...)`, `recordFailure(...)` | 断路器过滤器 |
| `ConditionalFilter` | `ConditionalFilter(ConditionPredicate condition)` | 条件式过滤 |
| `LoggingFilter` | `LoggingFilter()` | 记录事件与参数 |
| `ParamModifyFilter` | `ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier)` | 修改参数 |
| `RateLimitFilter` | `RateLimitFilter(int maxCalls, double timeWindow)` | 限流 |
| `ValidationFilter` | `ValidationFilter(Predicate<Map<String, Object>> validator)` | 参数校验 |

---

## 4. 消息队列（mq）

### 4.1 LocalMessageQueue

本地发布订阅占位实现。

**包路径**：`com.openjiuwen.core.runner.mq`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `start()` | `boolean` | 启动本地队列（当前为 no-op） |
| `stop()` | `boolean` | 停止本地队列（当前为 no-op） |

### 4.2 MessageQueueBase / MessageQueueInMemory

消息队列抽象与内存实现。

**包路径**：`com.openjiuwen.core.runner.mq`

| 类型 | 方法 | 说明 |
|------|------|------|
| `MessageQueueBase` | `start()`, `stop()`, `subscribe(String topic)`, `unsubscribe(String topic)`, `produceMessage(String topic, QueueMessage message)` | 队列抽象基类 |
| `MessageQueueInMemory` | `MessageQueueInMemory(int queueMaxSize, long timeoutMs)`, `subscribe(...)`, `produceMessage(...)`, `start()`, `stop()` | 基于阻塞队列和虚拟线程的内存实现 |

### 4.3 SubscriptionBase / SubscriptionInMemory

订阅抽象与内存实现。

**包路径**：`com.openjiuwen.core.runner.mq`

| 类型 | 方法 | 说明 |
|------|------|------|
| `SubscriptionBase` | `setMessageHandler(...)`, `activate()`, `deactivate()`, `isActive()` | 订阅抽象基类 |
| `SubscriptionInMemory` | `SubscriptionInMemory(...)`, `pushMessage(QueueMessage message)` | 内存订阅实现，支持请求-响应和流式响应 |

### 4.4 QueueMessage / InvokeQueueMessage / StreamQueueMessage

消息队列数据模型。

**包路径**：`com.openjiuwen.core.runner.mq`

| 类型 | 字段/方法 | 说明 |
|------|-----------|------|
| `QueueMessage` | `messageId`, `payload`, `errorCode`, `errorMsg` 及对应 getter/setter | 基础消息体 |
| `InvokeQueueMessage` | `getResponse()` | 携带 `CompletableFuture<Object>` 的请求-响应消息 |
| `StreamQueueMessage` | `getResponse()` | 携带 `CompletableFuture<Iterator<Object>>` 的流式消息 |

---

## 5. 资源管理（resourcemanager）

### 5.1 ResourceMgr

统一资源管理门面，负责注册、获取、移除 Agent、Workflow、Tool、Model、Prompt、Group、SysOperation、MCP Server 以及标签管理。

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 方法分类 | 代表 API | 说明 |
|----------|----------|------|
| AgentGroup | `addAgentGroup(...)`, `removeAgentGroup(...)`, `getAgentGroup(...)` | 管理多智能体分组资源 |
| Agent | `addAgent(...)`, `addAgents(...)`, `removeAgent(...)`, `getAgent(...)` | 管理 Agent 资源 |
| Workflow | `addWorkflow(...)`, `addWorkflows(...)`, `removeWorkflow(...)`, `getWorkflow(...)` | 管理工作流资源 |
| Tool | `addTool(...)`, `addTools(...)`, `removeTool(...)`, `getTool(...)` | 管理工具资源 |
| Model | `addModel(...)`, `addModels(...)`, `removeModel(...)`, `getModel(...)` | 管理模型资源 |
| Prompt | `addPrompt(...)`, `addPrompts(...)`, `removePrompt(...)`, `getPrompt(...)` | 管理提示词模板 |
| SysOperation | `addSysOperation(...)`, `removeSysOperation(...)`, `getSysOperation(...)`, `getSysOpToolCards(...)` | 管理系统操作与自动导出的工具卡片 |
| ToolInfo | `getToolInfos(...)` | 按类型/标签聚合工具描述 |
| MCP | `addMcpServer(...)`, `removeMcpServer(...)`, `getMcpTool(...)` | 管理 MCP Server 与工具 |
| Tag | `getResourceByTag(...)`, `listTags()`, `hasTag(...)`, `removeTag(...)`, `updateResourceTag(...)`, `addResourceTag(...)`, `removeResourceTag(...)`, `getResourceTag(...)`, `resourceHasTag(...)` | 基于标签组织资源 |
| Cleanup | `release()` | 释放 Tool/MCP 等底层资源 |

**记录类型**

| 类型 | 说明 |
|------|------|
| `AgentEntry` | 批量注册 Agent 条目 |
| `WorkflowEntry` | 批量注册 Workflow 条目 |
| `ModelEntry` | 批量注册 Model 条目 |
| `PromptEntry` | 批量注册 Prompt 条目 |

### 5.2 ResourceRegistry

各子资源管理器的聚合容器。

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `removeById(String resourceId)` | `void` | 按 ID 从所有子管理器中移除资源 |
| `tool()` / `prompt()` / `model()` / `workflow()` / `agent()` / `agentGroup()` / `sysOperation()` | 对应子管理器 | 获取子管理器 |

### 5.3 AbstractManager 与 Provider 型子管理器

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 类型 | 方法 | 说明 |
|------|------|------|
| `AbstractManager<T>` | 受保护的 `registerResourceProvider(...)`, `getResource(...)`, `unregisterResourceProvider(...)` | Provider 型管理器基类 |
| `AgentMgr<T>` | `addAgent(...)`, `getAgent(...)`, `removeAgent(...)` | Agent Provider 管理器 |
| `AgentGroupMgr<T>` | `addAgentGroup(...)`, `getAgentGroup(...)`, `removeAgentGroup(...)` | AgentGroup Provider 管理器 |
| `ModelMgr` | `addModel(...)`, `getModel(...)`, `removeModel(...)` | Model Provider 管理器 |
| `WorkflowMgr` | `addWorkflow(...)`, `addWorkflows(...)`, `getWorkflow(...)`, `removeWorkflow(...)` | Workflow Provider 管理器 |

### 5.4 PromptMgr / SysOperationMgr

直接存储实例的轻量管理器。

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 类型 | 方法 | 说明 |
|------|------|------|
| `PromptMgr` | `addPrompt(...)`, `addPrompts(...)`, `removePrompt(...)`, `getPrompt(...)` | 管理 `PromptTemplate` 实例 |
| `SysOperationMgr` | `addSysOperation(...)`, `removeSysOperation(...)`, `getSysOperation(...)` | 管理 `SysOperation` 实例 |

### 5.5 ToolMgr

工具、MCP Server 与 SysOperation 导出工具管理器。

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addTool(String toolId, Tool tool)` | `void` | 注册本地工具 |
| `getTool(String toolId)` | `Tool` | 获取工具 |
| `getMcpTool(String toolName, String serverId)` | `Tool` | 获取指定 MCP 工具 |
| `getMcpTools(String serverId)` | `List<Tool>` | 获取 MCP Server 下全部工具 |
| `getMcpToolId(String serverId, String toolName)` | `Object` | 获取 MCP 工具 ID 或 ID 列表 |
| `removeTool(String toolId)` | `Tool` | 移除工具 |
| `generateMcpToolId(String serverId, String serverName, String toolName)` | `String` | 生成 MCP 工具 ID |
| `addToolServer(McpServerConfig serverConfig, Double expiryTime)` | `List<McpToolCard>` | 连接 MCP Server 并注册工具 |
| `getMcpServerIds(String serverName)` | `List<String>` | 根据服务名获取 MCP Server ID |
| `removeToolServer(String serverId, boolean ignoreNotExist)` | `List<String>` | 移除 MCP Server 及其工具 |
| `refreshToolServer(String serverId, boolean skipNotExist, boolean force)` | `List<McpToolCard>` | 刷新远端工具清单 |
| `addSysOperationTools(String sysOpId, List<String> toolIds)` | `void` | 记录系统操作导出的工具 |
| `removeSysOperationTools(String sysOpId)` | `List<String>` | 移除系统操作导出工具记录 |
| `getSysOperationToolIds(String sysOpId)` | `List<String>` | 获取系统操作导出工具 ID |
| `release()` | `void` | 释放 MCP 连接和内部缓存 |

**记录类型**

| 类型 | 说明 |
|------|------|
| `McpServerResource` | MCP Server 配置、客户端、工具 ID 与更新时间 |
| `SysOpToolResource` | SysOperation 导出工具记录 |

### 5.6 TagMgr

标签管理器，提供资源与标签的双向索引和线程安全更新。

**包路径**：`com.openjiuwen.core.runner.resourcemanager`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `hasTag(String tag)` | `boolean` | 标签是否存在 |
| `listTags()` | `List<String>` | 列出全部非空标签 |
| `hasResource(String resourceId)` | `boolean` | 资源是否已被标签系统管理 |
| `hasResourceTag(String resourceId, String tag)` | `boolean` | 资源是否带有标签 |
| `getResourcesTags(String resourceId)` | `List<String>` | 获取资源全部标签 |
| `tagResource(String resourceId, Object tags)` | `List<String>` | 为资源设置/追加标签 |
| `removeResource(String resourceId)` | `List<String>` | 从标签系统移除资源 |
| `removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists)` | `List<String>` | 移除资源标签 |
| `updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy)` | `List<String>` | 替换或合并资源标签 |
| `removeTag(String tag, boolean skipIfNotExists)` | `List<String>` | 删除标签并返回受影响资源 |
| `getTagResources(String tag)` | `List<String>` | 获取标签下资源 ID |
| `findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists)` | `List<String>` | 按标签查询资源 |
