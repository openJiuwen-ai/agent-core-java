# Application 模块 API 文档

> 包路径：`com.openjiuwen.core.application`

Application 模块位于框架应用层，提供开箱即用的 Agent 封装。当前实现包含两条主线：一类是基于 ReAct 规划与任务执行的 `LlmAgent`，另一类是面向预定义工作流路由的 `WorkflowAgent`。模块同时提供事件处理器和一组应用层配置 Schema。

---

## 目录

- [1. Agent 主入口](#1-agent-主入口)
- [2. 事件处理器](#2-事件处理器)
- [3. Schema 配置模型](#3-schema-配置模型)

---

## 1. Agent 主入口

### 1.1 LlmAgent

基于 `ControllerAgent` 的应用层 Agent，负责创建默认会话、驱动 `LlmEventHandler`，并在启用时将问答消息异步写入长期记忆。

**包路径**：`com.openjiuwen.core.application.llm`  
**继承**：`ControllerAgent`

**构造方法**
```java
LlmAgent(LlmAgentConfig agentConfig)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, Session session)` | `ControllerOutput` | 同步执行，`session == null` 时自动创建 `AgentSessionApi` |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行，并在流结束后触发 `postRun()` 与记忆写入 |
| `setPromptTemplate(List<Map<String, String>> promptTemplate)` | `void` | 更新提示词模板，并同步给 `LlmEventHandler` |
| `getAgentConfig()` | `LlmAgentConfig` | 获取当前 Agent 配置 |

### 1.2 WorkflowAgent

基于 `ControllerAgent` 的工作流应用层 Agent，负责装配 `WorkflowEventHandler`，将输入路由到预配置工作流。

**包路径**：`com.openjiuwen.core.application.workflow`  
**继承**：`ControllerAgent`

**构造方法**
```java
WorkflowAgent(WorkflowAgentConfig agentConfig)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object inputs, Session session)` | `ControllerOutput` | 同步执行工作流型 Agent，缺省自动创建 Session |
| `stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式执行，内部负责补齐 `preRun()/postRun()` |
| `getAgentConfig()` | `WorkflowAgentConfig` | 获取当前工作流 Agent 配置 |

---

## 2. 事件处理器

### 2.1 LlmEventHandler

`LlmAgent` 默认使用的事件处理器，实现 ReAct 风格的“规划 -> 执行任务 -> 再规划”循环，并支持工作流中断恢复。

**包路径**：`com.openjiuwen.core.application.llm`  
**继承**：`EventHandler`

**构造方法**
```java
LlmEventHandler(LlmAgentConfig agentConfig, ContextEngine contextEngine)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | 处理用户输入，生成规划、执行任务、回写流式输出 |
| `handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务交互事件，当前实现返回 `null` |
| `handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务完成事件，当前实现返回 `null` |
| `handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务失败事件，当前实现返回 `null` |
| `setPromptTemplate(List<Map<String, String>> promptTemplate)` | `void` | 动态更新提示词模板 |

**行为说明**

- 使用 `ContextEngine.createContext()` 管理会话上下文。
- 根据 `Task.taskType` 执行工作流任务或插件任务。
- 通过 Session `state["llm_controller"]` 持久化被中断工作流的剩余任务、迭代次数与组件 ID。
- 当工具/工作流产出交互块时，返回首个中断块，并等待后续 `InteractiveInput` 恢复执行。

### 2.2 WorkflowEventHandler

`WorkflowAgent` 默认使用的事件处理器，负责工作流选择、恢复中断任务、工作流流式执行与结果包装。

**包路径**：`com.openjiuwen.core.application.workflow`  
**继承**：`EventHandler`

**构造方法**
```java
WorkflowEventHandler(WorkflowAgentConfig agentConfig, ContextEngine contextEngine)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | 主入口：选择工作流、执行工作流或恢复中断 |
| `handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | 处理交互事件，当前实现返回 `null` |
| `handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | 处理完成事件，当前实现返回 `null` |
| `handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | 处理失败事件，当前实现返回 `null` |

**行为说明**

- 单工作流模式下直接选择唯一工作流。
- 多工作流模式下当前 `detectWorkflowViaLlm()` 默认回退到首个工作流。
- 若 Session 中存在中断记录，则可直接恢复被中断节点或回放上次交互输出。
- 中断状态通过 `state["workflow_controller"]` 保存 `task`、`component_id` 与最近一次交互值。

### 2.3 TaskInterruptionState

任务中断状态对象，聚合被中断任务、当前 Session、AI 输出、剩余任务列表以及交互数据。

**包路径**：`com.openjiuwen.core.application.llm`

**构造方法**
```java
TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage, List<Task> remainingTasks)
TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage,
                      List<Task> remainingTasks, List<Object> interactionData, Integer currentIteration)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getTask()` | `Task` | 获取当前被中断任务 |
| `getSession()` | `AgentSessionApi` | 获取会话对象 |
| `getAiMessage()` | `AssistantMessage` | 获取中断前的 AI 消息 |
| `getRemainingTasks()` | `List<Task>` | 获取剩余任务列表 |
| `getInteractionData()` / `setInteractionData(...)` | `List<Object>` / `void` | 读写交互块数据 |
| `getCurrentIteration()` / `setCurrentIteration(...)` | `Integer` / `void` | 读写当前迭代次数 |

---

## 3. Schema 配置模型

### 3.1 LlmAgentConfig

`LlmAgent` 配置对象，组合模型配置、工作流/插件清单、提示词模板、约束与上下文配置。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `id` | `String` | - | Agent 标识 |
| `version` | `String` | `"1.0"` | 版本号 |
| `description` | `String` | `""` | 描述 |
| `workflows` | `List<WorkflowSchema>` | 空列表 | 可调用工作流列表 |
| `plugins` | `List<PluginSchema>` | 空列表 | 可调用插件列表 |
| `model` | `ModelConfig` | - | 模型配置 |
| `promptTemplate` | `List<Map<String, String>>` | 空列表 | 提示词模板 |
| `tools` | `List<String>` | 空列表 | 附加工具名列表 |
| `memoryScopeId` | `String` | `""` | 记忆域 ID |
| `agentMemoryConfig` | `com.openjiuwen.core.memory.config.AgentMemoryConfig` | 默认构造 | 记忆引擎配置 |
| `constrain` | `ConstrainConfig` | 默认构造 | ReAct 循环限制 |
| `contextEngineConfig` | `ContextEngineConfig` | - | 上下文引擎配置 |
| `defaultResponse` | `DefaultResponse` | - | 默认兜底响应 |

**内部类**

| 类型 | 字段/方法 | 说明 |
|------|-----------|------|
| `ConstrainConfig` | `maxIteration` | 最大迭代次数，默认 5 |

### 3.2 WorkflowAgentConfig

`WorkflowAgent` 配置对象，描述可调度工作流、默认响应和上下文引擎配置。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `id` | `String` | - | Agent 标识 |
| `version` | `String` | `"1.0"` | 版本号 |
| `description` | `String` | `""` | 描述 |
| `workflows` | `List<WorkflowSchema>` | 空列表 | 可调度工作流列表 |
| `defaultResponse` | `DefaultResponse` | - | 未匹配工作流时的默认文本 |
| `contextEngineConfig` | `ContextEngineConfig` | - | 上下文引擎配置 |

### 3.3 WorkflowSchema

工作流引用模型，用于在应用层 Agent 中声明一个可执行工作流。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `id` | `String` | - | 工作流 ID |
| `name` | `String` | - | 工作流显示名 |
| `version` | `String` | `"1.0"` | 工作流版本 |
| `description` | `String` | - | 工作流描述 |
| `inputParams` | `Map<String, Object>` | - | 输入 Schema |

### 3.4 PluginSchema

插件引用模型，描述应用层 Agent 可调用的插件。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 插件 ID |
| `name` | `String` | 插件名称 |
| `description` | `String` | 插件描述 |

### 3.5 DefaultResponse

默认响应配置，用于未命中工作流或意图识别失败时的兜底输出。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `text` | `String` | `""` | 默认返回文本 |

### 3.6 AgentMemoryConfig

应用层记忆配置兼容模型，提供长期记忆、片段记忆、摘要记忆与变量记忆开关。

**包路径**：`com.openjiuwen.core.application.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enableLongTermMem` | `boolean` | `false` | 是否启用长期记忆 |
| `enableFragmentMemory` | `boolean` | `false` | 是否启用片段记忆 |
| `enableSummaryMemory` | `boolean` | `false` | 是否启用摘要记忆 |
| `memVariables` | `List<MemVariable>` | 空列表 | 可提取的变量记忆描述 |

**内部类**

| 类型 | 字段 | 说明 |
|------|------|------|
| `MemVariable` | `name`, `description` | 单个记忆变量定义 |

**备注**

- 当前 `LlmAgentConfig.agentMemoryConfig` 字段实际引用的是 `memory` 模块中的 `AgentMemoryConfig`。
- 本类型仍保留在 `application.schema` 下，可视为应用层兼容配置模型。
