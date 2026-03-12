# Session 模块 API 文档

> 包路径：`com.openjiuwen.core.session`

Agent / Workflow Session、状态管理、交互输入、流式输出与 tracer。基于 `session` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `70` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.session` | 7 |
| `com.openjiuwen.core.session.callback` | 3 |
| `com.openjiuwen.core.session.checkpointer` | 5 |
| `com.openjiuwen.core.session.config` | 2 |
| `com.openjiuwen.core.session.constants` | 1 |
| `com.openjiuwen.core.session.interaction` | 8 |
| `com.openjiuwen.core.session.internal` | 7 |
| `com.openjiuwen.core.session.state` | 11 |
| `com.openjiuwen.core.session.store` | 3 |
| `com.openjiuwen.core.session.stream` | 9 |
| `com.openjiuwen.core.session.tracer` | 12 |
| `com.openjiuwen.core.session.utils` | 2 |

## `com.openjiuwen.core.session`

公开类型：`7`

### `AgentGroupSessionApi`

- 类型：`class`
- 声明：`public class AgentGroupSessionApi`
- 说明：User-facing agent group session.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentGroupSessionApi(String sessionId, Map<String, Object> envs)` | Create a new agent group session. |
| `public AgentGroupSessionApi(String sessionId)` | - |
| `public AgentGroupSessionApi()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSessionId()` | `String` | - |
| `public Object getEnv(String key, Object defaultValue)` | `Object` | Get an environment variable. |
| `public AgentSession getInner()` | `AgentSession` | Get the underlying internal AgentSession. |
| `public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs)` | `AgentGroupSessionApi` | Factory method to create an agent group session. |

### `AgentSessionApi`

- 类型：`class`
- 声明：`public class AgentSessionApi implements Session`
- 说明：User-facing agent session providing high-level API for agent lifecycle management.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card)` | Create a new AgentSessionApi. |
| `public AgentSessionApi(String sessionId, Map<String, Object> envs)` | - |
| `public AgentSessionApi(String sessionId)` | - |
| `public AgentSessionApi()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getSessionId()` | `String` | - |
| `public Object getEnv(String key)` | `Object` | - |
| `public Object getEnv(String key, Object defaultValue)` | `Object` | - |
| `public Map<String, Object> getEnvs()` | `Map<String, Object>` | - |
| `public String getAgentId()` | `String` | - |
| `public String getAgentName()` | `String` | - |
| `public String getAgentDescription()` | `String` | - |
| `public void updateState(Map<String, Object> data)` | `void` | - |
| `public Object getState(Object key)` | `Object` | - |
| `public Object getState(String key)` | `Object` | - |
| `public Map<String, Object> dumpState()` | `Map<String, Object>` | - |
| `public void writeStream(Object data)` | `void` | - |
| `public void writeCustomStream(Map<String, Object> data)` | `void` | - |
| `public Iterator<Object> streamIterator()` | `Iterator<Object>` | Get stream output as a blocking iterator. |
| `public void streamOutput(java.util.function.Consumer<Object> consumer)` | `void` | Consume stream output incrementally via a callback. |
| `public void preRun(Object inputs)` | `void` | Pre-run hook: execute checkpointer pre-agent logic. |
| `public void postRun()` | `void` | Post-run hook: close stream and execute checkpointer post-agent logic. |
| `public WorkflowSessionApi createWorkflowSession()` | `WorkflowSessionApi` | Create a workflow session from this agent session. |
| `public void interact(Object value)` | `void` | Trigger an interaction. |
| `public AgentSession getInner()` | `AgentSession` | Get the underlying internal AgentSession. |
| `public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card)` | `AgentSessionApi` | Factory method for creating an agent session. |

### `BaseSession`

- 类型：`class`
- 声明：`public abstract class BaseSession implements Session`
- 说明：Base session abstraction providing access to all session subsystems.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract Config config()` | `Config` | Get the session configuration. |
| `public abstract State state()` | `State` | Get the session state. |
| `public abstract Object tracer()` | `Object` | Get the tracer instance. |
| `public abstract StreamWriterManager streamWriterManager()` | `StreamWriterManager` | Get the stream writer manager. |
| `public abstract CallbackManager callbackManager()` | `CallbackManager` | Get the callback manager. |
| `public abstract String sessionId()` | `String` | Get the unique session identifier. |
| `public abstract Object checkpointer()` | `Object` | Get the checkpointer instance. |
| `public String getSessionId()` | `String` | - |
| `public Object getState(String key)` | `Object` | - |
| `public void updateState(java.util.Map<String, Object> stateMap)` | `void` | - |
| `public void setCurrentOperatorId(String operatorId)` | `void` | - |
| `public String getCurrentOperatorId()` | `String` | - |
| `public void close()` | `void` | Close the session and release resources. |

### `NodeSessionApi`

- 类型：`class`
- 声明：`public class NodeSessionApi`
- 说明：User-facing node session providing simplified API for workflow components.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NodeSessionApi(NodeSession session, boolean streamMode)` | - |
| `public NodeSessionApi(NodeSession session)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getWorkflowId()` | `String` | - |
| `public String getComponentId()` | `String` | - |
| `public String getComponentType()` | `String` | - |
| `public String getComponentDescrip()` | `String` | - |
| `public void trace(Map<String, Object> data)` | `void` | - |
| `public void traceError(Exception error)` | `void` | - |
| `public <T>T interact(Object value)` | `T` | Trigger interaction with the user. |
| `public String getExecutableId()` | `String` | - |
| `public String getSessionId()` | `String` | - |
| `public void updateState(Map<String, Object> data)` | `void` | - |
| `public Object getState(Object key)` | `Object` | - |
| `public void updateGlobalState(Map<String, Object> data)` | `void` | - |
| `public Object getGlobalState(Object key)` | `Object` | - |
| `public Map<String, Object> dumpState()` | `Map<String, Object>` | - |
| `public void writeStream(Object data)` | `void` | - |
| `public void writeCustomStream(Map<String, Object> data)` | `void` | - |
| `public Object getCallbackManager()` | `Object` | - |
| `public Object getEnv(String key)` | `Object` | - |
| `public NodeSession getInner()` | `NodeSession` | Get the underlying internal NodeSession. |

### `ProxySession`

- 类型：`class`
- 声明：`public class ProxySession extends BaseSession`
- 说明：Proxy session that delegates all calls to an underlying stub session.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ProxySession()` | - |
| `public ProxySession(BaseSession stub)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setSession(BaseSession stub)` | `void` | Set the underlying session implementation. |
| `public BaseSession getStub()` | `BaseSession` | Get the underlying session implementation. |
| `public Config config()` | `Config` | - |
| `public State state()` | `State` | - |
| `public Object tracer()` | `Object` | - |
| `public StreamWriterManager streamWriterManager()` | `StreamWriterManager` | - |
| `public CallbackManager callbackManager()` | `CallbackManager` | - |
| `public String sessionId()` | `String` | - |
| `public Object checkpointer()` | `Object` | - |

### `Session`

- 类型：`interface`
- 声明：`public interface Session`
- 说明：Minimal session interface required by ContextEngine.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String getSessionId()` | `String` | Return the unique session identifier. |
| `Object getState(String key)` | `Object` | Retrieve a named state block (e.g., "context") from session storage. |
| `void updateState(Map<String, Object> state)` | `void` | Merge the given state map into session storage. |
| `default void setCurrentOperatorId(String operatorId)` | `void` | Set the current operator id for tracing and attribution. |
| `default String getCurrentOperatorId()` | `String` | Get the current operator id used by the active execution span. |

### `WorkflowSessionApi`

- 类型：`class`
- 声明：`public class WorkflowSessionApi`
- 说明：User-facing workflow session managing the lifecycle of a workflow execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowSessionApi(BaseSession parent, String sessionId, Map<String, Object> envs)` | Create a workflow session from a parent session. |
| `public WorkflowSessionApi(BaseSession parent, String sessionId)` | - |
| `public WorkflowSessionApi(String sessionId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public CallbackManager getCallbackManager()` | `CallbackManager` | - |
| `public String getSessionId()` | `String` | - |
| `public Map<String, Object> getEnvs()` | `Map<String, Object>` | - |
| `public BaseSession getParent()` | `BaseSession` | - |
| `public void setWorkflowCard(Object card)` | `void` | - |
| `public Object getWorkflowCard()` | `Object` | - |
| `public static WorkflowSessionApi create(BaseSession parent, String sessionId, Map<String, Object> envs)` | `WorkflowSessionApi` | Factory method to create a workflow session. |

