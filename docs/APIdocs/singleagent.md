# SingleAgent 模块 API 文档

> 包路径：`com.openjiuwen.core.singleagent`

单智能体基类、ReAct Agent、技能管理与 Rail 回调体系。基于 `singleagent` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `40` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.singleagent` | 6 |
| `com.openjiuwen.core.singleagent.agents` | 3 |
| `com.openjiuwen.core.singleagent.legacy` | 4 |
| `com.openjiuwen.core.singleagent.legacy.config` | 5 |
| `com.openjiuwen.core.singleagent.legacy.schema` | 2 |
| `com.openjiuwen.core.singleagent.rail` | 12 |
| `com.openjiuwen.core.singleagent.schema` | 3 |
| `com.openjiuwen.core.singleagent.skills` | 5 |

## `com.openjiuwen.core.singleagent`

公开类型：`6`

### `AbilityExecutionError`

- 类型：`class`
- 声明：`public class AbilityExecutionError extends AgentError`
- 说明：Unified exception for ability execution failures.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AbilityExecutionError(StatusCode status, String msg, ToolMessage toolMessage)` | - |
| `public AbilityExecutionError(StatusCode status, String msg, Throwable cause, ToolMessage toolMessage)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ToolMessage getToolMessage()` | `ToolMessage` | - |

### `AbilityManager`

- 类型：`class`
- 声明：`public class AbilityManager implements ToolRegistry`
- 说明：Agent Ability Manager.
- 嵌套公开类型：`AbilityManager.ToolExecutionEntry`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void add(Object ability)` | `void` | Add an ability. |
| `public Object remove(String name)` | `Object` | Remove an ability by name. |
| `public List<Object> remove(List<String> names)` | `List<Object>` | Remove abilities by name list. |
| `public Object get(String name)` | `Object` | Get an ability Card by name. |
| `public List<Object> list()` | `List<Object>` | List all ability Cards. |
| `public List<ToolInfo> listToolInfo()` | `List<ToolInfo>` | Get ToolInfo list (for LLM usage). |
| `public List<ToolInfo> listToolInfo(List<String> names, String mcpServerName)` | `List<ToolInfo>` | Get ToolInfo list (for LLM usage) with optional name/server filtering. |
| `public void setToolDescription(String toolName, String description)` | `void` | - |
| `public ToolExecutionResult executeAsToolExecutor(Object toolCallObj, Session session)` | `ToolExecutionResult` | Execute a single tool call for use as a ToolExecutor. |
| `public List<ToolExecutionEntry> execute(AgentCallbackContext ctx, Object toolCall, Session session, String tag)` | `List<ToolExecutionEntry>` | Execute ability call(s) with per-tool rail hooks. |
| `public ToolExecutionEntry executeSingleToolCall(ToolCall toolCall, Session session, String tag)` | `ToolExecutionEntry` | Execute a single tool call by dispatching to the appropriate handler. |

### `AbilityManager.ToolExecutionEntry`

- 类型：`record`
- 声明：`public record ToolExecutionEntry(Object result, ToolMessage toolMessage)`
- 说明：Result entry from tool execution.
- 宿主类型：`AbilityManager`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `result` | `Object` | `private final` | `-` | - |
| `toolMessage` | `ToolMessage` | `private final` | `-` | - |

### `AgentCallbackManager`

- 类型：`class`
- 声明：`public class AgentCallbackManager`
- 说明：Manager for agent callback/rail registration and execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentCallbackManager(String agentId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | `void` | Register an agent callback for an event. |
| `public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | `void` | Register an agent callback with default priority. |
| `public void registerRail(AgentRail rail, Object agent)` | `void` | Register a rail instance. |
| `public void unregisterRail(AgentRail rail, Object agent)` | `void` | Unregister a rail instance. |
| `public void unregister(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | `void` | Unregister a callback from an event. |
| `public void clear(AgentCallbackEvent event)` | `void` | Clear hooks for a specific event or all events. |
| `public boolean hasHooks(AgentCallbackEvent event)` | `boolean` | Check if any hooks are registered for an event. |
| `public void execute(AgentCallbackEvent event, AgentCallbackContext ctx)` | `void` | Execute all hooks for an event. |

### `BaseAgent`

- 类型：`class`
- 声明：`public abstract class BaseAgent implements AgentCallbackFirer`
- 说明：Single Agent Base Class.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseAgent(AgentCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void lazyInitSkill()` | `void` | Lazy init SkillUtil. |
| `protected String getSysOperationId(Object config)` | `String` | Extract sys_operation_id from config via reflection. |
| `public abstract BaseAgent configure(Object config)` | `BaseAgent` | Set configuration. |
| `public abstract Object getConfig()` | `Object` | Get current configuration. |
| `public AgentCard getCard()` | `AgentCard` | - |
| `public AbilityManager getAbilityManager()` | `AbilityManager` | - |
| `public AgentCallbackManager getAgentCallbackManager()` | `AgentCallbackManager` | - |
| `public SkillUtil getSkillUtil()` | `SkillUtil` | - |
| `protected void setSkillUtil(SkillUtil skillUtil)` | `void` | - |
| `public BaseAgent registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | `BaseAgent` | Register a callback for an event. |
| `public BaseAgent registerRail(AgentRail rail)` | `BaseAgent` | Register a rail instance. |
| `public BaseAgent unregisterRail(AgentRail rail)` | `BaseAgent` | Unregister a rail instance. |
| `public void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx)` | `void` | - |
| `public abstract Object invoke(Object inputs, Session session)` | `Object` | Batch execution. |
| `public abstract Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | Stream execution. |

