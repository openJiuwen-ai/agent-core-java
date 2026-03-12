# Controller 模块 API 文档

> 包路径：`com.openjiuwen.core.controller`

控制器主入口、意图识别、任务编排与控制层 schema。基于 `controller` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `60` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.controller` | 2 |
| `com.openjiuwen.core.controller.legacy` | 6 |
| `com.openjiuwen.core.controller.legacy.config` | 1 |
| `com.openjiuwen.core.controller.legacy.event` | 7 |
| `com.openjiuwen.core.controller.legacy.reasoner` | 3 |
| `com.openjiuwen.core.controller.legacy.task` | 6 |
| `com.openjiuwen.core.controller.modules` | 18 |
| `com.openjiuwen.core.controller.schema` | 17 |

## `com.openjiuwen.core.controller`

公开类型：`2`

### `Controller`

- 类型：`class`
- 声明：`public class Controller`
- 说明：Controller \u2014 the core component of ControllerAgent.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Controller()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void init(BaseCard card, ControllerConfig config, Object abilityManager, ContextEngine contextEngine)` | `void` | Initialize controller with all required dependencies. |
| `public EventQueue getEventQueue()` | `EventQueue` | - |
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public void setConfig(ControllerConfig config)` | `void` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `public void setContextEngine(ContextEngine contextEngine)` | `void` | - |
| `public Object getAbilityManager()` | `Object` | - |
| `public void setAbilityManager(Object abilityManager)` | `void` | - |
| `public TaskManager getTaskManager()` | `TaskManager` | - |
| `public TaskScheduler getTaskScheduler()` | `TaskScheduler` | - |
| `public EventHandler getEventHandler()` | `EventHandler` | - |
| `public void setEventHandler(EventHandler eventHandler)` | `void` | Set event handler and wire its dependencies. |
| `public Controller addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder)` | `Controller` | Add a task executor builder (fluent API). |
| `public void removeTaskExecutor(String taskType)` | `void` | Remove a task executor. |
| `public TaskExecutor getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | Get task executor for a given task type. |
| `public void start()` | `void` | Start controller (event queue + task scheduler). |
| `public void stop()` | `void` | Stop controller. |
| `public ControllerOutput invoke(InputEvent inputs, AgentSessionApi session)` | `ControllerOutput` | Batch execution: collect all stream output into a single result. |
| `public Iterator<Object> stream(InputEvent inputs, AgentSessionApi session, List<StreamMode> streamModes)` | `Iterator<Object>` | Stream execution. |

### `ControllerConfig`

- 类型：`class`
- 声明：`public class ControllerConfig`
- 说明：Controller configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `maxConcurrentTasks` | `int` | `private` | `5` | Maximum number of concurrent tasks (0 means no limit). |
| `scheduleInterval` | `double` | `private` | `1.0` | Task scheduling interval in seconds. |
| `taskTimeout` | `Double` | `private` | `-` | Task timeout in seconds. |
| `defaultTaskPriority` | `int` | `private` | `1` | Default task priority. |
| `enableTaskPersistence` | `boolean` | `private` | `false` | Whether to enable task persistence. |
| `eventQueueSize` | `int` | `private` | `10000` | Event queue size. |
| `eventTimeout` | `double` | `private` | `300` | Event processing timeout in seconds. |
| `enableIntentRecognition` | `boolean` | `private` | `false` | Whether to enable intent recognition. |
| `intentLlmId` | `String` | `private` | `""` | Intent LLM model ID |
| `intentConfidenceThreshold` | `double` | `private` | `0.7` | Confidence threshold for intent recognition. |
| `intentTypeList` | `List<String>` | `private` | `List.of("create_task", "pause_task", "resume_task", "cancel_task", "unknown_task")` | List of supported intent types |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static ControllerConfig defaultConfig()` | `ControllerConfig` | - |
| `public int getMaxConcurrentTasks()` | `int` | - |
| `public void setMaxConcurrentTasks(int maxConcurrentTasks)` | `void` | - |
| `public double getScheduleInterval()` | `double` | - |
| `public void setScheduleInterval(double scheduleInterval)` | `void` | - |
| `public Double getTaskTimeout()` | `Double` | - |
| `public void setTaskTimeout(Double taskTimeout)` | `void` | - |
| `public int getDefaultTaskPriority()` | `int` | - |
| `public void setDefaultTaskPriority(int defaultTaskPriority)` | `void` | - |
| `public boolean isEnableTaskPersistence()` | `boolean` | - |
| `public void setEnableTaskPersistence(boolean enableTaskPersistence)` | `void` | - |
| `public int getEventQueueSize()` | `int` | - |
| `public void setEventQueueSize(int eventQueueSize)` | `void` | - |
| `public double getEventTimeout()` | `double` | - |
| `public void setEventTimeout(double eventTimeout)` | `void` | - |
| `public boolean isEnableIntentRecognition()` | `boolean` | - |
| `public void setEnableIntentRecognition(boolean enableIntentRecognition)` | `void` | - |
| `public String getIntentLlmId()` | `String` | - |
| `public void setIntentLlmId(String intentLlmId)` | `void` | - |
| `public double getIntentConfidenceThreshold()` | `double` | - |
| `public void setIntentConfidenceThreshold(double intentConfidenceThreshold)` | `void` | - |
| `public List<String> getIntentTypeList()` | `List<String>` | - |
| `public void setIntentTypeList(List<String> intentTypeList)` | `void` | - |

## `com.openjiuwen.core.controller.legacy`

公开类型：`6`

### `BaseController`

- 类型：`class`
- 声明：`public abstract class BaseController`
- 说明：Legacy controller base class backed by the in-memory message queue.
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `Object` | `protected` | `-` | - |
| `contextEngine` | `ContextEngine` | `protected` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseController()` | - |
| `protected BaseController(Object config, ContextEngine contextEngine)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setupFromAgent(Object agent)` | `void` | - |
| `public Map<String, Object> invoke(Map<String, Object> inputs, Session session)` | `Map<String, Object>` | - |
| `protected abstract Map<String, Object> handleEvent(Event event, Session session)` | `Map<String, Object>` | - |
| `public Event createMessage(Map<String, Object> inputs)` | `Event` | - |
| `public void cleanupConversation(String conversationId)` | `void` | - |
| `public void stop()` | `void` | - |
| `public void setGroup(Object group)` | `void` | - |
| `public Object sendToAgent(String agentId, Event event, Session session)` | `Object` | - |
| `public Object publish(Event event, Session session)` | `Object` | - |