## `com.openjiuwen.core.session.callback`

公开类型：`3`

### `BaseHandler`

- 类型：`class`
- 声明：`public abstract class BaseHandler`
- 说明：Base handler for stateless data processing via callbacks.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `owner` | `Object` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseHandler(Object owner)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getOwner()` | `Object` | - |
| `public abstract String eventName()` | `String` | Return the event name this handler is associated with. |
| `public List<String> getTriggerEvents()` | `List<String>` | Get all methods annotated with TriggerEvent. |

### `CallbackManager`

- 类型：`class`
- 声明：`public class CallbackManager`
- 说明：Manages callback handlers and triggers events.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `handlers` | `Map<String, BaseHandler>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `triggerEvents` | `Map<String, List<String>>` | `private final` | `new ConcurrentHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CallbackManager()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void register(Map<String, BaseHandler> configs)` | `void` | Register handlers from a config map. |
| `public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | `void` | Trigger a specific event on a handler. |
| `public BaseHandler getHandler(String handlerName)` | `BaseHandler` | Get a registered handler by name. |

### `TriggerEvent`

- 类型：`annotation`
- 声明：`@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface TriggerEvent`
- 说明：Annotation to mark methods as trigger events in a handler.
- 注解：`@Retention`、`@Target`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

## `com.openjiuwen.core.session.checkpointer`

公开类型：`5`

### `Checkpointer`

- 类型：`class`
- 声明：`public abstract class Checkpointer`
- 说明：Abstract checkpointer for managing session state persistence across workflow/agent executions.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `SESSION_NAMESPACE_AGENT` | `String` | `public static final` | `"agent"` | Namespace for agent state under session. |
| `SESSION_NAMESPACE_WORKFLOW` | `String` | `public static final` | `"workflow"` | Namespace for workflow state under session. |
| `WORKFLOW_NAMESPACE_GRAPH` | `String` | `public static final` | `"workflow-graph"` | Namespace for graph state under workflow. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String getThreadId(BaseSession session)` | `String` | Get the thread ID for a session (session_id:workflow_id). |
| `public abstract void preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | `void` | Pre-workflow execution hook. |
| `public abstract void postWorkflowExecute(BaseSession session, Object result, Exception exception)` | `void` | Post-workflow execution hook. |
| `public abstract void preAgentExecute(BaseSession session, Object inputs)` | `void` | Pre-agent execution hook. |
| `public abstract void interruptAgentExecute(BaseSession session)` | `void` | Interrupt agent execution for interaction. |
| `public abstract void postAgentExecute(BaseSession session)` | `void` | Post-agent execution hook. |
| `public abstract boolean sessionExists(String sessionId)` | `boolean` | Check whether a session exists. |
| `public abstract void release(String sessionId)` | `void` | Release (clear) all checkpoints for a session. |
| `public abstract Store graphStore()` | `Store` | Get the graph store used by this checkpointer. |
| `protected static String getWorkflowId(BaseSession session)` | `String` | - |
| `public static String buildKey(String... parts)` | `String` | Build a key by joining parts with ':'. |
| `public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId, String... suffixes)` | `String` | Build a key with namespace structure: session:namespace:entity_id:suffixes. |

### `CheckpointerFactory`

- 类型：`class`
- 声明：`public final class CheckpointerFactory`
- 说明：Factory and registry for checkpointer instances.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void register(String name, CheckpointerProvider provider)` | `void` | Register a checkpointer provider for a given type name. |
| `public static Checkpointer create(String type, Map<String, Object> conf)` | `Checkpointer` | Create a checkpointer from config. |
| `public static void setDefaultCheckpointer(Checkpointer checkpointer)` | `void` | Set the default checkpointer instance. |
| `public static void setCheckpointer(String storeType, Checkpointer checkpointer)` | `void` | Set a checkpointer instance for a specific type. |
| `public static Checkpointer getCheckpointer(String storeType)` | `Checkpointer` | Get checkpointer instance. |
| `public static Checkpointer getCheckpointer()` | `Checkpointer` | Get the default in-memory checkpointer. |

### `CheckpointerProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface CheckpointerProvider`
- 说明：Provider interface for creating checkpointer instances.
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Checkpointer create(Map<String, Object> conf)` | `Checkpointer` | Create a checkpointer with the given configuration. |

### `InMemoryCheckpointer`

- 类型：`class`
- 声明：`public class InMemoryCheckpointer extends Checkpointer`
- 说明：In-memory checkpointer implementation storing state in local maps.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void preWorkflowExecute(BaseSession session, InteractiveInput inputs)` | `void` | - |
| `public void postWorkflowExecute(BaseSession session, Object result, Exception exception)` | `void` | - |
| `public void preAgentExecute(BaseSession session, Object inputs)` | `void` | - |
| `public void interruptAgentExecute(BaseSession session)` | `void` | - |
| `public void postAgentExecute(BaseSession session)` | `void` | - |
| `public boolean sessionExists(String sessionId)` | `boolean` | - |
| `public void release(String sessionId)` | `void` | - |
| `public Store graphStore()` | `Store` | - |

### `Storage`

- 类型：`class`
- 声明：`public abstract class Storage`
- 说明：Abstract storage for saving/recovering session state.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract void save(BaseSession session)` | `void` | Save the session state. |
| `public abstract void recover(BaseSession session, InteractiveInput inputs)` | `void` | Recover the session state. |
| `public void recover(BaseSession session)` | `void` | Recover session state without interactive input. |
| `public abstract void clear(String id)` | `void` | Clear stored state for the given ID. |
| `public abstract boolean exists(BaseSession session)` | `boolean` | Check if state exists for the given session. |

## `com.openjiuwen.core.session.config`

公开类型：`2`

### `Config`

- 类型：`class`
- 声明：`public class Config`
- 说明：Session configuration holding environment variables, workflow configs, and agent config.
- 嵌套公开类型：`Config.MetadataLike`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `callbackMetadata` | `Map<String, MetadataLike>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `env` | `Map<String, Object>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `workflowConfigs` | `Map<String, Object>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `agentConfig` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Config()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setEnvs(Map<String, Object> envs)` | `void` | Set environment variables. |
| `public Object getEnv(String key, Object defaultValue)` | `Object` | Get an environment variable by key. |
| `public Object getEnv(String key)` | `Object` | Get an environment variable by key with null default. |
| `public Map<String, Object> getEnvs()` | `Map<String, Object>` | Get a copy of all environment variables. |
| `public Object getWorkflowConfig(String workflowId)` | `Object` | Get workflow config by workflow ID. |
| `public Object getAgentConfig()` | `Object` | Get agent config. |
| `public void setAgentConfig(Object agentConfig)` | `void` | Set agent config. |
| `public void addWorkflowConfig(String workflowId, Object workflowConfig)` | `void` | Add a workflow config. |
| `public Map<String, MetadataLike> getCallbackMetadata()` | `Map<String, MetadataLike>` | Get callback metadata. |

### `Config.MetadataLike`

- 类型：`class`
- 声明：`public static class MetadataLike`
- 说明：Metadata-like structure for callback registration.
- 宿主类型：`Config`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `name` | `String` | `private` | `-` | - |
| `event` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MetadataLike()` | - |
| `public MetadataLike(String name, String event)` | - |
| `public MetadataLike(String id, String name, String event)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getId()` | `String` | - |
| `public void setId(String id)` | `void` | - |
| `public String getName()` | `String` | - |
| `public void setName(String name)` | `void` | - |
| `public String getEvent()` | `String` | - |
| `public void setEvent(String event)` | `void` | - |

## `com.openjiuwen.core.session.constants`

公开类型：`1`

### `SessionConstants`