### `ControllerAgent`

- 类型：`class`
- 声明：`public class ControllerAgent extends BaseAgent`
- 说明：Controller-based Agent implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerAgent(AgentCard card, Controller controller)` | - |
| `public ControllerAgent(AgentCard card, Controller controller, ControllerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseAgent configure(Object config)` | `BaseAgent` | - |
| `public Object getConfig()` | `Object` | - |
| `public Controller getController()` | `Controller` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `public void releaseSession(String sessionId)` | `void` | Release session resources. |
| `public ControllerOutput invoke(Object inputs, Session session)` | `ControllerOutput` | - |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | - |

## `com.openjiuwen.core.singleagent.agents`

公开类型：`3`

### `ReActAgent`

- 类型：`class`
- 声明：`public class ReActAgent extends BaseAgent`
- 说明：ReAct paradigm Agent implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReActAgent(AgentCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected ReActAgentConfig createDefaultConfig()` | `ReActAgentConfig` | - |
| `public BaseAgent configure(Object configObj)` | `BaseAgent` | - |
| `public Object getConfig()` | `Object` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `protected Model getLlm()` | `Model` | Get LLM instance (lazy initialization). |
| `public Object invoke(Object inputs, Session session)` | `Object` | - |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | - |

### `ReActAgentConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ReActAgentConfig`
- 说明：ReActAgent Configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memScopeId` | `String` | `private` | `""` | - |
| `modelName` | `String` | `private` | `""` | - |
| `modelProvider` | `String` | `private` | `"openai"` | - |
| `apiKey` | `String` | `private` | `""` | - |
| `apiBase` | `String` | `private` | `""` | - |
| `promptTemplateName` | `String` | `private` | `""` | - |
| `promptTemplate` | `List<Map<String, String>>` | `private` | `new ArrayList<>()` | - |
| `maxIterations` | `int` | `private` | `5` | - |
| `modelClientConfig` | `ModelClientConfig` | `private` | `-` | - |
| `modelConfigObj` | `ModelRequestConfig` | `private` | `-` | - |
| `sysOperationId` | `String` | `private` | `-` | - |
| `contextEngineConfig` | `ContextEngineConfig` | `private` | `ContextEngineConfig.builder().maxContextMessageNum(200).defaultWindowRoundNum(10).build()` | - |
| `contextProcessors` | `List<Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ReActAgentConfig configureModel(String modelName)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configurePrompt(String promptName)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configurePromptTemplate(List<Map<String, String>> promptTemplate)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureContextEngine(Integer maxContextMessageNum, Integer defaultWindowRoundNum, boolean enableReload)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureMemScope(String memScopeId)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureMaxIterations(int maxIterations)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureModelClient(String provider, String apiKey, String apiBase, String modelName, boolean verifySsl)` | `ReActAgentConfig` | - |
| `public ReActAgentConfig configureContextProcessors(List<Object> processors)` | `ReActAgentConfig` | - |

### `ReActAgentEvolve`

- 类型：`class`
- 声明：`public class ReActAgentEvolve extends BaseAgent`
- 说明：ReAct paradigm Agent with self-evolving operators.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReActAgentEvolve(AgentCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected ReActAgentConfig createDefaultConfig()` | `ReActAgentConfig` | - |
| `public BaseAgent configure(Object configObj)` | `BaseAgent` | - |
| `public Object getConfig()` | `Object` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `protected Model getLlm()` | `Model` | Get LLM instance (lazy initialization). |
| `public Map<String, Operator> getOperators()` | `Map<String, Operator>` | Return evolvable operator registry. |
| `public Object invoke(Object inputs, Session session)` | `Object` | - |
| `public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | `Iterator<Object>` | - |
| `public void registerSkill(Object skillPath)` | `void` | Register a skill. |

## `com.openjiuwen.core.singleagent.legacy`

公开类型：`4`

### `BaseAgent`

- 类型：`class`
- 声明：`public abstract class BaseAgent`
- 说明：Legacy single-agent base class.
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agentConfig` | `AgentConfig` | `protected final` | `-` | - |
| `tools` | `List<Tool>` | `protected final` | `new ArrayList<>()` | - |
| `workflows` | `List<Workflow>` | `protected final` | `new ArrayList<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseAgent(AgentConfig agentConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public AgentConfig getAgentConfig()` | `AgentConfig` | - |
| `public void addTools(List<Tool> newTools)` | `void` | - |
| `public void addWorkflows(List<Workflow> newWorkflows)` | `void` | - |
| `public void clearSession(String sessionId)` | `void` | - |
| `public abstract Object invoke(Map<String, Object> inputs, Session session)` | `Object` | - |
| `public abstract Iterator<Object> stream(Map<String, Object> inputs, Session session)` | `Iterator<Object>` | - |

### `ControllerAgent`

- 类型：`class`
- 声明：`public class ControllerAgent extends BaseAgent`
- 说明：Legacy controller-driven agent wrapper.
- 兼容性：`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerAgent(AgentConfig agentConfig, BaseController controller)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseController getController()` | `BaseController` | - |
| `public void setController(BaseController controller)` | `void` | - |
| `public Object invoke(Map<String, Object> inputs, Session session)` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Session session)` | `Iterator<Object>` | - |

### `LegacyReActAgent`

- 类型：`class`
- 声明：`public class LegacyReActAgent extends BaseAgent`
- 说明：Backward-compatible wrapper over the modern ReActAgent.
- 兼容性：`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LegacyReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools)` | - |
| `public LegacyReActAgent(LegacyReActAgentConfig agentConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addTools(List<Tool> newTools)` | `void` | - |
| `public void addWorkflows(List<Workflow> newWorkflows)` | `void` | - |
| `public Object invoke(Map<String, Object> inputs, Session session)` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Session session)` | `Iterator<Object>` | - |
| `public static LegacyReActAgentConfig createReActAgentConfig(String agentId, String agentVersion, String description, ModelConfig model, List<Map<String, String>> promptTemplate)` | `LegacyReActAgentConfig` | - |

### `ReActAgent`

- 类型：`class`
- 声明：`public class ReActAgent extends LegacyReActAgent`
- 说明：Alias for the legacy ReActAgent wrapper.
- 兼容性：`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools)` | - |
| `public ReActAgent(LegacyReActAgentConfig agentConfig)` | - |

## `com.openjiuwen.core.singleagent.legacy.config`

公开类型：`5`

### `AgentConfig`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor public class AgentConfig`
- 说明：Legacy agent configuration.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `""` | - |
| `version` | `String` | `private` | `""` | - |
| `description` | `String` | `private` | `""` | - |
| `controllerType` | `ControllerType` | `private` | `ControllerType.UNDEFINED` | - |
| `workflows` | `List<WorkflowSchema>` | `private` | `new ArrayList<>()` | - |
| `model` | `ModelConfig` | `private` | `-` | - |
| `tools` | `List<String>` | `private` | `new ArrayList<>()` | - |

### `ConstrainConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ConstrainConfig`
- 说明：Legacy constraint configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `reservedMaxChatRounds` | `int` | `private` | `10` | - |
| `maxIteration` | `int` | `private` | `5` | - |

### `DefaultResponse`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DefaultResponse`
- 说明：Default response configuration for legacy workflow agents.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `"text"` | - |
| `text` | `String` | `private` | `-` | - |

### `LegacyReActAgentConfig`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class LegacyReActAgentConfig extends AgentConfig`
- 说明：Legacy ReAct agent configuration.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `controllerType` | `ControllerType` | `private` | `ControllerType.REACT_CONTROLLER` | - |
| `promptTemplateName` | `String` | `private` | `"react_system_prompt"` | - |
| `promptTemplate` | `List<Map<String, String>>` | `private` | `new ArrayList<>()` | - |
| `constrain` | `ConstrainConfig` | `private` | `ConstrainConfig.builder().build()` | - |
| `plugins` | `List<PluginSchema>` | `private` | `new ArrayList<>()` | - |
| `memoryScopeId` | `String` | `private` | `""` | - |
| `agentMemoryConfig` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getContextWindowLimit()` | `int` | - |

### `WorkflowAgentConfig`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class WorkflowAgentConfig extends AgentConfig`
- 说明：Legacy workflow-agent configuration.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `controllerType` | `ControllerType` | `private` | `ControllerType.WORKFLOW_CONTROLLER` | - |
| `startWorkflow` | `WorkflowSchema` | `private` | `new WorkflowSchema()` | - |
| `endWorkflow` | `WorkflowSchema` | `private` | `new WorkflowSchema()` | - |
| `globalVariables` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `globalParams` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `constrain` | `ConstrainConfig` | `private` | `ConstrainConfig.builder().build()` | - |
| `defaultResponse` | `DefaultResponse` | `private` | `DefaultResponse.builder().build()` | - |

## `com.openjiuwen.core.singleagent.legacy.schema`

公开类型：`2`

### `PluginSchema`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class PluginSchema`
- 说明：Legacy plugin schema for backward compatibility.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `""` | - |
| `version` | `String` | `private` | `""` | - |
| `name` | `String` | `private` | `""` | - |
| `description` | `String` | `private` | `""` | - |
| `inputs` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `pluginId` | `String` | `private` | `""` | - |

### `WorkflowSchema`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class WorkflowSchema`
- 说明：Legacy workflow schema for backward compatibility.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `""` | - |
| `name` | `String` | `private` | `""` | - |
| `description` | `String` | `private` | `""` | - |
| `version` | `String` | `private` | `""` | - |
| `inputs` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

## `com.openjiuwen.core.singleagent.rail`

公开类型：`12`

### `AgentCallback`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface AgentCallback extends Consumer<AgentCallbackContext>`
- 说明：Functional interface for agent callback.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `AgentCallbackContext`

- 类型：`class`
- 声明：`@Data @Builder public class AgentCallbackContext`
- 说明：Unified context object passed to rail/callback hooks.
- 注解：`@Data`、`@Builder`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agent` | `Object` | `private` | `-` | - |
| `event` | `AgentCallbackEvent` | `private` | `-` | - |
| `inputs` | `EventInputs` | `private` | `null` | - |
| `config` | `Object` | `private` | `-` | - |
| `session` | `Session` | `private` | `-` | - |
| `context` | `ModelContext` | `private` | `-` | - |
| `extra` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |
| `exception` | `Exception` | `private` | `-` | - |
| `retryAttempt` | `int` | `private` | `0` | - |
| `retryRequest` | `RetryRequest` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void fire(AgentCallbackEvent event)` | `void` | Trigger all registered callbacks for an event. |
| `public void requestRetry(double delaySeconds)` | `void` | Request the wrapped rail method to retry once more. |
| `public RetryRequest consumeRetryRequest()` | `RetryRequest` | Read and clear pending retry request. |

### `AgentCallbackEvent`

- 类型：`enum`
- 声明：`public enum AgentCallbackEvent`
- 说明：Agent callback event types for agent lifecycle.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `BEFORE_INVOKE` | `new AgentCallbackEvent("before_invoke")` | - |
| `AFTER_INVOKE` | `new AgentCallbackEvent("after_invoke")` | - |
| `BEFORE_MODEL_CALL` | `new AgentCallbackEvent("before_model_call")` | - |
| `AFTER_MODEL_CALL` | `new AgentCallbackEvent("after_model_call")` | - |
| `ON_MODEL_EXCEPTION` | `new AgentCallbackEvent("on_model_exception")` | - |
| `BEFORE_TOOL_CALL` | `new AgentCallbackEvent("before_tool_call")` | - |
| `AFTER_TOOL_CALL` | `new AgentCallbackEvent("after_tool_call")` | - |
| `ON_TOOL_EXCEPTION` | `new AgentCallbackEvent("on_tool_exception")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public String toString()` | `String` | - |

### `AgentCallbackFirer`

- 类型：`interface`
- 声明：`public interface AgentCallbackFirer`
- 说明：Interface for objects that can fire agent callback events.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx)` | `void` | Fire a callback event with the given context. |

### `AgentRail`

- 类型：`class`
- 声明：`public abstract class AgentRail`
- 说明：Base class for agent rails.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected AgentRail()` | - |
| `protected AgentRail(List<ToolCard> tools)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getPriority()` | `int` | - |
| `public void setPriority(int priority)` | `void` | - |
| `public List<ToolCard> getTools()` | `List<ToolCard>` | - |
| `public void beforeInvoke(AgentCallbackContext ctx)` | `void` | - |
| `public void afterInvoke(AgentCallbackContext ctx)` | `void` | - |
| `public void beforeModelCall(AgentCallbackContext ctx)` | `void` | - |
| `public void afterModelCall(AgentCallbackContext ctx)` | `void` | - |
| `public void onModelException(AgentCallbackContext ctx)` | `void` | - |
| `public void beforeToolCall(AgentCallbackContext ctx)` | `void` | - |
| `public void afterToolCall(AgentCallbackContext ctx)` | `void` | - |
| `public void onToolException(AgentCallbackContext ctx)` | `void` | - |
| `public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks()` | `Map<AgentCallbackEvent, Consumer<AgentCallbackContext>>` | Extract overridden hook methods. |

### `EventInputs`

- 类型：`interface`
- 声明：`public interface EventInputs`
- 说明：Marker interface for typed event inputs.

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `InvokeInputs`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class InvokeInputs implements EventInputs`
- 说明：Data for BEFORE/AFTER_INVOKE events.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `query` | `String` | `private` | `-` | - |
| `conversationId` | `String` | `private` | `-` | - |
| `result` | `Map<String, Object>` | `private` | `-` | - |

### `ModelCallInputs`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ModelCallInputs implements EventInputs`
- 说明：Input data for BEFORE/AFTER_MODEL_CALL events.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messages` | `List<Object>` | `private` | `new ArrayList<>()` | - |
| `tools` | `List<ToolInfo>` | `private` | `-` | - |
| `response` | `Object` | `private` | `-` | - |

### `RailExecutor`

- 类型：`class`
- 声明：`public final class RailExecutor`
- 说明：Utility class that replaces the Python `@rail` decorator.
- 嵌套公开类型：`RailExecutor.RailBody`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static <T>T execute(AgentCallbackContext ctx, AgentCallbackEvent before, AgentCallbackEvent after, AgentCallbackEvent onException, RailBody<T> body)` | `T` | Execute a callable with rail lifecycle events. |

### `RailExecutor.RailBody`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface RailBody<T>`
- 说明：Functional interface for the body of a railed method.
- 宿主类型：`RailExecutor`
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `T execute() throws Exception` | `T` | - |