### `IntentDetectionController`

- 类型：`class`
- 声明：`public abstract class IntentDetectionController extends BaseController`
- 说明：Legacy intent-detection controller with task routing support.
- 兼容性：`legacy` 包/说明
- 嵌套公开类型：`IntentDetectionController.IntentType`、`IntentDetectionController.Intent`、`IntentDetectionController.TaskQueue`、`IntentDetectionController.RunningTaskInfo`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `taskQueue` | `TaskQueue` | `protected final` | `new TaskQueue()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected Map<String, Object> handleEvent(Event event, Session session)` | `Map<String, Object>` | - |
| `protected Map<String, Object> handleNewTask(Event event, Intent intent, Session session)` | `Map<String, Object>` | - |
| `protected Map<String, Object> handleResume(Event event, Intent intent, Session session)` | `Map<String, Object>` | - |
| `protected Map<String, Object> handleCancel(Event event, Intent intent, Session session)` | `Map<String, Object>` | - |
| `protected Map<String, Object> handleDefaultResponse(Event event, Intent intent, Session session)` | `Map<String, Object>` | - |
| `protected Map<String, Object> handleUnknownIntent(Event event, Intent intent, Session session)` | `Map<String, Object>` | - |
| `protected abstract Intent intentDetection(Event event, Session session)` | `Intent` | - |
| `protected abstract Map<String, Object> execTask(Event.EventContent messageContent, Task task, Session session)` | `Map<String, Object>` | - |
| `protected abstract Map<String, Object> interruptTask(Task task, Session session)` | `Map<String, Object>` | - |

### `IntentDetectionController.Intent`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public static class Intent`
- 说明：旧版意图对象，封装识别出的意图类型以及关联任务、工作流和扩展元数据。
- 宿主类型：`IntentDetectionController`
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intentType` | `IntentType` | `private` | `IntentType.UNKNOWN` | - |
| `task` | `Task` | `private` | `-` | - |
| `workflow` | `Object` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

### `IntentDetectionController.IntentType`

- 类型：`enum`
- 声明：`public enum IntentType`
- 说明：旧版意图类型枚举。
- 宿主类型：`IntentDetectionController`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `EXEC_NEW_TASK` | `new IntentType()` | - |
| `RESUME_TASK` | `new IntentType()` | - |
| `CANCEL_TASK` | `new IntentType()` | - |
| `DEFAULT_RESPONSE` | `new IntentType()` | - |
| `UNKNOWN` | `new IntentType()` | - |

### `IntentDetectionController.RunningTaskInfo`

- 类型：`class`
- 声明：`@Data @AllArgsConstructor public static class RunningTaskInfo`
- 说明：运行中任务快照，记录任务对象、Future、目标标识和开始时间。
- 宿主类型：`IntentDetectionController`
- 注解：`@Data`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `task` | `Task` | `private` | `-` | - |
| `future` | `Future<?>` | `private` | `-` | - |
| `targetId` | `String` | `private` | `-` | - |
| `startTime` | `long` | `private` | `-` | - |

### `IntentDetectionController.TaskQueue`

- 类型：`class`
- 声明：`public static class TaskQueue`
- 说明：旧版控制器内存任务队列，按 `conversationId` 跟踪运行中任务。
- 宿主类型：`IntentDetectionController`
- 兼容性：`legacy` 包/说明

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void registerTask(String conversationId, Task task, Future<?> future, String targetId)` | `void` | - |
| `public boolean cancelRunningTask(String conversationId)` | `boolean` | - |
| `public void unregisterTask(String conversationId)` | `void` | - |
| `public RunningTaskInfo findTask(String conversationId)` | `RunningTaskInfo` | - |
| `public boolean hasRunningTask(String conversationId)` | `boolean` | - |

## `com.openjiuwen.core.controller.legacy.config`

公开类型：`1`

### `ReasonerConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ReasonerConfig`
- 说明：Legacy reasoner configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intentDetectionTemplate` | `List<Map<String, Object>>` | `private` | `new ArrayList<>()` | - |
| `defaultClass` | `String` | `private` | `"default"` | - |
| `enableInput` | `boolean` | `private` | `true` | - |
| `enableHistory` | `boolean` | `private` | `false` | - |
| `chatHistoryMaxTurn` | `int` | `private` | `5` | - |
| `categoryList` | `List<String>` | `private` | `new ArrayList<>()` | - |
| `userPrompt` | `String` | `private` | `""` | - |
| `exampleContent` | `List<String>` | `private` | `new ArrayList<>()` | - |

## `com.openjiuwen.core.controller.legacy.event`

公开类型：`7`

### `Event`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class Event`
- 说明：Legacy event model for backward compatibility.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明
- 嵌套公开类型：`Event.EventType`、`Event.EventPriority`、`Event.SourceType`、`Event.EventSource`、`Event.EventContent`、`Event.EventContext`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `eventId` | `String` | `private` | `UUID.randomUUID().toString()` | - |
| `eventType` | `EventType` | `private` | `EventType.USER_INPUT` | - |
| `priority` | `EventPriority` | `private` | `EventPriority.NORMAL` | - |
| `source` | `EventSource` | `private` | `new EventSource("unknown", SourceType.SYSTEM, null)` | - |
| `content` | `EventContent` | `private` | `new EventContent()` | - |
| `context` | `EventContext` | `private` | `new EventContext()` | - |
| `createdAt` | `Instant` | `private` | `Instant.now()` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `receiverId` | `String` | `private` | `-` | - |
| `customEventType` | `String` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Event createUserEvent(Object content, String conversationId, String userId, Map<String, Object> extensions)` | `Event` | - |
| `public static Event createTaskCompleted(String conversationId, String taskId, Object taskResult, String workflowId, List<Object> streamData)` | `Event` | - |
| `public static Event createTaskInterrupted(String conversationId, String taskId, String reason, Object taskResult, String workflowId, List<Object> streamData)` | `Event` | - |
| `public static Event createErrorEvent(String conversationId, String errorInfo, SourceType sourceType)` | `Event` | - |
| `public static Event createInfoEvent(String conversationId, String infoText, SourceType sourceType)` | `Event` | - |
| `public String getDisplayContent()` | `String` | - |

### `Event.EventContent`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class EventContent`
- 说明：旧版事件内容载荷，包含查询文本、交互输入、流式数据与任务结果。
- 宿主类型：`Event`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `query` | `String` | `private` | `-` | - |
| `interactiveInput` | `InteractiveInput` | `private` | `-` | - |
| `streamData` | `List<Object>` | `private` | `new ArrayList<>()` | - |
| `taskResult` | `Object` | `private` | `-` | - |
| `extensions` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getQueryText()` | `String` | - |

### `Event.EventContext`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class EventContext`
- 说明：旧版事件上下文，关联 correlation、conversation、task 与 workflow 标识。
- 宿主类型：`Event`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `correlationId` | `String` | `private` | `-` | - |
| `conversationId` | `String` | `private` | `-` | - |
| `taskId` | `String` | `private` | `-` | - |
| `workflowId` | `String` | `private` | `-` | - |

### `Event.EventPriority`

- 类型：`enum`
- 声明：`public enum EventPriority`
- 说明：旧版事件优先级枚举。
- 宿主类型：`Event`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `LOW` | `new EventPriority()` | - |
| `NORMAL` | `new EventPriority()` | - |
| `HIGH` | `new EventPriority()` | - |
| `URGENT` | `new EventPriority()` | - |

### `Event.EventSource`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class EventSource`
- 说明：旧版事件来源描述，记录会话来源、来源类型与用户标识。
- 宿主类型：`Event`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `conversationId` | `String` | `private` | `-` | - |
| `sourceType` | `SourceType` | `private` | `-` | - |
| `userId` | `String` | `private` | `-` | - |

### `Event.EventType`

- 类型：`enum`
- 声明：`public enum EventType`
- 说明：旧版事件类型枚举。
- 宿主类型：`Event`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `USER_INPUT` | `new EventType()` | - |
| `AGENT_RESPONSE` | `new EventType()` | - |
| `AGENT_HANDOFF` | `new EventType()` | - |
| `TASK_COMPLETED` | `new EventType()` | - |
| `TASK_INTERRUPTED` | `new EventType()` | - |
| `ERROR` | `new EventType()` | - |
| `INFO` | `new EventType()` | - |

### `Event.SourceType`

- 类型：`enum`
- 声明：`public enum SourceType`
- 说明：旧版事件来源类型枚举。
- 宿主类型：`Event`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `USER` | `new SourceType()` | - |
| `AGENT` | `new SourceType()` | - |
| `TASK` | `new SourceType()` | - |
| `WORKFLOW` | `new SourceType()` | - |
| `SYSTEM` | `new SourceType()` | - |

## `com.openjiuwen.core.controller.legacy.reasoner`

公开类型：`3`

### `AgentReasoner`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public class AgentReasoner`
- 说明：Minimal legacy reasoner composed of an intent detector and a planner.
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intentDetector` | `IntentDetector` | `private` | `-` | - |
| `planner` | `Planner` | `private` | `-` | - |
| `config` | `ReasonerConfig` | `private` | `new ReasonerConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public IntentDetectionController.Intent detect(Event event, Session session)` | `IntentDetectionController.Intent` | - |
| `public Task plan(IntentDetectionController.Intent intent, Session session)` | `Task` | - |

### `IntentDetector`

- 类型：`interface`
- 声明：`public interface IntentDetector`
- 说明：Legacy intent detector contract.
- 兼容性：`legacy` 包/说明

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `IntentDetectionController.Intent detect(Event event, Session session, ReasonerConfig config)` | `IntentDetectionController.Intent` | - |

### `Planner`

- 类型：`interface`
- 声明：`public interface Planner`
- 说明：Legacy task planner contract.
- 兼容性：`legacy` 包/说明

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Task plan(IntentDetectionController.Intent intent, Session session)` | `Task` | - |

## `com.openjiuwen.core.controller.legacy.task`

公开类型：`6`

### `Task`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class Task`
- 说明：Legacy task model for controller compatibility.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明
- 嵌套公开类型：`Task.TaskStatus`、`Task.DependencyType`、`Task.TaskDependency`、`Task.TaskInput`、`Task.TaskResult`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agentId` | `String` | `private` | `-` | - |
| `taskId` | `String` | `private` | `""` | - |
| `taskType` | `TaskType` | `private` | `TaskType.UNDEFINED` | - |
| `description` | `String` | `private` | `-` | - |
| `status` | `TaskStatus` | `private` | `TaskStatus.PENDING` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `input` | `TaskInput` | `private` | `new TaskInput()` | - |
| `result` | `TaskResult` | `private` | `-` | - |
| `dependencies` | `List<TaskDependency>` | `private` | `new ArrayList<>()` | - |
| `dependents` | `Set<String>` | `private` | `new LinkedHashSet<>()` | - |
| `parentTaskId` | `String` | `private` | `-` | - |
| `childTaskIds` | `Set<String>` | `private` | `new LinkedHashSet<>()` | - |
| `groupId` | `String` | `private` | `-` | - |
| `level` | `int` | `private` | `0` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setAgentId(String agentId)` | `void` | - |

### `Task.DependencyType`

- 类型：`enum`
- 声明：`public enum DependencyType`
- 说明：旧版任务依赖类型枚举。
- 宿主类型：`Task`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `SEQUENTIAL` | `new DependencyType()` | - |
| `PARALLEL` | `new DependencyType()` | - |
| `CONDITIONAL` | `new DependencyType()` | - |
| `DATA` | `new DependencyType()` | - |

### `Task.TaskDependency`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class TaskDependency`
- 说明：旧版任务依赖定义，描述依赖任务、依赖关系、数据映射和是否必需。
- 宿主类型：`Task`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `dependencyId` | `String` | `private` | `-` | - |
| `dependencyType` | `DependencyType` | `private` | `DependencyType.SEQUENTIAL` | - |
| `condition` | `String` | `private` | `-` | - |
| `dataMapping` | `Map<String, String>` | `private` | `new LinkedHashMap<>()` | - |
| `required` | `boolean` | `private` | `true` | - |

### `Task.TaskInput`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class TaskInput`
- 说明：旧版任务输入定义，描述目标对象及其调用参数。
- 宿主类型：`Task`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `targetId` | `String` | `private` | `""` | - |
| `targetName` | `String` | `private` | `""` | - |
| `arguments` | `Object` | `private` | `new LinkedHashMap<>()` | - |

### `Task.TaskResult`

- 类型：`class`
- 声明：`@Data @NoArgsConstructor @AllArgsConstructor public static class TaskResult`
- 说明：旧版任务执行结果快照。
- 宿主类型：`Task`
- 注解：`@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 兼容性：`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `status` | `TaskStatus` | `private` | `-` | - |
| `output` | `Object` | `private` | `-` | - |
| `error` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

### `Task.TaskStatus`

- 类型：`enum`
- 声明：`public enum TaskStatus`
- 说明：旧版任务状态枚举。
- 宿主类型：`Task`
- 兼容性：`legacy` 包/说明

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `PENDING` | `new TaskStatus()` | - |
| `RUNNING` | `new TaskStatus()` | - |
| `SUCCESS` | `new TaskStatus()` | - |
| `FAILED` | `new TaskStatus()` | - |
| `CANCELLED` | `new TaskStatus()` | - |
| `INTERRUPTED` | `new TaskStatus()` | - |

## `com.openjiuwen.core.controller.modules`

公开类型：`18`

### `EventHandler`

- 类型：`class`
- 声明：`public abstract class EventHandler`
- 说明：Abstract base class for event handlers.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `ControllerConfig` | `protected` | `-` | - |
| `contextEngine` | `ContextEngine` | `protected` | `-` | - |
| `abilityManager` | `Object` | `protected` | `-` | - |
| `taskManager` | `TaskManager` | `protected` | `-` | - |
| `taskScheduler` | `TaskScheduler` | `protected` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public void setConfig(ControllerConfig config)` | `void` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `public void setContextEngine(ContextEngine contextEngine)` | `void` | - |
| `public Object getAbilityManager()` | `Object` | - |
| `public void setAbilityManager(Object abilityManager)` | `void` | - |
| `public TaskManager getTaskManager()` | `TaskManager` | - |
| `public void setTaskManager(TaskManager taskManager)` | `void` | - |
| `public TaskScheduler getTaskScheduler()` | `TaskScheduler` | - |
| `public void setTaskScheduler(TaskScheduler taskScheduler)` | `void` | - |
| `public abstract Map<String, Object> handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | Handle input events. |
| `public abstract Map<String, Object> handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | Handle task interaction events. |
| `public abstract Map<String, Object> handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | Handle task completion events. |
| `public abstract Map<String, Object> handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | Handle task failure events. |

### `EventHandlerInput`

- 类型：`class`
- 声明：`public class EventHandlerInput`
- 说明：Input data model for event handlers.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `event` | `Event` | `private final` | `-` | - |
| `session` | `AgentSessionApi` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EventHandlerInput(Event event, AgentSessionApi session)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Event getEvent()` | `Event` | - |
| `public AgentSessionApi getSession()` | `AgentSessionApi` | - |

### `EventHandlerWithIntentRecognition`

- 类型：`class`
- 声明：`public class EventHandlerWithIntentRecognition extends EventHandler`
- 说明：Event handler with intent recognition.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EventHandlerWithIntentRecognition(IntentRecognizer.ModelProvider modelProvider)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void initRecognizer()` | `void` | Initialize the recognizer after dependencies are set (config, taskManager, etc.). |
| `public Map<String, Object> handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | - |
| `public Map<String, Object> handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | - |

### `EventQueue`

- 类型：`class`
- 声明：`public class EventQueue`
- 说明：Event queue responsible for event publishing and subscription.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EventQueue(ControllerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public void setConfig(ControllerConfig config)` | `void` | - |
| `public void setEventHandler(EventHandler eventHandler)` | `void` | - |
| `public void start()` | `void` | Start event queue processing. |
| `public void stop()` | `void` | Stop event queue processing and clear all subscriptions. |
| `public void subscribe(String agentId, String sessionId)` | `void` | Subscribe to all event types for a given agent/session pair. |
| `public void unsubscribe(String agentId, String sessionId)` | `void` | Unsubscribe from all event types for a given agent/session pair. |
| `public void publishEvent(String agentId, AgentSessionApi session, Event event)` | `void` | Publish an event to the queue and wait until it is handled. |
| `public void unsubscribeAll()` | `void` | Unsubscribe from all topics. |

### `IntentRecognizer`

- 类型：`class`
- 声明：`public class IntentRecognizer`
- 说明：Intent recognizer.
- 嵌套公开类型：`IntentRecognizer.ModelProvider`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public IntentRecognizer(ControllerConfig config, TaskManager taskManager, Object abilityManager, ContextEngine contextEngine, ModelProvider modelProvider)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Intent> recognize(Event event, AgentSessionApi session)` | `List<Intent>` | Recognize intents from an event. |

### `IntentRecognizer.ModelProvider`

- 类型：`interface`
- 声明：`@FunctionalInterface public interface ModelProvider`
- 说明：Functional interface for obtaining a Model instance.
- 宿主类型：`IntentRecognizer`
- 注解：`@FunctionalInterface`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `Model getModel(String modelId, AgentSessionApi session)` | `Model` | - |

### `IntentToolkits`

- 类型：`class`
- 声明：`public class IntentToolkits`
- 说明：Intent toolkits for intent recognition.
- 嵌套公开类型：`IntentToolkits.IntentResult`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public IntentToolkits(Event event, double confidenceThreshold)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public IntentResult createTask(double confidence, String taskDescription)` | `IntentResult` | - |
| `public IntentResult pauseTask(double confidence, String taskId)` | `IntentResult` | - |
| `public IntentResult cancelTask(double confidence, String taskId)` | `IntentResult` | - |
| `public IntentResult resumeTask(double confidence, String taskId)` | `IntentResult` | - |
| `public IntentResult unknownTask(double confidence, String questionForUser)` | `IntentResult` | - |
| `public IntentResult createDependentTask(double confidence, String taskDescription, List<String> dependentTaskIds)` | `IntentResult` | - |
| `public IntentResult modifyTask(double confidence, String taskId, String newTaskDescription)` | `IntentResult` | - |
| `public IntentResult supplementTask(double confidence, String taskId, String supplementInfo)` | `IntentResult` | - |
| `public List<Map<String, Object>> getOpenaiToolSchemas(List<String> choices)` | `List<Map<String, Object>>` | Get OpenAI-compatible tool schemas. |
| `public IntentResult dispatch(String toolName, Map<String, Object> arguments)` | `IntentResult` | Dispatch a tool call by name. |