- 类型：`class`
- 声明：`public final class SessionConstants`
- 说明：Session module constants.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `WORKFLOW_EXECUTE_TIMEOUT` | `String` | `public static final` | `"_execute_timeout"` | - |
| `WORKFLOW_STREAM_FRAME_TIMEOUT` | `String` | `public static final` | `"_stream_frame_timeout"` | - |
| `WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT` | `String` | `public static final` | `"_stream_first_frame_timeout"` | - |
| `COMP_STREAM_CALL_TIMEOUT_KEY` | `String` | `public static final` | `"_comp_stream_call_timeout"` | Transform/collect stream call timeout. |
| `STREAM_INPUT_GEN_TIMEOUT_KEY` | `String` | `public static final` | `"_stream_input_generator_timeout"` | Stream inputs generator timeout. |
| `END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY` | `String` | `public static final` | `"_end_comp_template_render_position_timeout"` | End Component template config environment fields. |
| `END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY` | `String` | `public static final` | `"_end_comp_template_branch_render_timeout"` | - |
| `LOOP_NUMBER_MAX_LIMIT_KEY` | `String` | `public static final` | `"_loop_number_max_limit"` | Loop Component max number limit. |
| `LOOP_NUMBER_MAX_LIMIT_DEFAULT` | `int` | `public static final` | `1000` | - |
| `FORCE_DEL_WORKFLOW_STATE_KEY` | `String` | `public static final` | `"_force_del_workflow_state"` | Checkpointer control. |
| `LOOP_ID` | `String` | `public static final` | `"_loop_id"` | Loop node constants. |
| `INDEX` | `String` | `public static final` | `"_index"` | - |
| `WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY` | `String` | `public static final` | `"WORKFLOW_EXECUTE_TIMEOUT"` | - |
| `WORKFLOW_STREAM_FRAME_TIMEOUT_ENV_KEY` | `String` | `public static final` | `"WORKFLOW_STREAM_FRAME_TIMEOUT"` | - |
| `WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT_ENV_KEY` | `String` | `public static final` | `"WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT"` | - |
| `COMP_STREAM_CALL_TIMEOUT_ENV_KEY` | `String` | `public static final` | `"COMP_STREAM_CALL_TIMEOUT"` | - |
| `STREAM_INPUT_GEN_TIMEOUT_ENV_KEY` | `String` | `public static final` | `"STREAM_INPUT_GEN_TIMEOUT"` | - |
| `LOOP_NUMBER_MAX_LIMIT_ENV_KEY` | `String` | `public static final` | `"LOOP_NUMBER_MAX_LIMIT"` | - |
| `FORCE_DEL_WORKFLOW_STATE_ENV_KEY` | `String` | `public static final` | `"FORCE_DEL_WORKFLOW_STATE"` | - |

## `com.openjiuwen.core.session.interaction`

公开类型：`8`

### `AgentInteraction`

- 类型：`class`
- 声明：`public class AgentInteraction extends BaseInteraction`
- 说明：Agent-level interaction handler.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentInteraction(BaseSession session)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object waitUserInputs(Object value)` | `Object` | - |

### `AgentInterrupt`

- 类型：`class`
- 声明：`public class AgentInterrupt extends RuntimeException`
- 说明：Exception thrown when an agent execution is interrupted for user interaction.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentInterrupt()` | - |
| `public AgentInterrupt(String message)` | - |

### `BaseInteraction`

- 类型：`class`
- 声明：`public abstract class BaseInteraction`
- 说明：Base class for interaction handling, managing interactive input queue.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `interactiveInputs` | `List<Object>` | `protected` | `-` | - |
| `latestInteractiveInputs` | `Object` | `protected` | `-` | - |
| `idx` | `int` | `protected` | `-` | - |
| `session` | `BaseSession` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseInteraction(BaseSession session, Object defaultInput)` | - |
| `protected BaseInteraction(BaseSession session)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected Object getNextInteractiveInput()` | `Object` | Get the next interactive input from the queue. |
| `public abstract Object waitUserInputs(Object value)` | `Object` | Wait for user inputs, blocking until input is available. |
| `public Object userLatestInput(Object value)` | `Object` | Get the latest user input. |

### `InteractionOutput`

- 类型：`class`
- 声明：`public class InteractionOutput`
- 说明：Output payload for interaction events.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `value` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InteractionOutput()` | - |
| `public InteractionOutput(String id, Object value)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getId()` | `String` | - |
| `public void setId(String id)` | `void` | - |
| `public Object getValue()` | `Object` | - |
| `public void setValue(Object value)` | `void` | - |

### `InteractiveInput`

- 类型：`class`
- 声明：`public class InteractiveInput`
- 说明：Interactive input data carrying user inputs for interactions.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `userInputs` | `Map<String, Object>` | `private` | `-` | Map of interaction ID -> user input value. |
| `rawInputs` | `Object` | `private` | `-` | Raw input not bound to any ID, used for the first interaction. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InteractiveInput()` | Create with no inputs. |
| `public InteractiveInput(Object rawInputs)` | Create with raw inputs. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Object> getUserInputs()` | `Map<String, Object>` | - |
| `public void setUserInputs(Map<String, Object> userInputs)` | `void` | - |
| `public Object getRawInputs()` | `Object` | - |
| `public void setRawInputs(Object rawInputs)` | `void` | - |
| `public void update(String nodeId, Object value)` | `void` | Update user inputs for a specific node. |

### `SimpleAgentInteraction`

- 类型：`class`
- 声明：`public class SimpleAgentInteraction`
- 说明：Simple agent interaction that interrupts via checkpointer.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SimpleAgentInteraction(BaseSession agentSession)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void waitUserInputs(String message)` | `void` | Wait for user inputs by interrupting agent execution. |

### `WorkflowInteraction`

- 类型：`class`
- 声明：`public class WorkflowInteraction extends BaseInteraction`
- 说明：Workflow-level interaction handler that interrupts graph execution for user input.
- 嵌套公开类型：`WorkflowInteraction.GraphInterruptRuntimeWrapper`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowInteraction(BaseSession session)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object waitUserInputs(Object value)` | `Object` | - |
| `public Object userLatestInput(Object value)` | `Object` | - |

### `WorkflowInteraction.GraphInterruptRuntimeWrapper`

- 类型：`class`
- 声明：`public static class GraphInterruptRuntimeWrapper extends RuntimeException`
- 说明：Runtime wrapper for GraphInterrupt so it can be thrown from methods that don't declare checked exceptions.
- 宿主类型：`WorkflowInteraction`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphInterruptRuntimeWrapper(GraphInterrupt cause)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public GraphInterrupt getGraphInterrupt()` | `GraphInterrupt` | - |

## `com.openjiuwen.core.session.internal`

公开类型：`7`

### `AgentSession`

- 类型：`class`
- 声明：`public class AgentSession extends BaseSession`
- 说明：Agent session providing full session lifecycle for an agent execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentSession(String sessionId, Config config, Checkpointer checkpointer, Object card)` | Create a new AgentSession. |
| `public AgentSession(String sessionId, Config config, Checkpointer checkpointer)` | Convenience constructor without card. |
| `public AgentSession(String sessionId, Config config)` | Convenience constructor with defaults. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Config config()` | `Config` | - |
| `public State state()` | `State` | - |
| `public Object tracer()` | `Object` | - |
| `public Tracer tracerTyped()` | `Tracer` | Get the tracer (typed). |
| `public StreamWriterManager streamWriterManager()` | `StreamWriterManager` | - |
| `public CallbackManager callbackManager()` | `CallbackManager` | - |
| `public String sessionId()` | `String` | - |
| `public Object checkpointer()` | `Object` | - |
| `public Checkpointer checkpointerTyped()` | `Checkpointer` | Get the checkpointer (typed). |
| `public TraceAgentSpan span()` | `TraceAgentSpan` | Get the agent span for this session. |
| `public WorkflowSession createWorkflowSession()` | `WorkflowSession` | Create a workflow session from this agent session. |
| `public String agentId()` | `String` | Get the agent ID from config or card. |
| `public String agentName()` | `String` | Get the agent name from the card. |
| `public String agentDescription()` | `String` | Get the agent description from the card. |

### `NodeSession`

- 类型：`class`
- 声明：`public class NodeSession extends BaseSession`
- 说明：Node session representing a workflow node's scoped session.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public NodeSession(BaseSession session, String nodeId, String nodeType)` | - |
| `public NodeSession(BaseSession session, String nodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String nodeId()` | `String` | - |
| `public String nodeType()` | `String` | - |
| `public String executableId()` | `String` | - |
| `public String parentId()` | `String` | - |
| `public String workflowId()` | `String` | - |
| `public String mainWorkflowId()` | `String` | - |
| `public int workflowNestingDepth()` | `int` | - |
| `public BaseSession parent()` | `BaseSession` | - |
| `public Config config()` | `Config` | - |
| `public State state()` | `State` | - |
| `public Object tracer()` | `Object` | - |
| `public StreamWriterManager streamWriterManager()` | `StreamWriterManager` | - |
| `public CallbackManager callbackManager()` | `CallbackManager` | - |
| `public String sessionId()` | `String` | - |
| `public Object checkpointer()` | `Object` | - |
| `public Object nodeConfig()` | `Object` | Get node-specific config from workflow config. |

### `RouterSession`

