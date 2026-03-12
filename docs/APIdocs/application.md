# Application 模块 API 文档

> 包路径：`com.openjiuwen.core.application`

应用层 Agent 封装、事件处理器与配置模型。基于 `application` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `13` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.application.llm` | 3 |
| `com.openjiuwen.core.application.schema` | 8 |
| `com.openjiuwen.core.application.workflow` | 2 |

## `com.openjiuwen.core.application.llm`

公开类型：`3`

### `LlmAgent`

- 类型：`class`
- 声明：`public class LlmAgent extends ControllerAgent`
- 说明：LLM Agent - ReAct style Agent based on ControllerAgent.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LlmAgent(LlmAgentConfig agentConfig)` | Create LlmAgent with the given configuration. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerOutput invoke(Object inputs, Session session)` | `ControllerOutput` | - |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | - |
| `public void setPromptTemplate(List<Map<String, String>> promptTemplate)` | `void` | Set prompt template and propagate to controller. |
| `public LlmAgentConfig getAgentConfig()` | `LlmAgentConfig` | - |

### `LlmEventHandler`

- 类型：`class`
- 声明：`public class LlmEventHandler extends EventHandler`
- 说明：LLM Controller - ReAct style event handler based on EventHandler.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LlmEventHandler(LlmAgentConfig agentConfig, ContextEngine contextEngine)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public void setPromptTemplate(List<Map<String, String>> promptTemplate)` | `void` | Set prompt template on the config. |

### `TaskInterruptionState`

- 类型：`class`
- 声明：`public class TaskInterruptionState`
- 说明：Encapsulates all data related to task interruption.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `task` | `Task` | `private final` | `-` | - |
| `session` | `AgentSessionApi` | `private final` | `-` | - |
| `aiMessage` | `AssistantMessage` | `private final` | `-` | - |
| `remainingTasks` | `List<Task>` | `private final` | `-` | - |
| `interactionData` | `List<Object>` | `private` | `-` | - |
| `currentIteration` | `Integer` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage, List<Task> remainingTasks)` | - |
| `public TaskInterruptionState(Task task, AgentSessionApi session, AssistantMessage aiMessage, List<Task> remainingTasks, List<Object> interactionData, Integer currentIteration)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Task getTask()` | `Task` | - |
| `public AgentSessionApi getSession()` | `AgentSessionApi` | - |
| `public AssistantMessage getAiMessage()` | `AssistantMessage` | - |
| `public List<Task> getRemainingTasks()` | `List<Task>` | - |
| `public List<Object> getInteractionData()` | `List<Object>` | - |
| `public void setInteractionData(List<Object> interactionData)` | `void` | - |
| `public Integer getCurrentIteration()` | `Integer` | - |
| `public void setCurrentIteration(Integer currentIteration)` | `void` | - |

## `com.openjiuwen.core.application.schema`

公开类型：`8`

### `AgentMemoryConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class AgentMemoryConfig`
- 说明：Memory configuration for application agents.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 嵌套公开类型：`AgentMemoryConfig.MemVariable`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `enableLongTermMem` | `boolean` | `private` | `false` | - |
| `enableFragmentMemory` | `boolean` | `private` | `false` | - |
| `enableSummaryMemory` | `boolean` | `private` | `false` | - |
| `memVariables` | `List<MemVariable>` | `private` | `new ArrayList<>()` | - |

### `AgentMemoryConfig.MemVariable`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public static class MemVariable`
- 说明：Memory variable definition.
- 宿主类型：`AgentMemoryConfig`
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |

### `DefaultResponse`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DefaultResponse`
- 说明：Default response configuration for agents.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `text` | `String` | `private` | `""` | - |

### `LlmAgentConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class LlmAgentConfig`
- 说明：Configuration for LLM-based agent in the application layer.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 嵌套公开类型：`LlmAgentConfig.ConstrainConfig`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `version` | `String` | `private` | `"1.0"` | - |
| `description` | `String` | `private` | `""` | - |
| `workflows` | `List<WorkflowSchema>` | `private` | `new ArrayList<>()` | - |
| `plugins` | `List<PluginSchema>` | `private` | `new ArrayList<>()` | - |
| `model` | `ModelConfig` | `private` | `-` | - |
| `promptTemplate` | `List<Map<String, String>>` | `private` | `new ArrayList<>()` | - |
| `tools` | `List<String>` | `private` | `new ArrayList<>()` | - |
| `memoryScopeId` | `String` | `private` | `""` | - |
| `agentMemoryConfig` | `AgentMemoryConfig` | `private` | `AgentMemoryConfig.builder().build()` | - |
| `constrain` | `ConstrainConfig` | `private` | `new ConstrainConfig()` | - |
| `contextEngineConfig` | `ContextEngineConfig` | `private` | `-` | - |
| `defaultResponse` | `DefaultResponse` | `private` | `-` | - |

### `LlmAgentConfig.ConstrainConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public static class ConstrainConfig`
- 说明：Constraint configuration for ReAct loop.
- 宿主类型：`LlmAgentConfig`
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `maxIteration` | `int` | `private` | `5` | - |

### `PluginSchema`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class PluginSchema`
- 说明：Schema describing a plugin reference in agent configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `name` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |

### `WorkflowAgentConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class WorkflowAgentConfig`
- 说明：Configuration for workflow-based agent in the application layer.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `version` | `String` | `private` | `"1.0"` | - |
| `description` | `String` | `private` | `""` | - |
| `workflows` | `List<WorkflowSchema>` | `private` | `new ArrayList<>()` | - |
| `defaultResponse` | `DefaultResponse` | `private` | `-` | - |
| `contextEngineConfig` | `ContextEngineConfig` | `private` | `-` | - |

### `WorkflowSchema`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class WorkflowSchema`
- 说明：Schema describing a workflow reference in agent configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `name` | `String` | `private` | `-` | - |
| `version` | `String` | `private` | `"1.0"` | - |
| `description` | `String` | `private` | `-` | - |
| `inputParams` | `Map<String, Object>` | `private` | `-` | - |

## `com.openjiuwen.core.application.workflow`

公开类型：`2`

### `WorkflowAgent`

- 类型：`class`
- 声明：`public class WorkflowAgent extends ControllerAgent`
- 说明：Workflow-based Agent - Executes predefined workflows with multi-workflow controller.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowAgent(WorkflowAgentConfig agentConfig)` | Create WorkflowAgent with the given configuration. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerOutput invoke(Object inputs, Session session)` | `ControllerOutput` | - |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | - |
| `public WorkflowAgentConfig getAgentConfig()` | `WorkflowAgentConfig` | - |

### `WorkflowEventHandler`

- 类型：`class`
- 声明：`public class WorkflowEventHandler extends EventHandler`
- 说明：Workflow Controller - Implements workflow-specific execution logic.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowEventHandler(WorkflowAgentConfig agentConfig, ContextEngine contextEngine)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | - |

