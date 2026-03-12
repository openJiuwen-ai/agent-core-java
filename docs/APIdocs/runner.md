# Runner 模块 API 文档

> 包路径：`com.openjiuwen.core.runner`

运行入口、回调链、消息队列、远程运行与资源管理。基于 `runner` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `83` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.runner` | 7 |
| `com.openjiuwen.core.runner.base` | 10 |
| `com.openjiuwen.core.runner.callback` | 20 |
| `com.openjiuwen.core.runner.drunner` | 1 |
| `com.openjiuwen.core.runner.drunner.dmessage_queue` | 3 |
| `com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription` | 2 |
| `com.openjiuwen.core.runner.drunner.dmessage_queue.message` | 5 |
| `com.openjiuwen.core.runner.drunner.remote_client` | 5 |
| `com.openjiuwen.core.runner.drunner.server_adapter` | 3 |
| `com.openjiuwen.core.runner.mq` | 8 |
| `com.openjiuwen.core.runner.resourcemanager` | 19 |

## `com.openjiuwen.core.runner`

公开类型：`7`

### `DistributedConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DistributedConfig`
- 说明：Distributed system configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `requestTimeout` | `double` | `private` | `30.0` | - |
| `maxRequestConcurrency` | `int` | `private` | `10000` | - |
| `messageQueueConfig` | `MessageQueueConfig` | `private` | `new MessageQueueConfig()` | - |
| `agentTopicTemplate` | `String` | `private` | `"openjiuwen.single_agent.{agent_id}.{version}"` | - |
| `replyTopicTemplate` | `String` | `private` | `"openjiuwen.reply.runner.{instance_id}"` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getAgentTopicTemplate(String envPrefix)` | `String` | Get agent topic template with environment prefix. |
| `public String getReplyTopicTemplate(String envPrefix)` | `String` | Get reply topic template with environment prefix. |

### `MessageQueueConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MessageQueueConfig`
- 说明：Message queue configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `MessageQueueType.PULSAR.getValue()` | - |
| `pulsarConfig` | `PulsarConfig` | `private` | `-` | - |

### `MessageQueueType`

- 类型：`enum`
- 声明：`public enum MessageQueueType`
- 说明：Message queue type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `PULSAR` | `new MessageQueueType("pulsar")` | - |
| `FAKE` | `new MessageQueueType("fake")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `PulsarConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class PulsarConfig`
- 说明：Pulsar message queue configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `url` | `String` | `private` | `-` | - |
| `maxWorkers` | `int` | `private` | `8` | - |

### `Runner`

- 类型：`class`
- 声明：`public final class Runner`
- 说明：Runner singleton class that proxies all calls to the global runner instance.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ResourceMgr resourceMgr()` | `ResourceMgr` | Get the resource manager for workflow, agent, agent_group, tool, model, prompt... |
| `public static LocalMessageQueue pubsub()` | `LocalMessageQueue` | Get the local message queue for publish/subscribe communication. |
| `public static CallbackFramework callbackFramework()` | `CallbackFramework` | Get the callback framework. |
| `public static void setConfig(RunnerConfig config)` | `void` | Set the runner configuration with provided config object. |
| `public static RunnerConfig getConfig()` | `RunnerConfig` | Retrieve the current runner configuration. |
| `public static boolean start()` | `boolean` | Start the runner and its associated components, such as message queue. |
| `public static boolean stop()` | `boolean` | Stop the runner and clean up resources. |
| `public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context)` | `Object` | Execute a workflow with given inputs. |
| `public static Iterator<Object> runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a workflow with streaming output support. |
| `public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context)` | `Object` | Execute a single agent with given inputs. |
| `public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a single agent with streaming output support. |
| `public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context)` | `Object` | Execute a group of agents with given inputs. |
| `public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a group of agents with streaming output support. |
| `public static void release(String sessionId)` | `void` | Release resources associated with a session. |

### `RunnerConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class RunnerConfig`
- 说明：Runner global configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `distributedMode` | `boolean` | `private` | `true` | - |
| `distributedConfig` | `DistributedConfig` | `private` | `new DistributedConfig()` | - |
| `envPrefix` | `String` | `private` | `""` | - |
| `instanceId` | `String` | `private` | `UUID.randomUUID().toString()` | - |
| `checkpointerConfig` | `Map<String, Object>` | `private` | `-` | Checkpointer configuration. |
| `DEFAULT` | `RunnerConfig` | `public static final` | `RunnerConfig.builder().distributedMode(false).distributedConfig(DistributedConfig.builder().requestTimeout(30.0).messageQueueConfig(MessageQueueConfig.builder().type(MessageQueueType.FAKE.getValue()).build()).build()).build()` | Default runner configuration (non-distributed, fake MQ). |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String agentTopicTemplate()` | `String` | Get agent topic template with environment prefix. |
| `public String replyTopicTemplate()` | `String` | Get reply topic template with environment prefix. |
| `public static void setRunnerConfig(RunnerConfig config)` | `void` | Set the global runner configuration. |
| `public static RunnerConfig getRunnerConfig()` | `RunnerConfig` | Get the global runner configuration. |

### `RunnerImpl`

- 类型：`class`
- 声明：`public class RunnerImpl`
- 说明：Runner implementation class.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RunnerImpl()` | - |
| `public RunnerImpl(String runnerId, RunnerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ResourceMgr getResourceMgr()` | `ResourceMgr` | Get the resource manager for workflow, agent, agent_group, tool, model, prompt... |
| `public LocalMessageQueue getPubsub()` | `LocalMessageQueue` | Get the local message queue for publish/subscribe communication. |
| `public CallbackFramework getCallbackFramework()` | `CallbackFramework` | Get the callback framework. |
| `public void setConfig(RunnerConfig config)` | `void` | Set the runner configuration. |
| `public RunnerConfig getConfig()` | `RunnerConfig` | Retrieve the current runner configuration. |
| `public boolean start()` | `boolean` | Start the runner and its associated components, such as message queue. |
| `public boolean stop()` | `boolean` | Stop the runner and clean up resources. |
| `public Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context)` | `Object` | Execute a workflow with given inputs. |
| `public Iterator<Object> runWorkflowStreaming(Object workflow, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a workflow with streaming output support. |
| `public Object runAgent(Object agent, Object inputs, Object session, ModelContext context)` | `Object` | Execute a single agent with given inputs. |
| `public Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a single agent with streaming output support. |
| `public Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context)` | `Object` | Execute a group of agents with given inputs. |
| `public Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session, ModelContext context, List<StreamMode> streamModes)` | `Iterator<Object>` | Execute a group of agents with streaming output support. |
| `public void release(String sessionId)` | `void` | Release resources associated with a session. |
| `public static String generateWorkflowKey(String workflowId, String workflowVersion)` | `String` | Generate workflow key from ID and version (matches Python's generate_workflow_key). |

## `com.openjiuwen.core.runner.base`

公开类型：`10`

### `AgentGroupProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface AgentGroupProvider<T> extends Supplier<T>`
- 说明：Provider functional interface for creating AgentGroup instances.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `AgentProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface AgentProvider<T> extends Supplier<T>`
- 说明：Provider functional interface for creating Agent instances.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `Error`

- 类型：`class`
- 声明：`public final class Error<T> implements Result<T>`
- 说明：Represents a failed operation result following the Result pattern.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Error(Exception error)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isOk()` | `boolean` | - |
| `public boolean isError()` | `boolean` | - |
| `public T getValue()` | `T` | - |
| `public Exception getError()` | `Exception` | - |
| `public String toString()` | `String` | - |