- 类型：`class`
- 声明：`public class RouterSession extends StateSession`
- 说明：Router session where most operations are no-ops.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RouterSession(BaseSession inner)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void interact(Object value)` | `void` | - |
| `public void trace(Map<String, Object> data)` | `void` | - |
| `public void traceError(Exception error)` | `void` | - |
| `public StreamWriter<?> streamWriter()` | `StreamWriter<?>` | - |
| `public StreamWriter<?> customWriter()` | `StreamWriter<?>` | - |
| `public void writeStream(Object data)` | `void` | - |
| `public void writeCustomStream(Map<String, Object> data)` | `void` | - |
| `public void updateGlobalState(Map<String, Object> data)` | `void` | - |
| `public void updateState(Map<String, Object> data)` | `void` | - |
| `public Object getWorkflowConfig(String workflowId)` | `Object` | - |
| `public Config.MetadataLike getAgentConfig()` | `Config.MetadataLike` | - |
| `public Object getEnv(String key)` | `Object` | - |
| `public BaseSession base()` | `BaseSession` | - |

### `StateSession`

- 类型：`class`
- 声明：`public abstract class StateSession extends WrappedSession`
- 说明：Abstract session providing state and stream delegation to the inner session.

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected StateSession(BaseSession inner)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String executableId()` | `String` | - |
| `public String sessionId()` | `String` | - |
| `public void updateState(Map<String, Object> data)` | `void` | - |
| `public Object getState(Object key)` | `Object` | - |
| `public void updateGlobalState(Map<String, Object> data)` | `void` | - |
| `public Object getGlobalState(Object key)` | `Object` | - |
| `public StreamWriter<?> streamWriter()` | `StreamWriter<?>` | - |
| `public StreamWriter<?> customWriter()` | `StreamWriter<?>` | - |
| `public void writeStream(Object data)` | `void` | - |
| `public void writeCustomStream(Map<String, Object> data)` | `void` | - |

### `SubWorkflowSession`