### `IntentToolkits.IntentResult`

- 类型：`record`
- 声明：`public record IntentResult(Intent intent, String message)`
- 说明：Result of intent creation: an intent + a descriptive message.
- 宿主类型：`IntentToolkits`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intent` | `Intent` | `private final` | `-` | - |
| `message` | `String` | `private final` | `-` | - |

### `TaskExecutor`

- 类型：`class`
- 声明：`public abstract class TaskExecutor`
- 说明：Abstract base class for task executors.
- 嵌套公开类型：`TaskExecutor.PauseCheckResult`、`TaskExecutor.CancelCheckResult`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `ControllerConfig` | `protected final` | `-` | - |
| `abilityManager` | `Object` | `protected final` | `-` | - |
| `contextEngine` | `ContextEngine` | `protected final` | `-` | - |
| `taskManager` | `TaskManager` | `protected final` | `-` | - |
| `eventQueue` | `EventQueue` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskExecutor(TaskExecutorDependencies dependencies)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session)` | `Iterator<ControllerOutputChunk>` | Execute a task and return output chunks. |
| `public abstract PauseCheckResult canPause(String taskId, AgentSessionApi session)` | `PauseCheckResult` | Check whether the task can be paused. |
| `public abstract boolean pause(String taskId, AgentSessionApi session)` | `boolean` | Pause the given task. |
| `public abstract CancelCheckResult canCancel(String taskId, AgentSessionApi session)` | `CancelCheckResult` | Check whether the task can be canceled. |
| `public abstract boolean cancel(String taskId, AgentSessionApi session)` | `boolean` | Cancel the given task. |

### `TaskExecutor.CancelCheckResult`

- 类型：`record`
- 声明：`public record CancelCheckResult(boolean canCancel, String reason)`
- 说明：Result of a cancel-ability check.
- 宿主类型：`TaskExecutor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `canCancel` | `boolean` | `private final` | `-` | - |
| `reason` | `String` | `private final` | `-` | - |

### `TaskExecutor.PauseCheckResult`

- 类型：`record`
- 声明：`public record PauseCheckResult(boolean canPause, String reason)`
- 说明：Result of a pause-ability check.
- 宿主类型：`TaskExecutor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `canPause` | `boolean` | `private final` | `-` | - |
| `reason` | `String` | `private final` | `-` | - |

### `TaskExecutorDependencies`

- 类型：`class`
- 声明：`public class TaskExecutorDependencies`
- 说明：Task executor dependencies.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskExecutorDependencies(ControllerConfig config, Object abilityManager, ContextEngine contextEngine, TaskManager taskManager, EventQueue eventQueue)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public Object getAbilityManager()` | `Object` | - |
| `public ContextEngine getContextEngine()` | `ContextEngine` | - |
| `public TaskManager getTaskManager()` | `TaskManager` | - |
| `public EventQueue getEventQueue()` | `EventQueue` | - |

### `TaskExecutorRegistry`

- 类型：`class`
- 声明：`public class TaskExecutorRegistry`
- 说明：Task executor registry.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder)` | `void` | Register a task executor builder. |
| `public void removeTaskExecutor(String taskType)` | `void` | Remove a task executor. |
| `public TaskExecutor getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | Get a task executor instance for the given task type. |

### `TaskFilter`

- 类型：`class`
- 声明：`public class TaskFilter`
- 说明：Task filter for querying tasks.
- 嵌套公开类型：`TaskFilter.Builder`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static TaskFilter byTaskId(String taskId)` | `TaskFilter` | - |
| `public static TaskFilter byTaskIds(List<String> taskIds)` | `TaskFilter` | - |
| `public static TaskFilter bySessionId(String sessionId)` | `TaskFilter` | - |
| `public static TaskFilter byStatus(TaskStatus status)` | `TaskFilter` | - |
| `public static TaskFilter byRoot()` | `TaskFilter` | - |
| `public static TaskFilter byHighestPriority()` | `TaskFilter` | - |
| `public static Builder builder()` | `Builder` | General-purpose builder. |
| `public List<String> getTaskIdList()` | `List<String>` | - |
| `public Object getTaskId()` | `Object` | - |
| `public String getSessionId()` | `String` | - |
| `public String getUserId()` | `String` | - |
| `public Object getPriority()` | `Object` | - |
| `public Integer getPriorityAsInt()` | `Integer` | - |
| `public boolean isHighestPriority()` | `boolean` | - |
| `public TaskStatus getStatus()` | `TaskStatus` | - |
| `public boolean isWithChildren()` | `boolean` | - |
| `public boolean isRoot()` | `boolean` | - |

### `TaskFilter.Builder`