### `ModelProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface ModelProvider extends Supplier<Model>`
- 说明：Provider functional interface for creating Model instances.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

### `Ok`

- 类型：`class`
- 声明：`public final class Ok<T> implements Result<T>`
- 说明：Represents a successful operation result following the Result pattern.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Ok(T value)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isOk()` | `boolean` | - |
| `public boolean isError()` | `boolean` | - |
| `public T getValue()` | `T` | - |
| `public Exception getError()` | `Exception` | - |
| `public String toString()` | `String` | - |

### `Result`

- 类型：`interface`
- 声明：`public sealed interface Result<T> permits Ok, Error`
- 说明：Result type for type-safe error handling.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `boolean isOk()` | `boolean` | - |
| `boolean isError()` | `boolean` | - |
| `T getValue()` | `T` | - |
| `Exception getError()` | `Exception` | - |

### `Tag`

- 类型：`class`
- 声明：`public final class Tag`
- 说明：Tag type constants for categorizing and filtering resources.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ALL` | `String` | `public static final` | `"*"` | Special tag matching all resources. |
| `GLOBAL` | `String` | `public static final` | `"__global__"` | Default tag for untagged resources. |
| `ACTIVE` | `String` | `public static final` | `"__active__"` | Active state tag. |
| `INACTIVE` | `String` | `public static final` | `"__inactive__"` | Inactive state tag. |

### `TagMatchStrategy`

- 类型：`enum`
- 声明：`public enum TagMatchStrategy`
- 说明：Strategy for matching multiple tags when querying or filtering resources.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `ALL` | `new TagMatchStrategy("all")` | Resource must contain ALL specified tags. |
| `ANY` | `new TagMatchStrategy("any")` | Resource must contain ANY of the specified tags. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `TagUpdateStrategy`

- 类型：`enum`
- 声明：`public enum TagUpdateStrategy`
- 说明：Strategy for updating resource tags.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `MERGE` | `new TagUpdateStrategy("merge")` | Merge new tags with existing tags. |
| `REPLACE` | `new TagUpdateStrategy("replace")` | Replace all existing tags with new tags. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `WorkflowProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface WorkflowProvider extends Supplier<Workflow>`
- 说明：Provider functional interface for creating Workflow instances.
- 注解：`@FunctionalInterface`

显式公开成员较少，当前源码主要通过字段访问器、继承关系或运行时约定暴露能力。

## `com.openjiuwen.core.runner.callback`

公开类型：`20`

### `AuthFilter`

- 类型：`class`
- 声明：`public class AuthFilter extends EventFilter`
- 说明：Authorization filter for role-based access control.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `requiredRole` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AuthFilter(String requiredRole)` | - |
| `public AuthFilter(String requiredRole, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

### `CallbackChain`

- 类型：`class`
- 声明：`public class CallbackChain`
- 说明：Manages sequential execution of callbacks with rollback support.
- 嵌套公开类型：`CallbackChain.ExceptionContext`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `callbacks` | `List<CallbackInfo>` | `private final` | `new ArrayList<>()` | - |
| `rollbackHandlers` | `Map<Function<Map<String, Object>, Object>, Consumer<ChainContext>>` | `private final` | `new HashMap<>()` | - |
| `errorHandlers` | `Map<Function<Map<String, Object>, Object>, Function<ExceptionContext, Object>>` | `private final` | `new HashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CallbackChain(String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public List<CallbackInfo> getCallbacks()` | `List<CallbackInfo>` | - |
| `public void add(CallbackInfo callbackInfo, Consumer<ChainContext> rollbackHandler, Function<ExceptionContext, Object> errorHandler)` | `void` | Add callback to the chain. |
| `public void remove(Function<Map<String, Object>, Object> callback)` | `void` | Remove callback from the chain. |
| `public ChainResult execute(ChainContext context)` | `ChainResult` | Execute the callback chain. |

### `CallbackChain.ExceptionContext`

- 类型：`record`
- 声明：`public record ExceptionContext(Exception exception, ChainContext chainContext)`
- 说明：Context passed to error handlers: the exception + the chain context.
- 宿主类型：`CallbackChain`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `exception` | `Exception` | `private final` | `-` | - |
| `chainContext` | `ChainContext` | `private final` | `-` | - |

### `CallbackFramework`

- 类型：`class`
- 声明：`public class CallbackFramework`
- 说明：Production-ready callback framework for Java.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `callbacks` | `Map<String, List<CallbackInfo>>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `chains` | `Map<String, CallbackChain>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `filters` | `Map<String, List<EventFilter>>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `globalFilters` | `List<EventFilter>` | `private final` | `Collections.synchronizedList(new ArrayList<>())` | - |
| `callbackFilters` | `Map<Function<Map<String, Object>, Object>, List<EventFilter>>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `hooks` | `Map<String, Map<HookType, List<Consumer<Map<String, Object>>>>>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `enableMetrics` | `boolean` | `private final` | `-` | - |
| `metrics` | `Map<String, CallbackMetrics>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `enableLogging` | `boolean` | `private final` | `-` | - |
| `circuitBreakers` | `Map<String, CircuitBreakerFilter>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `eventHistory` | `LinkedList<Map<String, Object>>` | `private final` | `new LinkedList<>()` | - |
| `enableHistory` | `boolean` | `private` | `false` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CallbackFramework()` | - |
| `public CallbackFramework(boolean enableMetrics, boolean enableLogging)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, List<CallbackInfo>> getCallbacks()` | `Map<String, List<CallbackInfo>>` | - |
| `public Map<String, CallbackChain> getChains()` | `Map<String, CallbackChain>` | - |
| `public Map<String, CircuitBreakerFilter> getCircuitBreakers()` | `Map<String, CircuitBreakerFilter>` | - |
| `public Map<Function<Map<String, Object>, Object>, List<EventFilter>> getCallbackFilters()` | `Map<Function<Map<String, Object>, Object>, List<EventFilter>>` | - |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority, boolean once, String namespace, Set<String> tags, List<EventFilter> eventFilters, Consumer<ChainContext> rollbackHandler, Function<CallbackChain.ExceptionContext, Object> errorHandler, int maxRetries, double retryDelay, Double timeout, String callbackName)` | `CallbackInfo` | Register a callback for an event. |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, int priority, String callbackName)` | `CallbackInfo` | Simplified register with fewer parameters. |
| `public CallbackInfo register(String event, Function<Map<String, Object>, Object> callback, String callbackName)` | `CallbackInfo` | Register with default priority. |
| `public void unregister(String event, Function<Map<String, Object>, Object> callback)` | `void` | Unregister a callback from an event. |
| `public void unregisterNamespace(String namespace)` | `void` | Unregister all callbacks in a namespace. |
| `public void unregisterByTags(Set<String> tags)` | `void` | Unregister callbacks matching any of the given tags. |
| `public void unregisterEvent(String event)` | `void` | Unregister all callbacks for a specific event. |
| `public List<Object> trigger(String event, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | Trigger an event and execute all registered callbacks. |
| `public List<Object> trigger(String event, Map<String, Object> kwargs)` | `List<Object>` | Trigger with just event name and kwargs. |
| `public List<Object> trigger(String event)` | `List<Object>` | Trigger with just event name. |
| `public ChainResult triggerChain(String event, Object[] args, Map<String, Object> kwargs)` | `ChainResult` | Trigger callbacks as a chain with data flow. |
| `public List<Object> triggerParallel(String event, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | Trigger callbacks in parallel (concurrent execution). |
| `public Object triggerUntil(String event, Predicate<Object> condition, Object[] args, Map<String, Object> kwargs)` | `Object` | Trigger callbacks until a condition is satisfied. |
| `public List<Object> triggerWithTimeout(String event, double timeoutSeconds, Object[] args, Map<String, Object> kwargs)` | `List<Object>` | Trigger event with timeout control. |
| `public Iterator<Object> triggerStream(String event, Iterator<?> inputStream, Object[] args, Map<String, Object> kwargs)` | `Iterator<Object>` | Trigger event for each item in an input iterator (stream processing). |
| `public void addFilter(String event, EventFilter filter)` | `void` | Add a filter to a specific event. |
| `public void addGlobalFilter(EventFilter filter)` | `void` | Add a filter that applies to all events. |
| `public void addCircuitBreaker(String event, CallbackInfo callback, int failureThreshold, double timeout)` | `void` | Add circuit breaker protection to a callback. |
| `public void addHook(String event, HookType hookType, Consumer<Map<String, Object>> hook)` | `void` | Add a lifecycle hook to an event. |
| `public Map<String, Map<String, Object>> getMetrics(String event, String callback)` | `Map<String, Map<String, Object>>` | Get performance metrics for callbacks. |
| `public Map<String, Map<String, Object>> getMetrics()` | `Map<String, Map<String, Object>>` | Get all metrics. |
| `public void resetMetrics()` | `void` | Reset all performance metrics. |
| `public List<Map<String, Object>> getSlowCallbacks(double threshold)` | `List<Map<String, Object>>` | Get callbacks with average execution time above threshold. |
| `public void enableEventHistory(boolean enabled)` | `void` | Enable or disable event history recording. |
| `public List<Map<String, Object>> getEventHistory(String event, Long since)` | `List<Map<String, Object>>` | Get recorded event history. |
| `public void replayEvents(Long since)` | `void` | Replay recorded events. |
| `public List<String> listEvents(String namespace)` | `List<String>` | List all registered events. |
| `public List<Map<String, Object>> listCallbacks(String event)` | `List<Map<String, Object>>` | List all callbacks registered for an event. |
| `public Map<String, Object> getStatistics()` | `Map<String, Object>` | Get overall framework statistics. |

### `CallbackInfo`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class CallbackInfo`
- 说明：Metadata and configuration for a registered callback.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `callback` | `Function<Map<String, Object>, Object>` | `private` | `-` | The callback function. |
| `priority` | `int` | `private` | `0` | Execution priority (higher executes first). |
| `once` | `boolean` | `private` | `false` | Whether callback should execute only once. |
| `enabled` | `boolean` | `private` | `true` | Whether callback is currently enabled. |
| `namespace` | `String` | `private` | `"default"` | Namespace for grouping callbacks. |
| `tags` | `Set<String>` | `private` | `new HashSet<>()` | Set of tags for filtering. |
| `maxRetries` | `int` | `private` | `0` | Maximum retry attempts on failure. |
| `retryDelay` | `double` | `private` | `0.0` | Delay between retries in seconds. |
| `timeout` | `Double` | `private` | `-` | Execution timeout in seconds. |
| `createdAt` | `double` | `private` | `System.currentTimeMillis() / 1000.0` | Timestamp when callback was registered (epoch seconds). |
| `callbackName` | `String` | `private` | `-` | Name of the callback for logging purposes. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getCallbackDisplayName()` | `String` | Get the callback name for logging/metrics purposes. |

### `CallbackMetrics`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class CallbackMetrics`
- 说明：Performance metrics for callback execution.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `callCount` | `int` | `private` | `0` | - |
| `totalTime` | `double` | `private` | `0.0` | - |
| `minTime` | `double` | `private` | `Double.MAX_VALUE` | - |
| `maxTime` | `double` | `private` | `0.0` | - |
| `errorCount` | `int` | `private` | `0` | - |
| `lastCallTime` | `Double` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public synchronized void update(double executionTime, boolean isError)` | `void` | Update metrics with new execution data. |
| `public double getAvgTime()` | `double` | Calculate average execution time. |
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Convert metrics to dictionary format. |

### `ChainAction`

- 类型：`enum`
- 声明：`public enum ChainAction`
- 说明：Actions that callbacks can return to control chain execution.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `CONTINUE` | `new ChainAction("continue")` | Continue to next callback in chain. |
| `BREAK` | `new ChainAction("break")` | Break the chain and return current result. |
| `RETRY` | `new ChainAction("retry")` | Retry current callback. |
| `ROLLBACK` | `new ChainAction("rollback")` | Rollback all executed callbacks. |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `ChainContext`

- 类型：`class`
- 声明：`@Data public class ChainContext`
- 说明：Execution context for callback chains.
- 注解：`@Data`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `event` | `String` | `private final` | `-` | Name of the event being processed. |
| `initialArgs` | `Object[]` | `private final` | `-` | Original positional arguments. |
| `initialKwargs` | `Map<String, Object>` | `private final` | `-` | Original keyword arguments. |
| `results` | `List<Object>` | `private final` | `new ArrayList<>()` | List of results from executed callbacks. |
| `metadata` | `Map<String, Object>` | `private final` | `new HashMap<>()` | Arbitrary metadata for sharing data. |
| `currentIndex` | `int` | `private` | `0` | Index of currently executing callback. |
| `completed` | `boolean` | `private` | `false` | Whether chain completed successfully. |
| `rolledBack` | `boolean` | `private` | `false` | Whether chain was rolled back. |
| `startTime` | `long` | `private final` | `-` | Timestamp when chain execution started (epoch millis). |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChainContext(String event, Object[] initialArgs, Map<String, Object> initialKwargs)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getLastResult()` | `Object` | Get the result from the previous callback. |
| `public List<Object> getAllResults()` | `List<Object>` | Get all results from executed callbacks. |
| `public void setMetadata(String key, Object value)` | `void` | Store metadata in the context. |
| `public Object getMetadata(String key, Object defaultValue)` | `Object` | Retrieve metadata from the context. |
| `public double getElapsedTime()` | `double` | Calculate elapsed time since chain start. |

### `ChainResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ChainResult`
- 说明：Result of callback chain execution.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `action` | `ChainAction` | `private` | `-` | Final action taken by the chain. |
| `result` | `Object` | `private` | `-` | Final result value. |
| `context` | `ChainContext` | `private` | `-` | The chain execution context. |
| `error` | `Exception` | `private` | `-` | Exception if chain failed. |

### `CircuitBreakerFilter`

- 类型：`class`
- 声明：`public class CircuitBreakerFilter extends EventFilter`
- 说明：Circuit breaker pattern implementation.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `failureThreshold` | `int` | `private final` | `-` | - |
| `timeout` | `double` | `private final` | `-` | - |
| `failures` | `Map<String, Integer>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `lastFailureTime` | `Map<String, Double>` | `private final` | `new ConcurrentHashMap<>()` | - |
| `isOpen` | `Map<String, Boolean>` | `private final` | `new ConcurrentHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CircuitBreakerFilter()` | - |
| `public CircuitBreakerFilter(int failureThreshold, double timeout)` | - |
| `public CircuitBreakerFilter(int failureThreshold, double timeout, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Integer> getFailures()` | `Map<String, Integer>` | - |
| `public synchronized FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |
| `public synchronized void recordSuccess(String event, CallbackInfo callback)` | `void` | Record successful execution. |
| `public synchronized void recordFailure(String event, CallbackInfo callback)` | `void` | Record failed execution and potentially open circuit. |

### `ConditionalFilter`

- 类型：`class`
- 声明：`public class ConditionalFilter extends EventFilter`
- 说明：Conditional filter based on custom predicate.
- 嵌套公开类型：`ConditionalFilter.ConditionPredicate`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `condition` | `ConditionPredicate` | `private final` | `-` | - |
| `actionOnFalse` | `FilterAction` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ConditionalFilter(ConditionPredicate condition)` | - |
| `public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

### `ConditionalFilter.ConditionPredicate`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface ConditionPredicate`
- 说明：Predicate function: (event, callback, args, kwargs) -> boolean
- 宿主类型：`ConditionalFilter`
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `boolean test(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `boolean` | - |

### `EventFilter`

- 类型：`class`
- 声明：`public class EventFilter`
- 说明：Base class for event filters.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EventFilter()` | - |
| `public EventFilter(String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | Filter logic to execute before callback. |

### `FilterAction`

- 类型：`enum`
- 声明：`public enum FilterAction`
- 说明：Actions that filters can return to control callback execution.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `CONTINUE` | `new FilterAction("continue")` | Continue with callback execution. |
| `STOP` | `new FilterAction("stop")` | Stop the entire event processing. |
| `SKIP` | `new FilterAction("skip")` | Skip current callback and continue to next. |
| `MODIFY` | `new FilterAction("modify")` | Modify arguments and continue. |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `FilterResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class FilterResult`
- 说明：Result returned by event filters.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `action` | `FilterAction` | `private` | `-` | The action to take (CONTINUE, STOP, SKIP, MODIFY). |
| `modifiedArgs` | `Object[]` | `private` | `-` | New positional arguments if action is MODIFY. |
| `modifiedKwargs` | `Map<String, Object>` | `private` | `-` | New keyword arguments if action is MODIFY. |
| `reason` | `String` | `private` | `-` | Optional reason for the action taken. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static FilterResult continueResult()` | `FilterResult` | Create a CONTINUE result. |
| `public static FilterResult continueResult(Object[] args, Map<String, Object> kwargs)` | `FilterResult` | Create a CONTINUE result with modified arguments. |
| `public static FilterResult skipResult(String reason)` | `FilterResult` | Create a SKIP result with reason. |
| `public static FilterResult stopResult(String reason)` | `FilterResult` | Create a STOP result with reason. |
| `public static FilterResult modifyResult(Object[] modifiedArgs, Map<String, Object> modifiedKwargs)` | `FilterResult` | Create a MODIFY result with new arguments. |

### `HookType`

- 类型：`enum`
- 声明：`public enum HookType`
- 说明：Types of hooks that can be registered for lifecycle events.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `BEFORE` | `new HookType("before")` | Executed before event processing. |
| `AFTER` | `new HookType("after")` | Executed after event processing. |
| `ERROR` | `new HookType("error")` | Executed when an error occurs. |
| `CLEANUP` | `new HookType("cleanup")` | Executed during cleanup phase. |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `LoggingFilter`

- 类型：`class`
- 声明：`public class LoggingFilter extends EventFilter`
- 说明：Filter for logging callback execution.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoggingFilter()` | - |
| `public LoggingFilter(Logger logger, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

### `ParamModifyFilter`

- 类型：`class`
- 声明：`public class ParamModifyFilter extends EventFilter`
- 说明：Filter for modifying callback arguments.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modifier` | `BiFunction<Object[], Map<String, Object>, Object[]>` | `private final` | `-` | Modifier that takes (args, kwargs) and returns a two-element array: [newArgs, newKwargs]. |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier)` | - |
| `public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

### `RateLimitFilter`

- 类型：`class`
- 声明：`public class RateLimitFilter extends EventFilter`
- 说明：Filter to limit callback execution rate.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `maxCalls` | `int` | `private final` | `-` | - |
| `timeWindow` | `double` | `private final` | `-` | - |
| `callTimes` | `Map<String, Deque<Double>>` | `private final` | `new ConcurrentHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RateLimitFilter(int maxCalls, double timeWindow)` | - |
| `public RateLimitFilter(int maxCalls, double timeWindow, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public synchronized FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

### `ValidationFilter`

- 类型：`class`
- 声明：`public class ValidationFilter extends EventFilter`
- 说明：Filter for validating callback arguments.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `validator` | `Predicate<Map<String, Object>>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ValidationFilter(Predicate<Map<String, Object>> validator)` | - |
| `public ValidationFilter(Predicate<Map<String, Object>> validator, String name)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs)` | `FilterResult` | - |

## `com.openjiuwen.core.runner.drunner`

公开类型：`1`

### `DistributedRunner`

- 类型：`class`
- 声明：`public final class DistributedRunner`
- 说明：Lightweight runtime holder for distributed-runner components.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static synchronized void ensureStarted()` | `void` | - |
| `public static MessageQueueBase messageQueue()` | `MessageQueueBase` | - |
| `public static ReplyTopicSubscription replySubscription()` | `ReplyTopicSubscription` | - |
| `public static synchronized void shutdown()` | `void` | - |
| `public static String replyTopic()` | `String` | - |
| `public static String agentTopic(String agentId, String version)` | `String` | - |

## `com.openjiuwen.core.runner.drunner.dmessage_queue`

公开类型：`3`

### `FakeMessageQueue`

- 类型：`class`
- 声明：`public class FakeMessageQueue extends MessageQueueBase`
- 说明：In-memory fake MQ used by the distributed-runner compatibility layer.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void start()` | `void` | - |
| `public void stop()` | `void` | - |
| `public SubscriptionBase subscribe(String topic)` | `SubscriptionBase` | - |
| `public void unsubscribe(String topic)` | `void` | - |
| `public void produceMessage(String topic, QueueMessage message)` | `void` | - |

### `MessageQueueFactory`

- 类型：`class`
- 声明：`public final class MessageQueueFactory`
- 说明：Factory for distributed-runner message queues.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static MessageQueueBase create(MessageQueueConfig config)` | `MessageQueueBase` | - |

### `MessageSerializer`

- 类型：`class`
- 声明：`public final class MessageSerializer`
- 说明：JSON serializer for distributed-runner messages.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static byte[] serializeMessage(DmqMessage message) throws Exception` | `byte[]` | - |
| `public static DmqMessage deserializeMessage(byte[] bytes) throws Exception` | `DmqMessage` | - |

## `com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription`

公开类型：`2`

### `ReplyTopicSubscription`

- 类型：`class`
- 声明：`public class ReplyTopicSubscription`
- 说明：Listens on a reply topic and dispatches responses to collectors.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ReplyTopicSubscription(MessageQueueBase mq, String topic)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void activate()` | `void` | - |
| `public void deactivate()` | `void` | - |
| `public ResponseCollector registerCollector(String messageId, String remoteId, String requestId, Double ttlSeconds)` | `ResponseCollector` | - |
| `public void unregisterCollector(String messageId, String remoteId, String requestId)` | `void` | - |
| `public String getTopic()` | `String` | - |

### `ResponseCollector`

- 类型：`class`
- 声明：`public class ResponseCollector`
- 说明：Collects responses for one distributed request.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ResponseCollector(String messageId, String receiverId, String requestId, Double ttlSeconds)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void putMessage(DmqResponseMessage message)` | `void` | - |
| `public Object result(Double timeoutSeconds) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Double timeoutSeconds)` | `Iterator<Object>` | - |
| `public void close()` | `void` | - |

## `com.openjiuwen.core.runner.drunner.dmessage_queue.message`

公开类型：`5`

### `DMessageType`

- 类型：`enum`
- 声明：`public enum DMessageType`
- 说明：Distributed message type.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `INPUT` | `new DMessageType()` | - |
| `STOP` | `new DMessageType()` | - |
| `OUTPUT` | `new DMessageType()` | - |

### `DmqMessage`

- 类型：`class`
- 声明：`public abstract class DmqMessage extends QueueMessage`
- 说明：Base distributed-runner queue message.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `body` | `Object` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object getPayload()` | `Object` | - |
| `public void setPayload(Object payload)` | `void` | - |
| `public Object getBody()` | `Object` | - |
| `public void setBody(Object body)` | `void` | - |

### `DmqRequestMessage`

- 类型：`class`
- 声明：`@Data @EqualsAndHashCode(callSuper = true) public class DmqRequestMessage extends DmqMessage`
- 说明：Distributed request message.
- 注解：`@Data`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `DMessageType` | `private` | `DMessageType.INPUT` | - |
| `replyTopic` | `String` | `private` | `""` | - |
| `requestId` | `String` | `private` | `""` | - |
| `senderId` | `String` | `private` | `""` | - |
| `receiverId` | `String` | `private` | `""` | - |
| `enableStream` | `boolean` | `private` | `-` | - |
| `expireAt` | `Double` | `private` | `-` | - |

### `DmqResponseMessage`

- 类型：`class`
- 声明：`@Data @EqualsAndHashCode(callSuper = true) public class DmqResponseMessage extends DmqMessage`
- 说明：Distributed response message.
- 注解：`@Data`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `DMessageType` | `private` | `DMessageType.OUTPUT` | - |
| `resultType` | `ResultType` | `private` | `ResultType.MESSAGE` | - |
| `requestId` | `String` | `private` | `""` | - |
| `senderId` | `String` | `private` | `""` | - |
| `receiverId` | `String` | `private` | `""` | - |
| `seq` | `int` | `private` | `-` | - |
| `lastChunk` | `boolean` | `private` | `-` | - |
| `expireAt` | `Double` | `private` | `-` | - |

### `ResultType`

- 类型：`enum`
- 声明：`public enum ResultType`
- 说明：Remote result type.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `MESSAGE` | `new ResultType()` | - |
| `ERROR` | `new ResultType()` | - |

## `com.openjiuwen.core.runner.drunner.remote_client`

公开类型：`5`

### `MqRemoteClient`

- 类型：`class`
- 声明：`public class MqRemoteClient implements RemoteClient`
- 说明：MQ-backed remote client.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MqRemoteClient(RemoteClientConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void start()` | `void` | - |
| `public void stop()` | `void` | - |
| `public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Iterator<Object>` | - |

### `ProtocolEnum`

- 类型：`enum`
- 声明：`public enum ProtocolEnum`
- 说明：Supported remote transport protocols.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `MQ` | `new ProtocolEnum()` | - |

### `RemoteAgent`

- 类型：`class`
- 声明：`public class RemoteAgent`
- 说明：Remote-agent facade.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RemoteAgent(String agentId, String version, String description, String topic, ProtocolEnum protocol, Map<String, Object> config)` | - |
| `public RemoteAgent(String agentId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Object` | - |
| `public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Iterator<Object>` | - |

### `RemoteClient`

- 类型：`interface`
- 声明：`public interface RemoteClient`
- 说明：Remote-client abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `void start()` | `void` | - |
| `void stop()` | `void` | - |
| `Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Object` | - |
| `Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception` | `Iterator<Object>` | - |

### `RemoteClientConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class RemoteClientConfig`
- 说明：Remote client configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `version` | `String` | `private` | `-` | - |
| `name` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |
| `protocol` | `ProtocolEnum` | `private` | `ProtocolEnum.MQ` | - |
| `type` | `String` | `private` | `-` | - |
| `topic` | `String` | `private` | `-` | - |
| `url` | `String` | `private` | `-` | - |
| `kwargs` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

## `com.openjiuwen.core.runner.drunner.server_adapter`

公开类型：`3`

### `AgentAdapter`

- 类型：`class`
- 声明：`public class AgentAdapter`
- 说明：Exposes a local agent over the distributed-runner MQ transport.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentAdapter(String agentId, String version)` | - |
| `public AgentAdapter(String agentId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void start()` | `void` | - |
| `public void stop()` | `void` | - |

### `MqMessageUtils`

- 类型：`class`
- 声明：`public final class MqMessageUtils`
- 说明：Helpers for distributed MQ response construction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static DmqResponseMessage buildStreamResponse(DmqRequestMessage request, String senderId, Object payload, int seq, boolean last)` | `DmqResponseMessage` | - |
| `public static DmqResponseMessage buildFinalResponse(DmqRequestMessage request, String senderId, int seq)` | `DmqResponseMessage` | - |
| `public static DmqResponseMessage buildBatchResponse(DmqRequestMessage request, String senderId, Object result)` | `DmqResponseMessage` | - |
| `public static DmqResponseMessage buildErrorResponse(DmqRequestMessage request, String senderId, Exception error)` | `DmqResponseMessage` | - |

### `MqServerAdapter`

- 类型：`class`
- 声明：`public class MqServerAdapter`
- 说明：MQ-based server adapter for distributed-runner requests.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MqServerAdapter(String adapterId, String topic, Function<Map<String, Object>, Object> invokeHandler, Function<Map<String, Object>, Iterator<Object>> streamHandler)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void start()` | `void` | - |
| `public void stop()` | `void` | - |

## `com.openjiuwen.core.runner.mq`

公开类型：`8`

### `InvokeQueueMessage`

- 类型：`class`
- 声明：`public class InvokeQueueMessage extends QueueMessage`
- 说明：Message for invoke (request-response) pattern.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `response` | `CompletableFuture<Object>` | `private final` | `new CompletableFuture<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InvokeQueueMessage()` | - |
| `public InvokeQueueMessage(String messageId, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public CompletableFuture<Object> getResponse()` | `CompletableFuture<Object>` | - |

### `LocalMessageQueue`

- 类型：`class`
- 声明：`public class LocalMessageQueue`
- 说明：No-op local message queue stub.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean start()` | `boolean` | - |
| `public boolean stop()` | `boolean` | - |

### `MessageQueueBase`

- 类型：`class`
- 声明：`public abstract class MessageQueueBase`
- 说明：Abstract message queue supporting pub-sub topics.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract void start()` | `void` | - |
| `public abstract void stop()` | `void` | - |
| `public abstract SubscriptionBase subscribe(String topic)` | `SubscriptionBase` | - |
| `public abstract void unsubscribe(String topic)` | `void` | - |
| `public abstract void produceMessage(String topic, QueueMessage message)` | `void` | - |

### `MessageQueueInMemory`

- 类型：`class`
- 声明：`public class MessageQueueInMemory extends MessageQueueBase`
- 说明：In-memory message queue with topic-based routing.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MessageQueueInMemory(int queueMaxSize, long timeoutMs)` | - |
| `public MessageQueueInMemory()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void start()` | `void` | - |
| `public void stop()` | `void` | - |
| `public SubscriptionBase subscribe(String topic)` | `SubscriptionBase` | - |
| `public void unsubscribe(String topic)` | `void` | - |
| `public void produceMessage(String topic, QueueMessage message)` | `void` | - |

### `QueueMessage`

- 类型：`class`
- 声明：`public class QueueMessage`
- 说明：Base message object for message queue communication.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `messageId` | `String` | `private` | `""` | - |
| `payload` | `Object` | `private` | `-` | - |
| `errorCode` | `int` | `private` | `0` | - |
| `errorMsg` | `String` | `private` | `""` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public QueueMessage()` | - |
| `public QueueMessage(String messageId, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getMessageId()` | `String` | - |
| `public void setMessageId(String messageId)` | `void` | - |
| `public Object getPayload()` | `Object` | - |
| `public void setPayload(Object payload)` | `void` | - |
| `public int getErrorCode()` | `int` | - |
| `public void setErrorCode(int errorCode)` | `void` | - |
| `public String getErrorMsg()` | `String` | - |
| `public void setErrorMsg(String errorMsg)` | `void` | - |

### `StreamQueueMessage`

- 类型：`class`
- 声明：`public class StreamQueueMessage extends QueueMessage`
- 说明：Message for streaming (iterator) pattern.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `response` | `CompletableFuture<Iterator<Object>>` | `private final` | `new CompletableFuture<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StreamQueueMessage()` | - |
| `public StreamQueueMessage(String messageId, Object payload)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public CompletableFuture<Iterator<Object>> getResponse()` | `CompletableFuture<Iterator<Object>>` | - |

### `SubscriptionBase`

- 类型：`class`
- 声明：`public abstract class SubscriptionBase`
- 说明：Abstract subscription that processes received messages.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setMessageHandler(Function<Object, Object> handler)` | `void` | - |
| `public void activate()` | `void` | - |
| `public void deactivate()` | `void` | - |
| `public boolean isActive()` | `boolean` | - |

### `SubscriptionInMemory`

- 类型：`class`
- 声明：`public class SubscriptionInMemory extends SubscriptionBase`
- 说明：In-memory subscription using a blocking queue and Virtual Thread consumer.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SubscriptionInMemory(int maxSize, long timeoutMs)` | - |
| `public SubscriptionInMemory()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setMessageHandler(Function<Object, Object> handler)` | `void` | - |
| `public void activate()` | `void` | - |
| `public void deactivate()` | `void` | - |
| `public boolean isActive()` | `boolean` | - |
| `public void pushMessage(QueueMessage message)` | `void` | - |

## `com.openjiuwen.core.runner.resourcemanager`

公开类型：`19`

### `AbstractManager`

- 类型：`class`
- 声明：`public abstract class AbstractManager<T>`
- 说明：Generic base class for resource managers that use provider-based registration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `providers` | `ConcurrentHashMap<String, Supplier<? extends T>>` | `protected final` | `new ConcurrentHashMap<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected void registerResourceProvider(String resourceId, Supplier<? extends T> resource)` | `void` | - |
| `protected T getResource(String resourceId)` | `T` | - |
| `protected Supplier<? extends T> unregisterResourceProvider(String resourceId)` | `Supplier<? extends T>` | - |

### `AgentGroupMgr`

- 类型：`class`
- 声明：`public class AgentGroupMgr<T> extends AbstractManager<T>`
- 说明：Manager for AgentGroup resource providers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addAgentGroup(String agentGroupId, Supplier<? extends T> agentGroup)` | `void` | - |
| `public Supplier<? extends T> removeAgentGroup(String agentGroupId)` | `Supplier<? extends T>` | - |
| `public T getAgentGroup(String agentGroupId)` | `T` | - |

### `AgentMgr`

- 类型：`class`
- 声明：`public class AgentMgr<T> extends AbstractManager<T>`
- 说明：Manager for Agent resource providers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addAgent(String agentId, Supplier<? extends T> agent)` | `void` | - |
| `public T getAgent(String agentId)` | `T` | - |
| `public Supplier<? extends T> removeAgent(String agentId)` | `Supplier<? extends T>` | - |

### `ModelMgr`

- 类型：`class`
- 声明：`public class ModelMgr extends AbstractManager<Model>`
- 说明：Manager for Model resource providers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addModel(String modelId, Supplier<Model> model)` | `void` | - |
| `public Supplier<? extends Model> removeModel(String modelId)` | `Supplier<? extends Model>` | - |
| `public Model getModel(String modelId)` | `Model` | - |

### `PromptMgr`

- 类型：`class`
- 声明：`public class PromptMgr`
- 说明：Manager for PromptTemplate instances.
- 嵌套公开类型：`PromptMgr.PromptEntry`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addPrompt(String templateId, PromptTemplate template)` | `void` | - |
| `public void addPrompts(List<PromptEntry> templates)` | `void` | - |
| `public PromptTemplate removePrompt(String templateId)` | `PromptTemplate` | - |
| `public PromptTemplate getPrompt(String templateId)` | `PromptTemplate` | - |

### `PromptMgr.PromptEntry`

- 类型：`record`
- 声明：`public record PromptEntry(String id, PromptTemplate template)`
- 说明：Prompt 管理器批量注册条目，包含模板 id 与模板实例。
- 宿主类型：`PromptMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private final` | `-` | - |
| `template` | `PromptTemplate` | `private final` | `-` | - |

### `ResourceMgr`

- 类型：`class`
- 声明：`public class ResourceMgr`
- 说明：Resource Manager facade for Model, Workflow, Prompt, Tool, Agent, AgentGroup, SysOperation.
- 嵌套公开类型：`ResourceMgr.AgentEntry`、`ResourceMgr.WorkflowEntry`、`ResourceMgr.ModelEntry`、`ResourceMgr.PromptEntry`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Result<GroupCard> addAgentGroup(GroupCard card, Supplier<Object> agentGroup, Object tag)` | `Result<GroupCard>` | - |
| `public List<Result<GroupCard>> removeAgentGroup(Object groupId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `List<Result<GroupCard>>` | - |
| `public Object getAgentGroup(String groupId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Result<AgentCard> addAgent(AgentCard card, Supplier<Object> agent, Object tag)` | `Result<AgentCard>` | - |
| `public List<Result<AgentCard>> addAgents(List<AgentEntry> agents, Object tag)` | `List<Result<AgentCard>>` | - |
| `public Object removeAgent(Object agentId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Object getAgent(String agentId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getAgent(String agentId)` | `Object` | - |
| `public Result<WorkflowCard> addWorkflow(WorkflowCard card, Supplier<Workflow> workflow, Object tag)` | `Result<WorkflowCard>` | - |
| `public List<Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows, Object tag)` | `List<Result<WorkflowCard>>` | - |
| `public Object removeWorkflow(Object workflowId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Object getWorkflow(String workflowId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getWorkflow(String workflowId)` | `Object` | - |
| `public Result<ToolCard> addTool(Tool tool, Object tag)` | `Result<ToolCard>` | - |
| `public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag)` | `List<Result<ToolCard>>` | - |
| `public Object getTool(String toolId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getTool(String toolId)` | `Object` | - |
| `public Object removeTool(Object toolId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Result<String> addModel(String modelId, Supplier<Model> model, Object tag)` | `Result<String>` | - |
| `public List<Result<String>> addModels(List<ModelEntry> models, Object tag)` | `List<Result<String>>` | - |
| `public Object removeModel(Object modelId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Object getModel(String modelId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getModel(String modelId)` | `Object` | - |
| `public Result<String> addPrompt(String promptId, PromptTemplate template, Object tag)` | `Result<String>` | - |
| `public List<Result<String>> addPrompts(List<PromptEntry> prompts, Object tag)` | `List<Result<String>>` | - |
| `public Object removePrompt(Object promptId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Object getPrompt(String promptId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getPrompt(String promptId)` | `Object` | - |
| `public Result<SysOperationCard> addSysOperation(SysOperationCard card, Object tag)` | `Result<SysOperationCard>` | - |
| `public Object removeSysOperation(Object sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | `Object` | - |
| `public Object getSysOperation(String sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy)` | `Object` | - |
| `public Object getSysOpToolCards(String sysOperationId, Object operationName, Object toolName)` | `Object` | - |
| `public List<ToolInfo> getToolInfos(Object toolId, Object toolType, Object tag, TagMatchStrategy tagMatchStrategy)` | `List<ToolInfo>` | - |
| `public List<Result<String>> addMcpServer(Object serverConfig, Object tag, Double expiryTime) throws Exception` | `List<Result<String>>` | - |
| `public List<Result<String>> removeMcpServer(Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists) throws Exception` | `List<Result<String>>` | - |
| `public Object getMcpTool(Object name, Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists) throws Exception` | `Object` | - |
| `public List<BaseCard> getResourceByTag(String tag)` | `List<BaseCard>` | - |
| `public List<String> listTags()` | `List<String>` | - |
| `public boolean hasTag(String tag)` | `boolean` | - |
| `public List<Result<String>> removeTag(Object tag, boolean skipIfTagNotExists)` | `List<Result<String>>` | - |
| `public Result<List<String>> updateResourceTag(String resourceId, Object tag)` | `Result<List<String>>` | - |
| `public Result<List<String>> addResourceTag(String resourceId, Object tag)` | `Result<List<String>>` | - |
| `public Result<List<String>> removeResourceTag(String resourceId, Object tag, boolean skipIfTagNotExists)` | `Result<List<String>>` | - |
| `public List<String> getResourceTag(String resourceId)` | `List<String>` | - |
| `public boolean resourceHasTag(String resourceId, String tag)` | `boolean` | - |
| `public void release()` | `void` | - |

### `ResourceMgr.AgentEntry`

- 类型：`record`
- 声明：`public record AgentEntry(AgentCard card, Supplier<Object> provider)`
- 说明：资源注册条目：`AgentCard` 与其实例提供器。
- 宿主类型：`ResourceMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `card` | `AgentCard` | `private final` | `-` | - |
| `provider` | `Supplier<Object>` | `private final` | `-` | - |

### `ResourceMgr.ModelEntry`

- 类型：`record`
- 声明：`public record ModelEntry(String id, Supplier<Model> provider)`
- 说明：资源注册条目：模型 id 与其实例提供器。
- 宿主类型：`ResourceMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private final` | `-` | - |
| `provider` | `Supplier<Model>` | `private final` | `-` | - |

### `ResourceMgr.PromptEntry`

- 类型：`record`
- 声明：`public record PromptEntry(String id, PromptTemplate template)`
- 说明：资源注册条目：prompt id 与模板实例。
- 宿主类型：`ResourceMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private final` | `-` | - |
| `template` | `PromptTemplate` | `private final` | `-` | - |

### `ResourceMgr.WorkflowEntry`

- 类型：`record`
- 声明：`public record WorkflowEntry(WorkflowCard card, Supplier<Workflow> provider)`
- 说明：资源注册条目：`WorkflowCard` 与工作流提供器。
- 宿主类型：`ResourceMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `card` | `WorkflowCard` | `private final` | `-` | - |
| `provider` | `Supplier<Workflow>` | `private final` | `-` | - |

### `ResourceRegistry`

- 类型：`class`
- 声明：`public class ResourceRegistry`
- 说明：Central registry holding all sub-managers for different resource types.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void removeById(String resourceId)` | `void` | - |
| `public ToolMgr tool()` | `ToolMgr` | - |
| `public PromptMgr prompt()` | `PromptMgr` | - |
| `public ModelMgr model()` | `ModelMgr` | - |
| `public WorkflowMgr workflow()` | `WorkflowMgr` | - |
| `public AgentMgr<Object> agent()` | `AgentMgr<Object>` | - |
| `public AgentGroupMgr<Object> agentGroup()` | `AgentGroupMgr<Object>` | - |
| `public SysOperationMgr sysOperation()` | `SysOperationMgr` | - |

### `SysOperationMgr`

- 类型：`class`
- 声明：`public class SysOperationMgr`
- 说明：Manager for SysOperation instances.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addSysOperation(String sysOperationId, SysOperation sysOperationInstance)` | `void` | - |
| `public SysOperation removeSysOperation(String sysOperationId)` | `SysOperation` | - |
| `public SysOperation getSysOperation(String sysOperationId)` | `SysOperation` | - |

### `TagMgr`

- 类型：`class`
- 声明：`public class TagMgr`
- 说明：Tag-based resource organization and filtering manager.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TagMgr()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean hasTag(String tag)` | `boolean` | - |
| `public List<String> listTags()` | `List<String>` | - |
| `public boolean hasResource(String resourceId)` | `boolean` | - |
| `public boolean hasResourceTag(String resourceId, String tag)` | `boolean` | - |
| `public List<String> getResourcesTags(String resourceId)` | `List<String>` | - |
| `public List<String> tagResource(String resourceId, Object tags)` | `List<String>` | - |
| `public List<String> removeResource(String resourceId)` | `List<String>` | - |
| `public List<String> removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists)` | `List<String>` | - |
| `public List<String> updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy)` | `List<String>` | - |
| `public List<String> removeTag(String tag, boolean skipIfNotExists)` | `List<String>` | - |
| `public List<String> getTagResources(String tag)` | `List<String>` | - |
| `public List<String> findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists)` | `List<String>` | - |

### `ToolMgr`

- 类型：`class`
- 声明：`public class ToolMgr`
- 说明：Manager for Tool instances, MCP servers, and SysOperation-related tools.
- 嵌套公开类型：`ToolMgr.McpServerResource`、`ToolMgr.SysOpToolResource`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addTool(String toolId, Tool tool)` | `void` | - |
| `public Tool getTool(String toolId)` | `Tool` | - |
| `public Tool getMcpTool(String toolName, String serverId)` | `Tool` | - |
| `public List<Tool> getMcpTools(String serverId)` | `List<Tool>` | - |
| `public Object getMcpToolId(String serverId, String toolName)` | `Object` | - |
| `public Tool removeTool(String toolId)` | `Tool` | - |
| `public static String generateMcpToolId(String serverId, String serverName, String toolName)` | `String` | - |
| `public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception` | `List<McpToolCard>` | - |
| `public List<String> getMcpServerIds(String serverName)` | `List<String>` | - |
| `public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception` | `List<String>` | - |
| `public List<String> removeToolServer(String serverId) throws Exception` | `List<String>` | - |
| `public void addSysOperationTools(String sysOpId, List<String> toolIds)` | `void` | - |
| `public List<String> removeSysOperationTools(String sysOpId)` | `List<String>` | - |
| `public List<String> getSysOperationToolIds(String sysOpId)` | `List<String>` | - |
| `public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception` | `List<McpToolCard>` | - |
| `public void release()` | `void` | - |

### `ToolMgr.McpServerResource`

- 类型：`record`
- 声明：`public record McpServerResource(McpServerConfig config, McpClient client, List<String> toolIds, long lastUpdateTime, Double expiryTime)`
- 说明：MCP 服务缓存条目，记录配置、客户端、工具 id 和刷新信息。
- 宿主类型：`ToolMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `McpServerConfig` | `private final` | `-` | - |
| `client` | `McpClient` | `private final` | `-` | - |
| `toolIds` | `List<String>` | `private final` | `-` | - |
| `lastUpdateTime` | `long` | `private final` | `-` | - |
| `expiryTime` | `Double` | `private final` | `-` | - |

### `ToolMgr.SysOpToolResource`

- 类型：`record`
- 声明：`public record SysOpToolResource(String sysOpId, List<String> toolIds, long lastUpdateTime)`
- 说明：SysOperation 工具缓存条目，记录该系统操作导出的工具 id。
- 宿主类型：`ToolMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sysOpId` | `String` | `private final` | `-` | - |
| `toolIds` | `List<String>` | `private final` | `-` | - |
| `lastUpdateTime` | `long` | `private final` | `-` | - |

### `WorkflowMgr`

- 类型：`class`
- 声明：`public class WorkflowMgr extends AbstractManager<Workflow>`
- 说明：Manager for Workflow resource providers.
- 嵌套公开类型：`WorkflowMgr.WorkflowEntry`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addWorkflow(String workflowId, Supplier<Workflow> workflow)` | `void` | - |
| `public void addWorkflows(List<WorkflowEntry> workflows)` | `void` | - |
| `public Workflow getWorkflow(String workflowId)` | `Workflow` | - |
| `public Supplier<? extends Workflow> removeWorkflow(String workflowId)` | `Supplier<? extends Workflow>` | - |

### `WorkflowMgr.WorkflowEntry`

- 类型：`record`
- 声明：`public record WorkflowEntry(String id, Supplier<Workflow> provider)`
- 说明：Workflow 管理器批量注册条目，包含工作流 id 与提供器。
- 宿主类型：`WorkflowMgr`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private final` | `-` | - |
| `provider` | `Supplier<Workflow>` | `private final` | `-` | - |