- 类型：`class`
- 声明：`public class SubWorkflowSession extends NodeSession`
- 说明：Sub-workflow session used when a workflow is nested inside another workflow.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SubWorkflowSession(BaseSession session, String nodeId, String nodeType, String workflowId)` | - |
| `public SubWorkflowSession(BaseSession session, String nodeId, String workflowId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String workflowId()` | `String` | - |
| `public int workflowNestingDepth()` | `int` | - |
| `public ActorManager actorManager()` | `ActorManager` | - |
| `public void setActorManager(ActorManager actorManager)` | `void` | - |

### `WorkflowSession`

- 类型：`class`
- 声明：`public class WorkflowSession extends BaseSession`
- 说明：Internal workflow session implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowSession(String workflowId, BaseSession parent, String sessionId, State state, CallbackManager callbackManager)` | - |
| `public WorkflowSession(String workflowId, BaseSession parent)` | - |
| `public WorkflowSession(String workflowId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setStreamWriterManager(StreamWriterManager streamWriterManager)` | `void` | - |
| `public void setTracer(Object tracer)` | `void` | - |
| `public void setActorManager(ActorManager actorManager)` | `void` | - |
| `public void setWorkflowId(String workflowId)` | `void` | - |
| `public String workflowId()` | `String` | - |
| `public String mainWorkflowId()` | `String` | - |
| `public int workflowNestingDepth()` | `int` | - |
| `public BaseSession parent()` | `BaseSession` | - |
| `public ActorManager actorManager()` | `ActorManager` | - |
| `public Config config()` | `Config` | - |
| `public State state()` | `State` | - |
| `public Object tracer()` | `Object` | - |
| `public StreamWriterManager streamWriterManager()` | `StreamWriterManager` | - |
| `public CallbackManager callbackManager()` | `CallbackManager` | - |
| `public String sessionId()` | `String` | - |
| `public Object checkpointer()` | `Object` | - |
| `public void close()` | `void` | - |

### `WrappedSession`

- 类型：`class`
- 声明：`public abstract class WrappedSession`
- 说明：Abstract wrapped session providing convenience accessors around a BaseSession.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inner` | `BaseSession` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected WrappedSession(BaseSession inner)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getWorkflowConfig(String workflowId)` | `Object` | Get workflow config for the given workflow ID. |
| `public Config.MetadataLike getAgentConfig()` | `Config.MetadataLike` | Get agent config. |
| `public Object getEnv(String key)` | `Object` | Get environment variable from config. |
| `public BaseSession base()` | `BaseSession` | Get the underlying base session. |
| `public abstract String executableId()` | `String` | Get the executable ID for this wrapped session. |
| `public abstract String sessionId()` | `String` | Get the session ID. |
| `public String userId()` | `String` | Get user ID (default empty). |
| `public abstract void updateState(Map<String, Object> data)` | `void` | Update the session state. |
| `public abstract Object getState(Object key)` | `Object` | Get session state by key. |
| `public abstract void updateGlobalState(Map<String, Object> data)` | `void` | Update global state. |
| `public abstract Object getGlobalState(Object key)` | `Object` | Get global state by key. |
| `public abstract StreamWriter<?> streamWriter()` | `StreamWriter<?>` | Get output stream writer. |
| `public abstract StreamWriter<?> customWriter()` | `StreamWriter<?>` | Get custom stream writer. |
| `public abstract void writeStream(Object data)` | `void` | Write data to the output stream. |
| `public abstract void writeCustomStream(Map<String, Object> data)` | `void` | Write data to the custom stream. |
| `public abstract void trace(Map<String, Object> data)` | `void` | Trace data. |
| `public abstract void traceError(Exception error)` | `void` | Trace an error. |
| `public abstract void interact(Object value)` | `void` | Trigger an interaction. |
| `public void postRun()` | `void` | Post-run hook (default no-op). |
| `public void preRun(Map<String, Object> kwargs)` | `void` | Pre-run hook (default no-op). |
| `public void release(String sessionId)` | `void` | Release session resources (default no-op). |

## `com.openjiuwen.core.session.state`

公开类型：`11`

### `AgentStateCollection`

- 类型：`class`
- 声明：`public class AgentStateCollection implements State`
- 说明：Agent state collection managing global and agent state partitions.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `globalState` | `InMemoryStateLike` | `private final` | `-` | - |
| `agentState` | `InMemoryStateLike` | `private final` | `-` | - |
| `traceState` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentStateCollection()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object get(Object key)` | `Object` | - |
| `public void update(Map<String, Object> data)` | `void` | - |
| `public void updateTrace(Object span)` | `void` | - |
| `public void updateGlobal(Map<String, Object> data)` | `void` | - |
| `public Object getGlobal(Object key)` | `Object` | - |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void setState(Map<String, Object> state)` | `void` | - |
| `public InMemoryStateLike getGlobalStateLike()` | `InMemoryStateLike` | Get the internal global state object. |
| `public Map<String, Object> dump()` | `Map<String, Object>` | - |

### `CommitStateLike`

- 类型：`interface`
- 声明：`public interface CommitStateLike extends StateLike`
- 说明：State interface with commit/rollback capabilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void updateById(String nodeId, Map<String, Object> data)` | `void` | Update state by node id. |
| `void commit(String nodeId)` | `void` | Commit all pending updates, or only for a specific node. |
| `default void commit()` | `void` | Commit all pending updates. |
| `void rollback(String nodeId)` | `void` | Rollback pending updates for a specific node. |
| `Map<String, Object> getUpdates()` | `Map<String, Object>` | Get pending updates. |
| `void setUpdates(Map<String, Object> updates)` | `void` | Set pending updates. |

### `InMemoryCommitState`

- 类型：`class`
- 声明：`public class InMemoryCommitState implements CommitStateLike`
- 说明：In-memory commit state with pending updates and commit/rollback support.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `state` | `StateLike` | `private final` | `-` | - |
| `updates` | `Map<String, List<Map<String, Object>>>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InMemoryCommitState()` | - |
| `public InMemoryCommitState(StateLike state)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public synchronized void update(Map<String, Object> data)` | `void` | - |
| `public synchronized void updateById(String nodeId, Map<String, Object> data)` | `void` | - |
| `public synchronized void commit(String nodeId)` | `void` | - |
| `public synchronized void rollback(String nodeId)` | `void` | - |
| `public Object getByTransformer(Function<Object, Object> transformer)` | `Object` | - |
| `public Object get(Object key)` | `Object` | - |
| `public Object getByPrefix(Object key, String nestedPrefix)` | `Object` | - |
| `public synchronized Map<String, Object> getUpdates()` | `Map<String, Object>` | - |
| `public synchronized void setUpdates(Map<String, Object> newUpdates)` | `void` | - |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void setState(Map<String, Object> newState)` | `void` | - |

### `InMemoryState`

- 类型：`class`
- 声明：`public final class InMemoryState`
- 说明：Factory for creating in-memory workflow states (CommitState instances backed by InMemoryCommitState).

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static WorkflowCommitState create(Map<String, Object> ioState, Map<String, Object> globalState, Map<String, Object> compState, Map<String, Object> workflowState, Map<String, Object> traceState)` | `WorkflowCommitState` | Create a new WorkflowCommitState backed by in-memory commit states. |
| `public static WorkflowCommitState create()` | `WorkflowCommitState` | Create a new WorkflowCommitState with empty states. |
| `public static WorkflowCommitState fromMap(Map<String, Object> stateMap)` | `WorkflowCommitState` | Create from a full state map (as returned by CommitState.getState()). |

### `InMemoryStateLike`

- 类型：`class`
- 声明：`public class InMemoryStateLike implements StateLike`
- 说明：In-memory implementation of StateLike.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `state` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InMemoryStateLike()` | - |
| `public InMemoryStateLike(Map<String, Object> initialState)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public synchronized Object get(Object key)` | `Object` | - |
| `public synchronized Object getByPrefix(Object key, String nestedPrefix)` | `Object` | - |
| `public synchronized Object getByTransformer(Function<Object, Object> transformer)` | `Object` | - |
| `public synchronized void update(Map<String, Object> data)` | `void` | - |
| `public synchronized Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public synchronized void setState(Map<String, Object> newState)` | `void` | - |

### `ReadableState`

- 类型：`interface`
- 声明：`public interface ReadableState`
- 说明：Read-only state interface.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Object get(Object key)` | `Object` | Get value by key (supports str, list, dict schema). |
| `Object getByPrefix(Object key, String nestedPrefix)` | `Object` | Get value by key with nested path prefix. |

### `RecoverableState`

- 类型：`interface`
- 声明：`public interface RecoverableState`
- 说明：Recoverable state interface supporting snapshot and restore.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Map<String, Object> getState()` | `Map<String, Object>` | Get full state as a map. |
| `void setState(Map<String, Object> state)` | `void` | Set full state from a map. |

### `State`

- 类型：`interface`
- 声明：`public interface State extends RecoverableState`
- 说明：Abstract base State interface for session state management.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `GLOBAL_STATE_KEY` | `String` | `-` | `"global_state"` | Key for global state partition. |
| `IO_STATE_KEY` | `String` | `-` | `"io_state"` | Key for io state partition. |
| `IO_STATE_UPDATES_KEY` | `String` | `-` | `"io_state_updates"` | Key for io state updates. |
| `GLOBAL_STATE_UPDATES_KEY` | `String` | `-` | `"global_state_updates"` | Key for global state updates. |
| `COMP_STATE_KEY` | `String` | `-` | `"comp_state"` | Key for component state partition. |
| `COMP_STATE_UPDATES_KEY` | `String` | `-` | `"comp_state_updates"` | Key for component state updates. |
| `WORKFLOW_STATE_KEY` | `String` | `-` | `"workflow_state"` | Key for workflow state partition. |
| `WORKFLOW_STATE_UPDATES_KEY` | `String` | `-` | `"workflow_state_updates"` | Key for workflow state updates. |
| `AGENT_STATE_KEY` | `String` | `-` | `"agent_state"` | Key for agent state partition. |
| `TRACE_STATE_KEY` | `String` | `-` | `"trace_state"` | Key for trace state partition. |
| `DEFAULT_NODE_ID` | `String` | `-` | `"default"` | Default node id. |
| `DEFAULT_WORKFLOW_ID` | `String` | `-` | `"workflow"` | Default workflow id. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Object getGlobal(Object key)` | `Object` | Get global state by key. |
| `void updateGlobal(Map<String, Object> data)` | `void` | Update global state. |
| `void updateTrace(Object span)` | `void` | Update trace state. |
| `void update(Map<String, Object> data)` | `void` | Update component/local state. |
| `Object get(Object key)` | `Object` | Get component/local state by key. |
| `Map<String, Object> dump()` | `Map<String, Object>` | Dump full state for debugging. |

### `StateLike`

- 类型：`interface`
- 声明：`public interface StateLike extends ReadableState, RecoverableState`
- 说明：Mutable state interface with read/write capabilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void update(Map<String, Object> data)` | `void` | Update state with the given data. |
| `Object getByTransformer(Function<Object, Object> transformer)` | `Object` | Get value via transformer function. |

### `WorkflowCommitState`

- 类型：`class`
- 声明：`public class WorkflowCommitState extends WorkflowStateCollection`
- 说明：Workflow commit state with full commit/rollback and node state creation.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `snapshot` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowCommitState(CommitStateLike ioState, CommitStateLike globalState, CommitStateLike compState, CommitStateLike workflowState, Map<String, Object> traceState, String parentId, String nodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void commit()` | `void` | Commit all state partitions. |
| `public void commitCmp()` | `void` | Commit component and IO state for the current node. |
| `public void commitWorkflow()` | `void` | Commit workflow-scoped state for the current node. |
| `public void updateAndCommitWorkflowState(Map<String, Object> data)` | `void` | Update and immediately commit workflow-scoped state. |
| `public void rollback()` | `void` | Rollback all state partitions. |
| `public Map<String, Object> getState()` | `Map<String, Object>` | Save snapshot of current state. |
| `public void setState(Map<String, Object> state)` | `void` | Restore state from snapshot. |
| `public WorkflowCommitState createNodeState(String newNodeId, String newParentId)` | `WorkflowCommitState` | Create a node state for the given node ID. |
| `public WorkflowCommitState createNodeState(String newNodeId)` | `WorkflowCommitState` | Backward-compatible overload for tests and callers that only provide node id. |
| `public CommitStateLike getIoState()` | `CommitStateLike` | Get IO state. |
| `public CommitStateLike getGlobalState()` | `CommitStateLike` | Get global state. |
| `public CommitStateLike getCompState()` | `CommitStateLike` | Get component state. |
| `public CommitStateLike getWorkflowState()` | `CommitStateLike` | Get workflow state. |
| `public Map<String, Object> getTraceState()` | `Map<String, Object>` | Get trace state. |
| `public Map<String, Object> getUpdates()` | `Map<String, Object>` | Get pending updates for all partitions. |
| `public void setUpdates(Map<String, Object> updates)` | `void` | Restore pending updates for all partitions. |

### `WorkflowStateCollection`

- 类型：`class`
- 声明：`public class WorkflowStateCollection implements State`
- 说明：Workflow state collection managing io, global, comp, and workflow state partitions.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ioState` | `CommitStateLike` | `protected final` | `-` | - |
| `globalState` | `CommitStateLike` | `protected final` | `-` | - |
| `compState` | `CommitStateLike` | `protected final` | `-` | - |
| `workflowState` | `CommitStateLike` | `protected final` | `-` | - |
| `traceState` | `Map<String, Object>` | `protected` | `-` | - |
| `parentId` | `String` | `protected` | `-` | - |
| `nodeId` | `String` | `protected` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WorkflowStateCollection(CommitStateLike ioState, CommitStateLike globalState, CommitStateLike compState, CommitStateLike workflowState, Map<String, Object> traceState, String parentId, String nodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getGlobal(Object key)` | `Object` | - |
| `public void updateGlobal(Map<String, Object> data)` | `void` | - |
| `public void updateTrace(Object span)` | `void` | - |
| `public void update(Map<String, Object> data)` | `void` | - |
| `public Object get(Object key)` | `Object` | - |
| `public Object getWorkflow(Object key)` | `Object` | Get workflow-scoped state by key. |
| `public void updateWorkflow(Map<String, Object> data)` | `void` | Update workflow-scoped state for the current node. |
| `public Map<String, Object> dump()` | `Map<String, Object>` | - |
| `public void commitCmp()` | `void` | Commit component state. |
| `public Object getInputs(Object schema)` | `Object` | Get inputs by a schema map. |
| `public Object getInputsByTransformer(Object transformer)` | `Object` | Get inputs by a transformer function. |
| `public void setOutputs(Object results)` | `void` | Set outputs for the current node. |
| `public Object getOutputs(String outputNodeId)` | `Object` | Get outputs for a specific node. |
| `public void commitUserInputs(Map<String, Object> inputs)` | `void` | Commit user inputs (input data submitted to the workflow). |
| `public void commit()` | `void` | Commit all workflow state partitions. |
| `public WorkflowCommitState createNodeState(String newNodeId, String newParentId)` | `WorkflowCommitState` | Create a node-scoped state sharing the same underlying partitions. |
| `public Map<String, Object> getState()` | `Map<String, Object>` | - |
| `public void setState(Map<String, Object> state)` | `void` | - |

## `com.openjiuwen.core.session.store`

公开类型：`3`

### `FileStore`

- 类型：`class`
- 声明：`public class FileStore extends Store`
- 说明：Placeholder file store (not yet implemented).

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object read(Object key)` | `Object` | - |
| `public void write(Map<String, Object> value)` | `void` | - |

### `MemoryStore`

- 类型：`class`
- 声明：`public class MemoryStore extends Store`
- 说明：In-memory store backed by a HashMap.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object read(Object key)` | `Object` | - |
| `public void write(Map<String, Object> value)` | `void` | - |
| `public Map<String, Object> getData()` | `Map<String, Object>` | Get the underlying data map. |

### `Store`

- 类型：`class`
- 声明：`public abstract class Store`
- 说明：Abstract base class for key-value storage.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract Object read(Object key)` | `Object` | Read a value by key (string or dict schema). |
| `public abstract void write(Map<String, Object> value)` | `void` | Write data to the store. |

## `com.openjiuwen.core.session.stream`

公开类型：`9`

### `AsyncStreamQueue`

- 类型：`class`
- 声明：`public class AsyncStreamQueue`
- 说明：Thread-safe blocking stream queue for producer-consumer pattern.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_SEND_ATTEMPT_TIMEOUT_MS` | `long` | `public static final` | `200` | Default timeout for each send attempt in milliseconds. |
| `DEFAULT_MAX_SEND_RETRIES` | `int` | `public static final` | `5` | Maximum number of retries for sending data. |
| `DEFAULT_RECEIVE_TIMEOUT_MS` | `long` | `public static final` | `-1` | Default timeout for receiving data in milliseconds, -1 means no timeout. |
| `DEFAULT_CLOSE_TIMEOUT_MS` | `long` | `public static final` | `5000` | Default timeout for closing the queue in milliseconds. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AsyncStreamQueue(int maxSize)` | Create a stream queue with the specified capacity. |
| `public AsyncStreamQueue()` | Create an unbounded stream queue. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isClosed()` | `boolean` | - |
| `public void send(Object data, long attemptTimeout, int maxRetries)` | `void` | Send data to the queue with retry logic. |
| `public void send(Object data)` | `void` | Send data with default timeout and retries. |
| `public Object receive(long timeoutMs)` | `Object` | Receive data from the queue. |
| `public Object receive()` | `Object` | Receive data with default timeout. |
| `public void close(long timeoutMs)` | `void` | Close the queue and drain remaining items. |
| `public void close()` | `void` | Close with default timeout. |

### `CustomSchema`

- 类型：`class`
- 声明：`public class CustomSchema implements StreamSchema`
- 说明：Custom stream schema allowing arbitrary properties.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `properties` | `Map<String, Object>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CustomSchema()` | - |
| `public CustomSchema(Map<String, Object> properties)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object get(String key)` | `Object` | - |
| `public void put(String key, Object value)` | `void` | - |
| `public Map<String, Object> getProperties()` | `Map<String, Object>` | - |
| `public static CustomSchema fromMap(Map<String, Object> data)` | `CustomSchema` | Validate data from a map. |

### `OutputSchema`

- 类型：`class`
- 声明：`public class OutputSchema implements StreamSchema`
- 说明：Standard output stream schema.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `-` | - |
| `index` | `int` | `private` | `-` | - |
| `payload` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OutputSchema()` | - |
| `public OutputSchema(String type, int index, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |
| `public void setType(String type)` | `void` | - |
| `public int getIndex()` | `int` | - |
| `public void setIndex(int index)` | `void` | - |
| `public Object getPayload()` | `Object` | - |
| `public void setPayload(Object payload)` | `void` | - |
| `public static OutputSchema fromMap(Map<String, Object> data)` | `OutputSchema` | Validate data from a map. |

### `StreamEmitter`

- 类型：`class`
- 声明：`public class StreamEmitter`
- 说明：Stream emitter responsible for pushing stream data to the stream queue.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `END_FRAME` | `String` | `public static final` | `"all streaming outputs finish"` | Sentinel value indicating end of stream. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamEmitter()` | - |
| `public StreamEmitter(AsyncStreamQueue streamQueue)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public AsyncStreamQueue getStreamQueue()` | `AsyncStreamQueue` | - |
| `public void emit(Object streamData)` | `void` | Emit stream data. |
| `public boolean isClosed()` | `boolean` | - |
| `public void close()` | `void` | Close the emitter, sending END_FRAME sentinel. |

### `StreamMode`

- 类型：`enum`
- 声明：`public enum StreamMode`
- 说明：Stream mode definition.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `OUTPUT` | `new StreamMode("output", "Standard stream data defined by the framework")` | - |
| `TRACE` | `new StreamMode("trace", "Trace stream data produced by the graph")` | - |
| `CUSTOM` | `new StreamMode("custom", "Custom stream data defined by the runnable")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `mode` | `String` | `private final` | `-` | - |
| `desc` | `String` | `private final` | `-` | - |
| `options` | `Map<String, Object>` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getMode()` | `String` | - |
| `public String getDesc()` | `String` | - |
| `public Map<String, Object> getOptions()` | `Map<String, Object>` | - |
| `public String toString()` | `String` | - |

### `StreamSchema`

- 类型：`interface`
- 声明：`public interface StreamSchema`
- 说明：Marker interface for stream schema types.

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `StreamWriter`

- 类型：`class`
- 声明：`public class StreamWriter<S extends StreamSchema>`
- 说明：Stream writer that validates and writes stream data to a StreamEmitter.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamWriter(StreamEmitter streamEmitter, Class<S> schemaType, Function<Map<String, Object>, S> validator)` | Create a new StreamWriter. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void write(Object streamData)` | `void` | Write stream data. |
| `protected void doWrite(S validatedData)` | `void` | Perform the actual write. |

### `StreamWriterManager`

- 类型：`class`
- 声明：`public class StreamWriterManager`
- 说明：Manages stream writers for different stream modes.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamWriterManager(StreamEmitter streamEmitter, List<StreamMode> modes)` | - |
| `public StreamWriterManager(StreamEmitter streamEmitter)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static StreamWriterManager createManager(StreamEmitter streamEmitter, List<StreamMode> modes)` | `StreamWriterManager` | Factory method. |
| `public static StreamWriterManager createManager(StreamEmitter streamEmitter)` | `StreamWriterManager` | - |
| `public StreamEmitter getStreamEmitter()` | `StreamEmitter` | - |
| `public void streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean needClose, Consumer<Object> consumer)` | `void` | Iterate over stream output synchronously, invoking the consumer for each item. |
| `public void streamOutput(Consumer<Object> consumer)` | `void` | Stream output with default timeouts. |
| `public Iterator<Object> streamIterator()` | `Iterator<Object>` | Expose stream output as a blocking iterator. |
| `public Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean needClose)` | `Iterator<Object>` | Expose stream output as a blocking iterator with configurable timeouts. |
| `public List<Object> collectStreamOutput()` | `List<Object>` | Collect all stream items into a list (blocking). |
| `public void addWriter(StreamMode key, StreamWriter<?> writer)` | `void` | Add a writer for a stream mode. |
| `public StreamWriter<?> getWriter(StreamMode key)` | `StreamWriter<?>` | Get writer by mode. |
| `public StreamWriter<OutputSchema> getOutputWriter()` | `StreamWriter<OutputSchema>` | Get the output writer. |
| `public StreamWriter<TraceSchema> getTraceWriter()` | `StreamWriter<TraceSchema>` | Get the trace writer. |
| `public StreamWriter<CustomSchema> getCustomWriter()` | `StreamWriter<CustomSchema>` | Get the custom writer. |
| `public StreamWriter<?> removeWriter(StreamMode key)` | `StreamWriter<?>` | Remove a writer by mode. |

### `TraceSchema`

- 类型：`class`
- 声明：`public class TraceSchema implements StreamSchema`
- 说明：Trace stream schema.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `-` | - |
| `payload` | `Object` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TraceSchema()` | - |
| `public TraceSchema(String type, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |
| `public void setType(String type)` | `void` | - |
| `public Object getPayload()` | `Object` | - |
| `public void setPayload(Object payload)` | `void` | - |
| `public static TraceSchema fromMap(Map<String, Object> data)` | `TraceSchema` | Validate data from a map. |

## `com.openjiuwen.core.session.tracer`

公开类型：`12`

### `InvokeType`

- 类型：`enum`
- 声明：`public enum InvokeType`
- 说明：Agent invoke type enum.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `PROMPT` | `new InvokeType("prompt")` | - |
| `LLM` | `new InvokeType("llm")` | - |
| `PLUGIN` | `new InvokeType("plugin")` | - |
| `WORKFLOW` | `new InvokeType("workflow")` | - |
| `CHAIN` | `new InvokeType("chain")` | - |
| `RETRIEVER` | `new InvokeType("retriever")` | - |
| `EVALUATOR` | `new InvokeType("evalutor")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `NodeStatus`

- 类型：`enum`
- 声明：`public enum NodeStatus`
- 说明：Workflow node status for tracing.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `START` | `new NodeStatus("start")` | - |
| `FINISH` | `new NodeStatus("finish")` | - |
| `RUNNING` | `new NodeStatus("running")` | - |
| `INTERRUPTED` | `new NodeStatus("interrupted")` | - |
| `ERROR` | `new NodeStatus("error")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `Span`

- 类型：`class`
- 声明：`public class Span`
- 说明：Base trace span class holding common trace properties.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Span()` | - |
| `public Span(String traceId, String invokeId, String parentInvokeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void update(Map<String, Object> data)` | `void` | Update span attributes from a data map. |
| `public void appendChildInvokeId(String invokeId)` | `void` | Append a child invoke ID. |
| `protected void setField(String name, Object value)` | `void` | - |
| `public Span snapshot()` | `Span` | Create a detached snapshot so previously emitted trace frames are not mutated later. |
| `protected void copyBaseFields(Span copy)` | `void` | - |
| `protected static Map<String, Object> deepCopyMap(Map<?, ?> source)` | `Map<String, Object>` | - |
| `protected static List<Map<String, Object>> deepCopyMapList(List<Map<String, Object>> source)` | `List<Map<String, Object>>` | - |
| `protected static List<Object> deepCopyList(List<?> source)` | `List<Object>` | - |
| `protected static Object deepCopyValue(Object value)` | `Object` | - |
| `public String getTraceId()` | `String` | - |
| `public void setTraceId(String traceId)` | `void` | - |
| `public LocalDateTime getStartTime()` | `LocalDateTime` | - |
| `public void setStartTime(LocalDateTime startTime)` | `void` | - |
| `public LocalDateTime getEndTime()` | `LocalDateTime` | - |
| `public void setEndTime(LocalDateTime endTime)` | `void` | - |
| `public Object getInputs()` | `Object` | - |
| `public void setInputs(Object inputs)` | `void` | - |
| `public Object getOutputs()` | `Object` | - |
| `public void setOutputs(Object outputs)` | `void` | - |
| `public Map<String, Object> getError()` | `Map<String, Object>` | - |
| `public void setError(Map<String, Object> error)` | `void` | - |
| `public String getInvokeId()` | `String` | - |
| `public void setInvokeId(String invokeId)` | `void` | - |
| `public String getParentInvokeId()` | `String` | - |
| `public void setParentInvokeId(String parentInvokeId)` | `void` | - |
| `public List<String> getChildInvokesId()` | `List<String>` | - |
| `public void setChildInvokesId(List<String> childInvokesId)` | `void` | - |
| `public String getStatus()` | `String` | - |
| `public void setStatus(String status)` | `void` | - |
| `public List<Map<String, Object>> getOnInvokeData()` | `List<Map<String, Object>>` | - |
| `public void setOnInvokeData(List<Map<String, Object>> onInvokeData)` | `void` | - |

### `SpanManager`

- 类型：`class`
- 声明：`public class SpanManager`
- 说明：Manages spans during a tracer session.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SpanManager(String traceId)` | - |
| `public SpanManager(String traceId, String parentNodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Span getSpan(String invokeId)` | `Span` | Get a span by invoke ID. |
| `public void popSpan(String invokeId)` | `void` | Remove a span by invoke ID. |
| `public void refreshSpanRecord(String invokeId, Span span)` | `void` | Add or update a span record. |
| `public TraceAgentSpan createAgentSpan(Span parentSpan)` | `TraceAgentSpan` | Create an agent span with optional parent. |
| `public TraceWorkflowSpan createWorkflowSpan(String invokeId, Span parentSpan)` | `TraceWorkflowSpan` | Create a workflow span with explicit invoke ID and optional parent. |
| `public void updateSpan(Span span, Map<String, Object> data)` | `void` | Update a span with data and refresh it in the record. |
| `public Span getLastSpan()` | `Span` | Get the last span in order. |
| `public String getTraceId()` | `String` | - |
| `public String getParentNodeId()` | `String` | - |

### `TraceAgentHandler`

- 类型：`class`
- 声明：`public class TraceAgentHandler extends TraceBaseHandler`
- 说明：Trace handler for agent-level tracing (chain, llm, prompt, plugin, retriever, evaluator, workflow).

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TraceAgentHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String eventName()` | `String` | - |
| `protected Map<String, Object> formatData(Span span)` | `Map<String, Object>` | - |
| `public TraceAgentSpan getTracerAgentSpan(String invokeId)` | `TraceAgentSpan` | Get or create an agent span. |
| `@TriggerEvent public void onChainStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onChainEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onChainError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onLlmStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onLlmEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onLlmError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onPromptStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onPromptEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onPromptError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onPluginStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onPluginEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onPluginError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onRetrieverStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onRetrieverEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onRetrieverError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onEvaluatorStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onEvaluatorEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onEvaluatorError(TraceAgentSpan span, Object error)` | `void` | - |
| `@TriggerEvent public void onWorkflowStart(TraceAgentSpan span, Object inputs, Map<String, Object> instanceInfo)` | `void` | - |
| `@TriggerEvent public void onWorkflowEnd(TraceAgentSpan span, Object outputs)` | `void` | - |
| `@TriggerEvent public void onWorkflowError(TraceAgentSpan span, Object error)` | `void` | - |

### `TraceAgentSpan`

- 类型：`class`
- 声明：`public class TraceAgentSpan extends Span`
- 说明：Agent trace span with invoke type, name, and metadata.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TraceAgentSpan()` | - |
| `public TraceAgentSpan(String traceId, String invokeId, String parentInvokeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void setField(String fieldName, Object value)` | `void` | - |
| `public TraceAgentSpan snapshot()` | `TraceAgentSpan` | - |
| `public String getInvokeType()` | `String` | - |
| `public void setInvokeType(String invokeType)` | `void` | - |
| `public String getName()` | `String` | - |
| `public void setName(String name)` | `void` | - |
| `public String getElapsedTime()` | `String` | - |
| `public void setElapsedTime(String elapsedTime)` | `void` | - |
| `public Map<String, Object> getMetaData()` | `Map<String, Object>` | - |
| `public void setMetaData(Map<String, Object> metaData)` | `void` | - |

### `TraceBaseHandler`

- 类型：`class`
- 声明：`public abstract class TraceBaseHandler extends BaseHandler`
- 说明：Base trace handler providing common span updates and stream writing.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `streamWriter` | `StreamWriter<TraceSchema>` | `protected final` | `-` | - |
| `spanManager` | `SpanManager` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected TraceBaseHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected Map<String, Object> formatData(Span span)` | `Map<String, Object>` | Format span data for stream emission. |
| `protected void emitStreamWriter(Span span)` | `void` | Emit span data to the trace stream writer. |
| `protected void sendData(Span span)` | `void` | Send span data to stream. |
| `protected String getElapsedTime(LocalDateTime startTime, LocalDateTime endTime)` | `String` | Calculate elapsed time string. |
| `protected String getNodeStatus(Span span)` | `String` | Determine node status from span state. |

### `TraceWorkflowHandler`

- 类型：`class`
- 声明：`public class TraceWorkflowHandler extends TraceBaseHandler`
- 说明：Trace handler for workflow-level tracing (component lifecycle events).

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TraceWorkflowHandler(Object owner, StreamWriterManager streamWriterManager, SpanManager spanManager)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String eventName()` | `String` | - |
| `protected Map<String, Object> formatData(Span span)` | `Map<String, Object>` | - |
| `public TraceWorkflowSpan getTracerWorkflowSpan(String invokeId)` | `TraceWorkflowSpan` | Get or create a workflow span. |
| `@TriggerEvent public void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs, boolean needSend, List<String> sourceIds)` | `void` | - |
| `@TriggerEvent public void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend)` | `void` | - |
| `@TriggerEvent public void onPreStream(String invokeId, Object chunk, boolean needSend)` | `void` | - |
| `@TriggerEvent public void onInvoke(String invokeId, Map<String, Object> onInvokeData, Exception exception)` | `void` | - |
| `@TriggerEvent public void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata, boolean needSend)` | `void` | - |
| `@TriggerEvent public void onPostStream(String invokeId, Object chunk)` | `void` | - |
| `@TriggerEvent public void onPostInvoke(String invokeId, Object outputs, Object inputs)` | `void` | - |
| `@TriggerEvent public void onCallDone(String invokeId, Object outputs)` | `void` | - |

### `TraceWorkflowSpan`

- 类型：`class`
- 声明：`public class TraceWorkflowSpan extends Span`
- 说明：Workflow trace span with workflow/component metadata and stream data.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TraceWorkflowSpan()` | - |
| `public TraceWorkflowSpan(String traceId, String invokeId, String parentInvokeId, String parentNodeId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void appendStreamOutput(Object chunk)` | `void` | Append a stream output chunk. |
| `public void appendStreamInput(Object chunk)` | `void` | Append a stream input chunk. |
| `protected void setField(String fieldName, Object value)` | `void` | - |
| `public TraceWorkflowSpan snapshot()` | `TraceWorkflowSpan` | - |
| `public String getExecutionId()` | `String` | - |
| `public void setExecutionId(String executionId)` | `void` | - |
| `public List<String> getSourceIds()` | `List<String>` | - |
| `public void setSourceIds(List<String> sourceIds)` | `void` | - |
| `public String getWorkflowId()` | `String` | - |
| `public void setWorkflowId(String workflowId)` | `void` | - |
| `public String getWorkflowVersion()` | `String` | - |
| `public void setWorkflowVersion(String workflowVersion)` | `void` | - |
| `public String getWorkflowName()` | `String` | - |
| `public void setWorkflowName(String workflowName)` | `void` | - |
| `public String getComponentId()` | `String` | - |
| `public void setComponentId(String componentId)` | `void` | - |
| `public String getComponentName()` | `String` | - |
| `public void setComponentName(String componentName)` | `void` | - |
| `public String getComponentType()` | `String` | - |
| `public void setComponentType(String componentType)` | `void` | - |
| `public String getLoopNodeId()` | `String` | - |
| `public void setLoopNodeId(String loopNodeId)` | `void` | - |
| `public Integer getLoopIndex()` | `Integer` | - |
| `public void setLoopIndex(Integer loopIndex)` | `void` | - |
| `public Map<String, Map<String, Object>> getLlmInvokeData()` | `Map<String, Map<String, Object>>` | - |
| `public void setLlmInvokeData(Map<String, Map<String, Object>> llmInvokeData)` | `void` | - |
| `public String getParentNodeId()` | `String` | - |
| `public void setParentNodeId(String parentNodeId)` | `void` | - |
| `public Object getInteractiveInputs()` | `Object` | - |
| `public void setInteractiveInputs(Object interactiveInputs)` | `void` | - |
| `public List<Object> getStreamInputs()` | `List<Object>` | - |
| `public void setStreamInputs(List<Object> streamInputs)` | `void` | - |
| `public List<Object> getStreamOutputs()` | `List<Object>` | - |
| `public void setStreamOutputs(List<Object> streamOutputs)` | `void` | - |