- 类型：`class`
- 声明：`public static class Builder`
- 说明：Builder for TaskFilter.
- 宿主类型：`TaskFilter`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Builder taskId(String taskId)` | `Builder` | - |
| `public Builder taskIds(List<String> taskIds)` | `Builder` | - |
| `public Builder sessionId(String sessionId)` | `Builder` | - |
| `public Builder userId(String userId)` | `Builder` | - |
| `public Builder priority(int priority)` | `Builder` | - |
| `public Builder highestPriority()` | `Builder` | - |
| `public Builder status(TaskStatus status)` | `Builder` | - |
| `public Builder withChildren(boolean withChildren)` | `Builder` | - |
| `public Builder isRoot(boolean isRoot)` | `Builder` | - |
| `public TaskFilter build()` | `TaskFilter` | - |

### `TaskManager`

- 类型：`class`
- 声明：`public class TaskManager`
- 说明：Task manager responsible for task CRUD, status management, priority management, and hierarchical relationship management.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskManager(ControllerConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public void setConfig(ControllerConfig config)` | `void` | - |
| `public TaskManagerState getState()` | `TaskManagerState` | Get task manager state for serialization. |
| `public void loadState(TaskManagerState state)` | `void` | Load task manager state. |
| `public void clearState()` | `void` | Clear all task manager state. |
| `public void addTask(Task task)` | `void` | Add task(s) to task queue. |
| `public void addTask(List<Task> taskList)` | `void` | Add tasks to task queue. |
| `public List<Task> getTask(TaskFilter taskFilter)` | `List<Task>` | Query tasks based on filter. |
| `public List<Task> popTask(TaskFilter taskFilter)` | `List<Task>` | Pop tasks (query and remove). |
| `public boolean updateTask(Task task)` | `boolean` | Update task(s). |
| `public boolean updateTask(List<Task> taskList)` | `boolean` | Update tasks. |
| `public void removeTask(TaskFilter taskFilter)` | `void` | Remove tasks based on filter. |
| `public List<Task> getChildTask(String taskId, boolean isRecursive)` | `List<Task>` | Get child tasks for a single task ID. |
| `public List<Task> getChildTask(List<String> taskIds, boolean isRecursive)` | `List<Task>` | Get child tasks for multiple task IDs. |
| `public void updateTaskStatus(String taskId, TaskStatus newStatus)` | `void` | Update task status. |
| `public void updateTaskStatus(String taskId, TaskStatus newStatus, String errorMessage)` | `void` | Update task status with error message. |
| `public void updateTaskStatus(List<String> taskIds, TaskStatus newStatus, boolean withChildren, boolean isRecursive, String errorMessage)` | `void` | Update task status for a list of task IDs. |
| `public void setPriority(String taskId, int newPriority, boolean withChildren, boolean isRecursive)` | `void` | Set task priority for a single task ID. |
| `public void setPriority(List<String> taskIds, int newPriority, boolean withChildren, boolean isRecursive)` | `void` | Set task priority for a list of task IDs. |

### `TaskManagerState`

- 类型：`class`
- 声明：`public class TaskManagerState`
- 说明：Task manager state for serialization and restoration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `tasks` | `Map<String, Task>` | `private` | `-` | - |
| `priorityIndex` | `Map<Integer, List<String>>` | `private` | `-` | - |
| `parentToChildren` | `Map<String, Set<String>>` | `private` | `-` | - |
| `childrenToParent` | `Map<String, String>` | `private` | `-` | - |
| `rootTasks` | `Set<String>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskManagerState()` | - |
| `public TaskManagerState(Map<String, Task> tasks, Map<Integer, List<String>> priorityIndex, Map<String, Set<String>> parentToChildren, Map<String, String> childrenToParent, Set<String> rootTasks)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Task> getTasks()` | `Map<String, Task>` | - |
| `public void setTasks(Map<String, Task> tasks)` | `void` | - |
| `public Map<Integer, List<String>> getPriorityIndex()` | `Map<Integer, List<String>>` | - |
| `public void setPriorityIndex(Map<Integer, List<String>> priorityIndex)` | `void` | - |
| `public Map<String, Set<String>> getParentToChildren()` | `Map<String, Set<String>>` | - |
| `public void setParentToChildren(Map<String, Set<String>> parentToChildren)` | `void` | - |
| `public Map<String, String> getChildrenToParent()` | `Map<String, String>` | - |
| `public void setChildrenToParent(Map<String, String> childrenToParent)` | `void` | - |
| `public Set<String> getRootTasks()` | `Set<String>` | - |
| `public void setRootTasks(Set<String> rootTasks)` | `void` | - |
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Serialize state to a plain map for persistence. |
| `public static TaskManagerState fromMap(Map<String, Object> map)` | `TaskManagerState` | Deserialize state from a plain map. |

### `TaskScheduler`