### `RetryRequest`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class RetryRequest`
- 说明：Retry directive produced by on_exception rails.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `delaySeconds` | `double` | `private` | `0.0` | - |

### `ToolCallInputs`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ToolCallInputs implements EventInputs`
- 说明：Input data for BEFORE/AFTER_TOOL_CALL events.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `toolCall` | `ToolCall` | `private` | `-` | - |
| `toolName` | `String` | `private` | `""` | - |
| `toolArgs` | `Object` | `private` | `-` | - |
| `toolResult` | `Object` | `private` | `-` | - |
| `toolMsg` | `ToolMessage` | `private` | `-` | - |

## `com.openjiuwen.core.singleagent.schema`

公开类型：`3`

### `AgentCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class AgentCard extends BaseCard`
- 说明：Agent card data class.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inputParams` | `Map<String, Object>` | `private` | `-` | - |
| `outputParams` | `Map<String, Object>` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object toolInfo()` | `Object` | - |

### `AgentResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class AgentResult`
- 说明：Agent result data model.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `taskId` | `String` | `private` | `-` | - |
| `sessionId` | `String` | `private` | `-` | - |
| `status` | `TaskStatus` | `private` | `-` | - |
| `artifacts` | `List<Artifact>` | `private` | `new ArrayList<>()` | - |
| `metadata` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |

### `Artifact`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class Artifact`
- 说明：Artifact data model - represents a result artifact within an AgentResult.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `artifactId` | `String` | `private` | `-` | - |
| `name` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |
| `parts` | `List<Part>` | `private` | `new ArrayList<>()` | - |
| `metadata` | `Map<String, Object>` | `private` | `new HashMap<>()` | - |

## `com.openjiuwen.core.singleagent.skills`

公开类型：`5`

### `GitHubTree`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public class GitHubTree`
- 说明：Represents a GitHub directory tree with its metadata.
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `repoOwner` | `String` | `private` | `-` | - |
| `repoName` | `String` | `private` | `-` | - |
| `treeRef` | `String` | `private` | `"HEAD"` | - |
| `directory` | `String` | `private` | `""` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GitHubTree(String repoOwner, String repoName)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public GitHubTree copy()` | `GitHubTree` | - |

### `RemoteSkillUtil`

- 类型：`class`
- 声明：`public class RemoteSkillUtil`
- 说明：Utility class for registering remote skills from GitHub.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RemoteSkillUtil(String sysOperationId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSysOperationId()` | `String` | - |
| `public void setSysOperationId(String sysOperationId)` | `void` | - |
| `public static byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token)` | `byte[]` | Download a file from GitHub. |
| `public List<String> uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token)` | `List<String>` | Upload skills from GitHub to local storage. |