### `Tracer`

- 类型：`class`
- 声明：`public class Tracer`
- 说明：Central tracer coordinating agent and workflow span managers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Tracer()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void init(StreamWriterManager streamWriterManager, CallbackManager callbackManager)` | `void` | Initialize the tracer with stream and callback managers. |
| `public void registerWorkflowSpanManager(String parentNodeId)` | `void` | Register a workflow span manager for a parent node. |
| `public TraceWorkflowSpan getWorkflowSpan(String invokeId, String parentNodeId)` | `TraceWorkflowSpan` | Get a workflow span by invoke ID and parent node ID. |
| `public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | `void` | Trigger a tracer event through the callback manager. |
| `public void popWorkflowSpan(String invokeId, String parentNodeId)` | `void` | Pop (remove) a workflow span. |
| `public String getTraceId()` | `String` | - |
| `public SpanManager getTracerAgentSpanManager()` | `SpanManager` | - |
| `public Map<String, SpanManager> getTracerWorkflowSpanManagerDict()` | `Map<String, SpanManager>` | - |

### `TracerHandlerName`

- 类型：`enum`
- 声明：`public enum TracerHandlerName`
- 说明：Handler name enum for tracer callbacks.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `TRACE_AGENT` | `new TracerHandlerName("tracer_agent")` | - |
| `TRACER_WORKFLOW` | `new TracerHandlerName("tracer_workflow")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `TracerWorkflowUtils`

- 类型：`class`
- 声明：`public final class TracerWorkflowUtils`
- 说明：Utility class for workflow tracing operations.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void traceWorkflowStart(BaseSession session, Object inputs)` | `void` | Trace workflow start event. |
| `public static void traceComponentBegin(BaseSession session, java.util.List<String> sourceIds)` | `void` | Trace component begin event. |
| `public static void traceComponentInputs(BaseSession session, Map<String, Object> inputs, boolean send)` | `void` | Trace component inputs. |
| `public static void traceComponentStreamInput(BaseSession session, Object chunk, boolean send)` | `void` | Trace component stream input. |
| `public static void traceComponentOutputs(BaseSession session, Object outputs)` | `void` | Trace component outputs. |
| `public static void traceComponentStreamOutput(BaseSession session, Object chunk)` | `void` | Trace component stream output. |
| `public static void traceWorkflowDone(BaseSession session, Object outputs)` | `void` | Trace workflow done event. |
| `public static void traceComponentDone(BaseSession session)` | `void` | Trace component done event. |
| `public static void trace(BaseSession session, Map<String, Object> data)` | `void` | General trace data. |
| `public static void traceError(BaseSession session, Exception error)` | `void` | Trace an error. |
| `public static void traceComponentInteractiveInputs(BaseSession session, Object inputs, boolean send)` | `void` | Trace component interactive inputs. |
| `public static void registerWorkflowSpanManager(BaseSession session)` | `void` | Register a dedicated workflow span manager for nested workflow tracing. |