- 类型：`class`
- 声明：`public class TaskScheduler`
- 说明：Task scheduler responsible for scheduling, executing, pausing, and canceling tasks.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskScheduler(ControllerConfig config, TaskManager taskManager, ContextEngine contextEngine, Object abilityManager, EventQueue eventQueue, BaseCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerConfig getConfig()` | `ControllerConfig` | - |
| `public void setConfig(ControllerConfig config)` | `void` | - |
| `public Map<String, AgentSessionApi> getSessions()` | `Map<String, AgentSessionApi>` | - |
| `public TaskManager getTaskManager()` | `TaskManager` | - |
| `public TaskExecutorRegistry getTaskExecutorRegistry()` | `TaskExecutorRegistry` | - |
| `public boolean pauseTask(String taskId)` | `boolean` | Pause a running task. |
| `public boolean cancelTask(String taskId)` | `boolean` | Cancel a running task. |
| `public void start()` | `void` | Start task scheduler. |
| `public void stop()` | `void` | Stop task scheduler. |

## `com.openjiuwen.core.controller.schema`

公开类型：`17`

### `ControllerOutput`

- 类型：`class`
- 声明：`public class ControllerOutput`
- 说明：Controller output for batch processing.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | `String` | `private` | `-` | - |
| `data` | `Object` | `private` | `-` | - |
| `inputEventId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerOutput()` | - |
| `public ControllerOutput(EventType type, List<ControllerOutputChunk> data)` | - |
| `public ControllerOutput(String type, Object data)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |
| `public void setType(String type)` | `void` | - |
| `public void setType(EventType type)` | `void` | - |
| `public Object getData()` | `Object` | Get data as raw object (can be List or Map). |
| `public List<ControllerOutputChunk> getDataAsChunks()` | `List<ControllerOutputChunk>` | Get data as a list of ControllerOutputChunk. |
| `public Map<String, Object> getDataAsMap()` | `Map<String, Object>` | Get data as a Map. |
| `public void setData(Object data)` | `void` | - |
| `public String getInputEventId()` | `String` | - |
| `public void setInputEventId(String inputEventId)` | `void` | - |

### `ControllerOutputChunk`

- 类型：`class`
- 声明：`public class ControllerOutputChunk extends OutputSchema`
- 说明：Controller output chunk for streaming output.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `CONTROLLER_OUTPUT_TYPE` | `String` | `public static final` | `"controller_output"` | - |
| `controllerPayload` | `ControllerOutputPayload` | `private` | `-` | - |
| `lastChunk` | `boolean` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerOutputChunk()` | - |
| `public ControllerOutputChunk(int index, ControllerOutputPayload payload)` | - |
| `public ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public ControllerOutputPayload getControllerPayload()` | `ControllerOutputPayload` | - |
| `public void setControllerPayload(ControllerOutputPayload payload)` | `void` | - |
| `public boolean isLastChunk()` | `boolean` | - |
| `public void setLastChunk(boolean lastChunk)` | `void` | - |

### `ControllerOutputPayload`

- 类型：`class`
- 声明：`public class ControllerOutputPayload`
- 说明：Controller output payload.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `TASK_PROCESSING` | `String` | `public static final` | `"processing"` | Processing type constant |
| `ALL_TASKS_PROCESSED` | `String` | `public static final` | `"all_tasks_processed"` | All tasks processed type constant |
| `type` | `String` | `private` | `-` | - |
| `data` | `List<DataFrame>` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerOutputPayload()` | - |
| `public ControllerOutputPayload(String type, List<DataFrame> data)` | - |
| `public ControllerOutputPayload(String type, List<DataFrame> data, Map<String, Object> metadata)` | - |
| `public ControllerOutputPayload(EventType eventType, List<DataFrame> data)` | Create payload from EventType. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |
| `public void setType(String type)` | `void` | - |
| `public List<DataFrame> getData()` | `List<DataFrame>` | - |
| `public void setData(List<DataFrame> data)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |
| `public static ControllerOutputPayload allTasksProcessed(String message)` | `ControllerOutputPayload` | Create a payload indicating all tasks have been processed. |

### `DataFrame`

- 类型：`interface`
- 声明：`public sealed interface DataFrame permits DataFrame.TextDataFrame, DataFrame.FileDataFrame, DataFrame.JsonDataFrame`
- 说明：DataFrame sealed interface for transmitting different types of data in the controller.
- 嵌套公开类型：`DataFrame.TextDataFrame`、`DataFrame.FileDataFrame`、`DataFrame.JsonDataFrame`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String getType()` | `String` | Get the data frame type. |

### `DataFrame.FileDataFrame`

- 类型：`record`
- 声明：`record FileDataFrame(String name, String mimeType, byte[] bytes, String uri) implements DataFrame`
- 说明：File data frame supporting both bytes and URI.
- 宿主类型：`DataFrame`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `mimeType` | `String` | `private final` | `-` | - |
| `bytes` | `byte[]` | `private final` | `-` | - |
| `uri` | `String` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public FileDataFrame(String name, String mimeType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |

### `DataFrame.JsonDataFrame`

- 类型：`record`
- 声明：`record JsonDataFrame(Map<String, Object> data) implements DataFrame`
- 说明：JSON format data frame.
- 宿主类型：`DataFrame`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `data` | `Map<String, Object>` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |

### `DataFrame.TextDataFrame`

- 类型：`record`
- 声明：`record TextDataFrame(String text) implements DataFrame`
- 说明：Text data frame.
- 宿主类型：`DataFrame`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `text` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getType()` | `String` | - |

### `Event`

- 类型：`class`
- 声明：`public class Event`
- 说明：Event class hierarchy for the controller module.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `eventType` | `EventType` | `private` | `-` | - |
| `eventId` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Event()` | - |
| `public Event(EventType eventType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public EventType getEventType()` | `EventType` | - |
| `public void setEventType(EventType eventType)` | `void` | - |
| `public String getEventId()` | `String` | - |
| `public void setEventId(String eventId)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `EventType`

- 类型：`enum`
- 声明：`public enum EventType`
- 说明：Event type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `INPUT` | `new EventType("input")` | - |
| `TASK_INTERACTION` | `new EventType("task_interaction")` | - |
| `TASK_COMPLETION` | `new EventType("task_completion")` | - |
| `TASK_FAILED` | `new EventType("task_failed")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static EventType fromValue(String value)` | `EventType` | - |
| `public String toString()` | `String` | - |

### `InputEvent`

- 类型：`class`
- 声明：`public class InputEvent extends Event`
- 说明：User input event containing input data.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `inputData` | `List<DataFrame>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InputEvent()` | - |
| `public InputEvent(List<DataFrame> inputData)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<DataFrame> getInputData()` | `List<DataFrame>` | - |
| `public void setInputData(List<DataFrame> inputData)` | `void` | - |
| `public static InputEvent fromUserInput(Object userInput)` | `InputEvent` | Create input event from user input. |

### `Intent`

- 类型：`class`
- 声明：`public class Intent`
- 说明：Intent data model.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `intentType` | `IntentType` | `private` | `-` | - |
| `event` | `Event` | `private` | `-` | - |
| `targetTaskId` | `String` | `private` | `-` | - |
| `targetTaskDescription` | `String` | `private` | `-` | - |
| `dependTaskId` | `List<String>` | `private` | `-` | - |
| `supplementaryInfo` | `String` | `private` | `-` | - |
| `modificationDetails` | `String` | `private` | `-` | - |
| `confidence` | `double` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |
| `clarificationPrompt` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Intent(IntentType intentType, Event event, String targetTaskId)` | - |
| `public Intent(IntentType intentType, Event event, String targetTaskId, String targetTaskDescription, List<String> dependTaskId, String supplementaryInfo, String modificationDetails, double confidence, String clarificationPrompt)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public IntentType getIntentType()` | `IntentType` | - |
| `public void setIntentType(IntentType intentType)` | `void` | - |
| `public Event getEvent()` | `Event` | - |
| `public void setEvent(Event event)` | `void` | - |
| `public String getTargetTaskId()` | `String` | - |
| `public void setTargetTaskId(String targetTaskId)` | `void` | - |
| `public String getTargetTaskDescription()` | `String` | - |
| `public void setTargetTaskDescription(String targetTaskDescription)` | `void` | - |
| `public List<String> getDependTaskId()` | `List<String>` | - |
| `public void setDependTaskId(List<String> dependTaskId)` | `void` | - |
| `public String getSupplementaryInfo()` | `String` | - |
| `public void setSupplementaryInfo(String supplementaryInfo)` | `void` | - |
| `public String getModificationDetails()` | `String` | - |
| `public void setModificationDetails(String modificationDetails)` | `void` | - |
| `public double getConfidence()` | `double` | - |
| `public void setConfidence(double confidence)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |
| `public String getClarificationPrompt()` | `String` | - |
| `public void setClarificationPrompt(String clarificationPrompt)` | `void` | - |

### `IntentType`

- 类型：`enum`
- 声明：`public enum IntentType`
- 说明：Intent type enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `CREATE_TASK` | `new IntentType("create_task")` | Execute new task / interrupt executing tasks and execute new task |
| `PAUSE_TASK` | `new IntentType("pause_task")` | Pause executing task |
| `RESUME_TASK` | `new IntentType("resume_task")` | Resume task (resume previously paused task) |
| `CONTINUE_TASK` | `new IntentType("continue_task")` | Continue task (continue executing task based on completed task) |
| `SUPPLEMENT_TASK` | `new IntentType("supplement_task")` | Supplement necessary information for task |
| `CANCEL_TASK` | `new IntentType("cancel_task")` | Cancel currently executing task |
| `MODIFY_TASK` | `new IntentType("modify_task")` | Modify executing task |
| `SWITCH_TASK` | `new IntentType("switch_task")` | Switch task (interrupt current task and execute another task) |
| `UNKNOWN_TASK` | `new IntentType("unknown_task")` | Unknown intent, requires user clarification |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static IntentType fromValue(String value)` | `IntentType` | - |
| `public String toString()` | `String` | - |

### `Task`

- 类型：`class`
- 声明：`public class Task`
- 说明：Task data model.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `sessionId` | `String` | `private` | `-` | - |
| `taskId` | `String` | `private` | `-` | - |
| `taskType` | `String` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |
| `priority` | `int` | `private` | `-` | - |
| `inputs` | `List<Object>` | `private` | `-` | - |
| `outputs` | `List<ControllerOutputChunk>` | `private` | `-` | - |
| `status` | `TaskStatus` | `private` | `-` | - |
| `parentTaskId` | `String` | `private` | `-` | - |
| `contextId` | `String` | `private` | `-` | - |
| `inputRequiredFields` | `Object` | `private` | `-` | - |
| `errorMessage` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |
| `extensions` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Task()` | - |
| `public Task(String sessionId, String taskId, String taskType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Task copy()` | `Task` | Deep copy of this task. |
| `public void validate()` | `void` | Validate the task for consistency. |
| `public String getSessionId()` | `String` | - |
| `public void setSessionId(String sessionId)` | `void` | - |
| `public String getTaskId()` | `String` | - |
| `public void setTaskId(String taskId)` | `void` | - |
| `public String getTaskType()` | `String` | - |
| `public void setTaskType(String taskType)` | `void` | - |
| `public String getDescription()` | `String` | - |
| `public void setDescription(String description)` | `void` | - |
| `public int getPriority()` | `int` | - |
| `public void setPriority(int priority)` | `void` | - |
| `public List<Object> getInputs()` | `List<Object>` | - |
| `public void setInputs(List<Object> inputs)` | `void` | - |
| `public List<ControllerOutputChunk> getOutputs()` | `List<ControllerOutputChunk>` | - |
| `public void setOutputs(List<ControllerOutputChunk> outputs)` | `void` | - |
| `public TaskStatus getStatus()` | `TaskStatus` | - |
| `public void setStatus(TaskStatus status)` | `void` | - |
| `public String getParentTaskId()` | `String` | - |
| `public void setParentTaskId(String parentTaskId)` | `void` | - |
| `public String getContextId()` | `String` | - |
| `public void setContextId(String contextId)` | `void` | - |
| `public Object getInputRequiredFields()` | `Object` | - |
| `public void setInputRequiredFields(Object inputRequiredFields)` | `void` | - |
| `public String getErrorMessage()` | `String` | - |
| `public void setErrorMessage(String errorMessage)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |
| `public Map<String, Object> getExtensions()` | `Map<String, Object>` | - |
| `public void setExtensions(Map<String, Object> extensions)` | `void` | - |
| `public Map<String, Object> toMap()` | `Map<String, Object>` | Serialize task to a plain map for persistence. |
| `public static Task fromMap(Map<String, Object> map)` | `Task` | Deserialize task from a plain map. |

### `TaskCompletionEvent`

- 类型：`class`
- 声明：`public class TaskCompletionEvent extends Event`
- 说明：Task completion event.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `taskResult` | `List<DataFrame>` | `private` | `-` | - |
| `task` | `Task` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskCompletionEvent()` | - |
| `public TaskCompletionEvent(List<DataFrame> taskResult, Task task)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<DataFrame> getTaskResult()` | `List<DataFrame>` | - |
| `public void setTaskResult(List<DataFrame> taskResult)` | `void` | - |
| `public Task getTask()` | `Task` | - |
| `public void setTask(Task task)` | `void` | - |

### `TaskFailedEvent`

- 类型：`class`
- 声明：`public class TaskFailedEvent extends Event`
- 说明：Task failed event.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `errorMessage` | `String` | `private` | `-` | - |
| `task` | `Task` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskFailedEvent()` | - |
| `public TaskFailedEvent(String errorMessage, Task task)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getErrorMessage()` | `String` | - |
| `public void setErrorMessage(String errorMessage)` | `void` | - |
| `public Task getTask()` | `Task` | - |
| `public void setTask(Task task)` | `void` | - |

### `TaskInteractionEvent`

- 类型：`class`
- 声明：`public class TaskInteractionEvent extends Event`
- 说明：Task interaction event.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `interaction` | `List<DataFrame>` | `private` | `-` | - |
| `task` | `Task` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TaskInteractionEvent()` | - |
| `public TaskInteractionEvent(List<DataFrame> interaction, Task task)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<DataFrame> getInteraction()` | `List<DataFrame>` | - |
| `public void setInteraction(List<DataFrame> interaction)` | `void` | - |
| `public Task getTask()` | `Task` | - |
| `public void setTask(Task task)` | `void` | - |

### `TaskStatus`

- 类型：`enum`
- 声明：`public enum TaskStatus`
- 说明：Task status enumeration.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `SUBMITTED` | `new TaskStatus("submitted")` | - |
| `WORKING` | `new TaskStatus("working")` | - |
| `PAUSED` | `new TaskStatus("paused")` | - |
| `INPUT_REQUIRED` | `new TaskStatus("input-required")` | - |
| `COMPLETED` | `new TaskStatus("completed")` | - |
| `CANCELED` | `new TaskStatus("canceled")` | - |
| `FAILED` | `new TaskStatus("failed")` | - |
| `WAITING` | `new TaskStatus("waiting")` | - |
| `UNKNOWN` | `new TaskStatus("unknown")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static TaskStatus fromValue(String value)` | `TaskStatus` | - |
| `public String toString()` | `String` | - |

