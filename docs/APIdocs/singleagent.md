# SingleAgent 模块 API 文档

> 包路径：`com.openjiuwen.core.singleagent`

SingleAgent 模块提供单智能体抽象、基于 Controller 的 Agent、ReAct Agent、Rail 生命周期回调体系，以及 Skill 管理与远端技能同步能力。

---

## 目录

- [1. 核心抽象与能力管理](#1-核心抽象与能力管理)
- [2. 内置 Agent 实现](#2-内置-agent-实现)
- [3. Rail 回调体系](#3-rail-回调体系)
- [4. Schema 模型](#4-schema-模型)
- [5. Skills 子模块](#5-skills-子模块)

---

## 1. 核心抽象与能力管理

### 1.1 BaseAgent

单智能体抽象基类，统一持有 `AgentCard`、`AbilityManager`、`AgentCallbackManager` 和 `SkillUtil`。

**包路径**：`com.openjiuwen.core.singleagent`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configure(Object config)` | `BaseAgent` | 更新配置（抽象方法） |
| `getConfig()` | `Object` | 获取当前配置（抽象方法） |
| `getCard()` | `AgentCard` | 获取 Agent 卡片 |
| `getAbilityManager()` | `AbilityManager` | 获取能力管理器 |
| `getAgentCallbackManager()` | `AgentCallbackManager` | 获取 Rail/回调管理器 |
| `getSkillUtil()` | `SkillUtil` | 获取技能工具 |
| `registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | `BaseAgent` | 注册回调 |
| `registerRail(AgentRail rail)` | `BaseAgent` | 注册 Rail |
| `unregisterRail(AgentRail rail)` | `BaseAgent` | 注销 Rail |
| `fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx)` | `void` | 触发指定生命周期事件 |
| `invoke(Object inputs, Session session)` | `Object` | 同步执行入口（抽象） |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行入口（抽象） |

### 1.2 ControllerAgent

基于 `Controller` 的 Agent 实现，用于承接复杂事件驱动逻辑。

**包路径**：`com.openjiuwen.core.singleagent`  
**继承**：`BaseAgent`

**构造方法**
```java
ControllerAgent(AgentCard card, Controller controller)
ControllerAgent(AgentCard card, Controller controller, ControllerConfig config)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configure(Object config)` | `BaseAgent` | 接收 `ControllerConfig` 或配置 Map |
| `getConfig()` | `Object` | 返回 `ControllerConfig` |
| `getController()` | `Controller` | 获取底层控制器 |
| `getContextEngine()` | `ContextEngine` | 获取上下文引擎 |
| `releaseSession(String sessionId)` | `void` | 释放控制器事件队列订阅和 Runner 资源 |
| `invoke(Object inputs, Session session)` | `ControllerOutput` | 将用户输入转换为 `InputEvent` 后交给 Controller |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 调用 Controller 的流式接口 |

### 1.3 AbilityManager

Agent 能力管理器，负责缓存可调用工具、工作流、子 Agent 与 MCP Server 信息，并执行工具调用。

**包路径**：`com.openjiuwen.core.singleagent`  
**实现**：`ToolRegistry`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `add(Object ability)` | `void` | 添加 ToolCard / WorkflowCard / AgentCard / McpServerConfig |
| `remove(String name)` | `Object` | 按名称移除单个能力 |
| `remove(List<String> names)` | `List<Object>` | 批量移除能力 |
| `get(String name)` | `Object` | 获取能力卡片/配置 |
| `list()` | `List<Object>` | 列出全部能力 |
| `listToolInfo()` | `List<ToolInfo>` | 获取全部 `ToolInfo` |
| `listToolInfo(List<String> names, String mcpServerName)` | `List<ToolInfo>` | 按名称/MCP Server 过滤工具信息 |
| `setToolDescription(String toolName, String description)` | `void` | 更新工具描述 |
| `executeAsToolExecutor(Object toolCallObj, Session session)` | `ToolExecutionResult` | 作为 ToolExecutor 执行单次工具调用 |
| `execute(AgentCallbackContext ctx, Object toolCall, Session session, String tag)` | `List<ToolExecutionEntry>` | 在 Rail 生命周期下执行一个或多个工具调用 |
| `executeSingleToolCall(ToolCall toolCall, Session session, String tag)` | `ToolExecutionEntry` | 执行单次工具/工作流/Agent 调用 |

**记录类型**

| 类型 | 说明 |
|------|------|
| `ToolExecutionEntry` | 原始结果与 `ToolMessage` 对 |

### 1.4 AgentCallbackManager

Agent Rail/回调注册与执行中心，通过 `Runner.callbackFramework()` 统一派发。

**包路径**：`com.openjiuwen.core.singleagent`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | `void` | 注册回调 |
| `registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | `void` | 使用默认优先级注册 |
| `registerRail(AgentRail rail, Object agent)` | `void` | 注册 Rail，并自动挂接其工具 |
| `unregisterRail(AgentRail rail, Object agent)` | `void` | 注销 Rail 并移除其工具 |
| `unregister(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | `void` | 注销单个回调 |
| `clear(AgentCallbackEvent event)` | `void` | 清除某类事件或全部事件回调 |
| `hasHooks(AgentCallbackEvent event)` | `boolean` | 判断是否已注册 Hook |
| `execute(AgentCallbackEvent event, AgentCallbackContext ctx)` | `void` | 执行事件回调 |

### 1.5 AbilityExecutionError

能力执行失败异常，统一携带可写回上下文的 `ToolMessage`。

**包路径**：`com.openjiuwen.core.singleagent`  
**继承**：`AgentError`

**构造方法**
```java
AbilityExecutionError(StatusCode status, String msg, ToolMessage toolMessage)
AbilityExecutionError(StatusCode status, String msg, Throwable cause, ToolMessage toolMessage)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getToolMessage()` | `ToolMessage` | 获取错误对应的工具消息 |

---

## 2. 内置 Agent 实现

### 2.1 ReActAgent

标准 ReAct Agent，实现“模型推理 -> 工具调用 -> 观察结果 -> 继续推理”的循环。

**包路径**：`com.openjiuwen.core.singleagent.agents`  
**继承**：`BaseAgent`

**构造方法**
```java
ReActAgent(AgentCard card)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configure(Object configObj)` | `BaseAgent` | 更新 `ReActAgentConfig` 并按需重建 LLM/ContextEngine |
| `getConfig()` | `Object` | 获取 `ReActAgentConfig` |
| `getContextEngine()` | `ContextEngine` | 获取上下文引擎 |
| `invoke(Object inputs, Session session)` | `Object` | 执行 ReAct 同步循环 |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 当前实现为“执行 invoke 后回放 Session 流” |

**行为说明**

- 支持 `Map` 输入（含 `query`、`conversation_id`）或直接传入 `String`。
- 通过 `ContextEngine` 创建 `ModelContext`，并根据配置将上下文重载工具自动注册到 `Runner.resourceMgr()`。
- 模型调用和工具调用都通过 Rail 生命周期事件包裹。

### 2.2 ReActAgentConfig

ReAct Agent 配置对象。

**包路径**：`com.openjiuwen.core.singleagent.agents`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `memScopeId` | `String` | `""` | 记忆域 ID |
| `modelName` | `String` | `""` | 模型名 |
| `modelProvider` | `String` | `"openai"` | 模型提供方 |
| `apiKey` | `String` | `""` | API Key |
| `apiBase` | `String` | `""` | API Base |
| `promptTemplateName` | `String` | `""` | 提示词模板名 |
| `promptTemplate` | `List<Map<String, String>>` | 空列表 | 提示词内容 |
| `maxIterations` | `int` | `5` | 最大迭代次数 |
| `modelClientConfig` | `ModelClientConfig` | - | 模型客户端配置 |
| `modelConfigObj` | `ModelRequestConfig` | - | 模型请求配置 |
| `sysOperationId` | `String` | - | 系统操作 ID，用于 skills |
| `contextEngineConfig` | `ContextEngineConfig` | 默认构造 | 上下文配置 |
| `contextProcessors` | `List<Object>` | - | 处理器配置，通常传 `ContextEngine.ProcessorSpec` |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configureModel(String modelName)` | `ReActAgentConfig` | 配置模型名 |
| `configureModelProvider(String provider, String apiKey, String apiBase)` | `ReActAgentConfig` | 配置提供方与认证 |
| `configurePrompt(String promptName)` | `ReActAgentConfig` | 配置提示词模板名 |
| `configurePromptTemplate(List<Map<String, String>> promptTemplate)` | `ReActAgentConfig` | 直接配置提示词 |
| `configureContextEngine(Integer maxContextMessageNum, Integer defaultWindowRoundNum, boolean enableReload)` | `ReActAgentConfig` | 配置上下文引擎 |
| `configureMemScope(String memScopeId)` | `ReActAgentConfig` | 配置记忆域 |
| `configureMaxIterations(int maxIterations)` | `ReActAgentConfig` | 配置最大迭代次数 |
| `configureModelClient(String provider, String apiKey, String apiBase, String modelName, boolean verifySsl)` | `ReActAgentConfig` | 一次性构造模型客户端配置 |
| `configureContextProcessors(List<Object> processors)` | `ReActAgentConfig` | 配置上下文处理器列表 |

### 2.3 ReActAgentEvolve

带自演进 Operator 的 ReAct Agent，内部使用 `LLMCallOperator` 和 `ToolCallOperator`。

**包路径**：`com.openjiuwen.core.singleagent.agents`  
**继承**：`BaseAgent`

**构造方法**
```java
ReActAgentEvolve(AgentCard card)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configure(Object configObj)` | `BaseAgent` | 更新配置并按需重置 Operator |
| `getConfig()` | `Object` | 获取 `ReActAgentConfig` |
| `getContextEngine()` | `ContextEngine` | 获取上下文引擎 |
| `getOperators()` | `Map<String, Operator>` | 获取当前可自演进 Operator 注册表 |
| `invoke(Object inputs, Session session)` | `Object` | 执行自演进版本 ReAct 循环 |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 当前实现为 invoke 后回放 Session 流 |
| `registerSkill(Object skillPath)` | `void` | 注册本地技能路径 |

---

## 3. Rail 回调体系

### 3.1 AgentCallbackEvent

Agent 生命周期回调事件枚举。

**包路径**：`com.openjiuwen.core.singleagent.rail`

| 枚举值 | 说明 |
|--------|------|
| `BEFORE_INVOKE` / `AFTER_INVOKE` | Agent 调用前后 |
| `BEFORE_MODEL_CALL` / `AFTER_MODEL_CALL` / `ON_MODEL_EXCEPTION` | 模型调用生命周期 |
| `BEFORE_TOOL_CALL` / `AFTER_TOOL_CALL` / `ON_TOOL_EXCEPTION` | 工具调用生命周期 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `String` | 获取事件字符串值 |
| `toString()` | `String` | 返回事件值 |

### 3.2 AgentCallbackContext

统一 Rail 上下文对象，跨生命周期阶段传递 Agent、输入、Session、ModelContext 与异常信息。

**包路径**：`com.openjiuwen.core.singleagent.rail`

| 字段 | 类型 | 说明 |
|------|------|------|
| `agent` | `Object` | 当前 Agent |
| `event` | `AgentCallbackEvent` | 当前事件 |
| `inputs` | `EventInputs` | 当前阶段输入 |
| `config` | `Object` | Agent 配置 |
| `session` | `Session` | Session |
| `context` | `ModelContext` | 模型上下文 |
| `extra` | `Map<String, Object>` | 跨 Rail 共享数据 |
| `exception` | `Exception` | 异常对象 |
| `retryAttempt` | `int` | 当前重试次数 |
| `retryRequest` | `RetryRequest` | 延迟重试请求 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fire(AgentCallbackEvent event)` | `void` | 触发指定事件 |
| `requestRetry(double delaySeconds)` | `void` | 请求执行体重试 |
| `consumeRetryRequest()` | `RetryRequest` | 读取并清空待处理重试请求 |

### 3.3 AgentRail

Rail 基类，支持类式生命周期 Hook、优先级与附带工具。

**包路径**：`com.openjiuwen.core.singleagent.rail`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getPriority()` / `setPriority(int priority)` | `int` / `void` | 读写执行优先级 |
| `getTools()` | `List<ToolCard>` | 获取 Rail 附带工具 |
| `beforeInvoke(...)`, `afterInvoke(...)`, `beforeModelCall(...)`, `afterModelCall(...)`, `onModelException(...)`, `beforeToolCall(...)`, `afterToolCall(...)`, `onToolException(...)` | `void` | 八个可重写 Hook |
| `getCallbacks()` | `Map<AgentCallbackEvent, Consumer<AgentCallbackContext>>` | 提取子类真正重写的 Hook |

### 3.4 RailExecutor

Rail 执行工具类，替代 Python 中的 `@rail` 装饰器。

**包路径**：`com.openjiuwen.core.singleagent.rail`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `execute(AgentCallbackContext ctx, AgentCallbackEvent before, AgentCallbackEvent after, AgentCallbackEvent onException, RailBody<T> body)` | `<T> T` | 按 before/after/onException 生命周期包裹执行体，并支持重试 |

### 3.5 其他 Rail 类型

**包路径**：`com.openjiuwen.core.singleagent.rail`

| 类型 | 说明 |
|------|------|
| `AgentCallback` | `Consumer<AgentCallbackContext>` 的函数式别名 |
| `AgentCallbackFirer` | 可触发 Agent 回调事件的对象接口 |
| `EventInputs` | 输入模型标记接口 |
| `InvokeInputs` | invoke 生命周期输入：`query`、`conversationId`、`result` |
| `ModelCallInputs` | 模型调用输入：`messages`、`tools`、`response` |
| `ToolCallInputs` | 工具调用输入：`toolCall`、`toolName`、`toolArgs`、`toolResult`、`toolMsg` |
| `RetryRequest` | 重试请求对象，字段 `delaySeconds` |

---

## 4. Schema 模型

### 4.1 AgentCard

Agent 卡片定义，同时可导出为 `ToolInfo`，使 Agent 以工具形态暴露给模型或其他 Agent。

**包路径**：`com.openjiuwen.core.singleagent.schema`  
**继承**：`BaseCard`

| 字段 | 类型 | 说明 |
|------|------|------|
| `inputParams` | `Map<String, Object>` | 输入 Schema |
| `outputParams` | `Map<String, Object>` | 输出 Schema |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toolInfo()` | `Object` | 构建 `ToolInfo`，参数来自 `inputParams` |

### 4.2 AgentResult

Agent 执行结果模型。

**包路径**：`com.openjiuwen.core.singleagent.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `taskId` | `String` | - | 任务 ID |
| `sessionId` | `String` | - | 会话 ID |
| `status` | `TaskStatus` | - | 任务状态 |
| `artifacts` | `List<Artifact>` | 空列表 | 产物列表 |
| `metadata` | `Map<String, Object>` | 空映射 | 元数据 |

### 4.3 Artifact

Agent 产物模型。

**包路径**：`com.openjiuwen.core.singleagent.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `artifactId` | `String` | - | 产物 ID |
| `name` | `String` | - | 名称 |
| `description` | `String` | - | 描述 |
| `parts` | `List<Part>` | 空列表 | 片段列表 |
| `metadata` | `Map<String, Object>` | 空映射 | 附加元数据 |

---

## 5. Skills 子模块

### 5.1 Skill / GitHubTree

技能元数据与 GitHub 目录引用模型。

**包路径**：`com.openjiuwen.core.singleagent.skills`

| 类型 | 关键字段/方法 | 说明 |
|------|---------------|------|
| `Skill` | `name`, `description`, `directory`, `toString()` | 单个技能元数据 |
| `GitHubTree` | `repoOwner`, `repoName`, `treeRef`, `directory`, `copy()` | GitHub 仓库树引用 |

### 5.2 SkillManager

本地技能注册管理器，负责从 `SKILL.md`/`Skill.md` 读取描述并维护技能注册表。

**包路径**：`com.openjiuwen.core.singleagent.skills`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `SkillManager(String sysOperationId)` | 构造 | 创建技能管理器 |
| `setSysOperationId(String sysOperationId)` / `getSysOperationId()` | `void` / `String` | 读写系统操作 ID |
| `register(String skillPath, String sessionId, boolean overwrite)` | `void` | 从路径注册技能 |
| `register(String skillPath)` | `void` | 简化注册 |
| `unregister(String name)` | `void` | 注销技能 |
| `get(String name)` | `Skill` | 获取技能 |
| `getAll()` | `List<Skill>` | 获取全部技能 |
| `getNames()` | `List<String>` | 获取全部技能名 |
| `has(String name)` | `boolean` | 判断技能是否存在 |
| `clear()` | `void` | 清空注册表 |
| `count()` | `int` | 技能数量 |
| `getDescription()` / `setDescription(String description)` | `String` / `void` | 读写额外描述 |

### 5.3 SkillUtil

面向 Agent 的高层技能工具，组合 `SkillManager` 与 `RemoteSkillUtil`。

**包路径**：`com.openjiuwen.core.singleagent.skills`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `SkillUtil(String sysOperationId)` | 构造 | 创建技能工具 |
| `setSysOperationId(String sysOperationId)` | `void` | 同步更新本地/远端技能工具的系统操作 ID |
| `getSkillManager()` | `SkillManager` | 获取本地技能管理器 |
| `getRemoteSkillUtil()` | `RemoteSkillUtil` | 获取远端技能工具 |
| `registerSkills(Object skillPath, BaseAgent agent)` | `void` | 注册本地技能路径（支持 `String` 或 `List<String>`） |
| `registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token)` | `void` | 从 GitHub 下载技能到本地目录 |
| `hasSkill()` | `boolean` | 是否已有技能 |
| `getSkillPrompt()` | `String` | 生成面向模型的技能提示词 |

### 5.4 RemoteSkillUtil

远端技能下载工具，负责通过 GitHub API 查找并下载技能目录。

**包路径**：`com.openjiuwen.core.singleagent.skills`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `RemoteSkillUtil(String sysOperationId)` | 构造 | 创建远端技能工具 |
| `getSysOperationId()` / `setSysOperationId(String sysOperationId)` | `String` / `void` | 读写系统操作 ID |
| `downloadFileFromGitHub(GitHubTree tree, String filePath, String token)` | `byte[]` | 下载 GitHub 单个文件（静态方法） |
| `uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token)` | `List<String>` | 下载仓库中包含 `SKILL.md` 的技能目录到本地 |