### `Skill`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class Skill`
- 说明：Represents a skill with its metadata.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |
| `directory` | `String` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String toString()` | `String` | - |

### `SkillManager`

- 类型：`class`
- 声明：`public class SkillManager`
- 说明：Manages skill registration and retrieval.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SkillManager(String sysOperationId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setSysOperationId(String sysOperationId)` | `void` | - |
| `public String getSysOperationId()` | `String` | - |
| `public void register(String skillPath, String sessionId, boolean overwrite)` | `void` | Register skill(s) from path. |
| `public void register(String skillPath)` | `void` | - |
| `public void unregister(String name)` | `void` | Unregister a skill by name. |
| `public Skill get(String name)` | `Skill` | Get skill by name. |
| `public List<Skill> getAll()` | `List<Skill>` | Get all registered skills. |
| `public List<String> getNames()` | `List<String>` | Get all registered skill names. |
| `public boolean has(String name)` | `boolean` | Check if a skill is registered. |
| `public void clear()` | `void` | Clear all registered skills. |
| `public int count()` | `int` | Get the number of registered skills. |
| `public String getDescription()` | `String` | - |
| `public void setDescription(String description)` | `void` | - |

### `SkillUtil`

- 类型：`class`
- 声明：`public class SkillUtil`
- 说明：High-level utility for managing and working with skills.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SkillUtil(String sysOperationId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setSysOperationId(String sysOperationId)` | `void` | - |
| `public SkillManager getSkillManager()` | `SkillManager` | - |
| `public RemoteSkillUtil getRemoteSkillUtil()` | `RemoteSkillUtil` | - |
| `public void registerSkills(Object skillPath, BaseAgent agent)` | `void` | Register skills from a path. |
| `public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token)` | `void` | Register remote skills from GitHub. |
| `public boolean hasSkill()` | `boolean` | Check if any skills are registered. |
| `public String getSkillPrompt()` | `String` | Generate a formatted prompt string with information about all registered skills. |