## `com.openjiuwen.core.session.utils`

公开类型：`2`

### `SessionUtils`

- 类型：`class`
- 声明：`public final class SessionUtils`
- 说明：Session utility methods for nested path operations and dict manipulation.
- 嵌套公开类型：`SessionUtils.EndFrame`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `NESTED_PATH_SPLIT` | `String` | `public static final` | `"."` | - |
| `NESTED_PATH_LIST_SPLIT` | `String` | `public static final` | `"["` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static boolean isRefPath(String path)` | `boolean` | Check if a string is a reference path like "${xxx.yyy}". |
| `public static String extractOriginKey(String key)` | `String` | Extract the origin key from a reference structure. |
| `public static List<Object> splitNestedPath(String nestedKey)` | `List<Object>` | Split a nested path into components. |
| `public static Object getValueByNestedPath(String nestedKey, Map<String, Object> source)` | `Object` | Get a value by nested path from a source map. |
| `public static Object[] rootToPath(String nestedPath, Object source, boolean createIfAbsent)` | `Object[]` | Navigate from root to the final path position. |
| `public static void updateDict(Map<String, Object> update, Map<String, Object> source, boolean ignoreDelete)` | `void` | Update source dict by update dict. |
| `public static void updateDict(Map<String, Object> update, Map<String, Object> source)` | `void` | Update source dict by update dict (default: don't ignore delete). |
| `public static Object expandNestedStructure(Object data)` | `Object` | Expand nested structure. |
| `public static Object getBySchema(Object schema, Map<String, Object> data)` | `Object` | Get value by schema (supports str, list, dict schemas). |
| `public static Object getBySchema(Object schema, Map<String, Object> data, String nestedPath, boolean isRoot)` | `Object` | Get value by schema with optional nested path prefix. |

### `SessionUtils.EndFrame`

- 类型：`class`
- 声明：`public static final class EndFrame`
- 说明：Sentinel class for representing end frame markers.
- 宿主类型：`SessionUtils`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `MESSAGE` | `String` | `public static final` | `"all streaming outputs finish"` | - |

