# Controller 模块 API 文档

> 包路径：`com.openjiuwen.core.controller`

Controller 模块负责处理用户输入事件、识别任务意图、维护任务状态，并通过事件队列和任务调度器驱动任务执行。模块核心由 `Controller`、事件处理器、任务管理器、调度器以及一组事件/任务数据模型组成。

---

## 目录

- [1. 核心入口](#1-核心入口)
- [2. 事件与任务调度模块](#2-事件与任务调度模块)
- [3. Schema 数据模型](#3-schema-数据模型)

---

## 1. 核心入口

### 1.1 Controller

控制器主入口，负责装配 `TaskManager`、`EventQueue`、`TaskScheduler` 和 `EventHandler`，并提供同步/流式调用接口。

**包路径**：`com.openjiuwen.core.controller`

**构造方法**
```java
Controller()
```

**核心方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `init(BaseCard card, ControllerConfig config, Object abilityManager, ContextEngine contextEngine)` | `void` | 初始化控制器依赖并创建任务/事件基础组件 |
| `setEventHandler(EventHandler eventHandler)` | `void` | 注入事件处理器，并自动回填配置、上下文、任务管理器和调度器 |
| `addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder)` | `Controller` | 注册任务执行器工厂，支持链式调用 |
| `removeTaskExecutor(String taskType)` | `void` | 移除指定任务类型的执行器 |
| `getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | 根据任务类型创建/获取执行器 |
| `start()` | `void` | 启动事件队列与任务调度 |
| `stop()` | `void` | 停止事件队列与任务调度 |
| `invoke(InputEvent inputEvent, AgentSessionApi session)` | `ControllerOutput` | 同步处理一次输入事件 |
| `stream(InputEvent inputEvent, AgentSessionApi session, List<StreamMode> streamModes)` | `Iterator<Object>` | 流式处理输入事件并将中间结果写入 session stream |

**依赖访问方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getEventQueue()` | `EventQueue` | 获取事件队列 |
| `getConfig()` / `setConfig(ControllerConfig config)` | `ControllerConfig` / `void` | 获取或更新控制器配置 |
| `getContextEngine()` / `setContextEngine(ContextEngine contextEngine)` | `ContextEngine` / `void` | 获取或更新上下文引擎 |
| `getAbilityManager()` / `setAbilityManager(Object abilityManager)` | `Object` / `void` | 获取或更新能力管理器 |
| `getTaskManager()` | `TaskManager` | 获取任务管理器 |
| `getTaskScheduler()` | `TaskScheduler` | 获取任务调度器 |
| `getEventHandler()` | `EventHandler` | 获取当前事件处理器 |

### 1.2 ControllerConfig

控制器配置对象，覆盖任务调度、事件队列、任务持久化和意图识别参数。

**包路径**：`com.openjiuwen.core.controller`

**构造方法**
```java
ControllerConfig()
```

**静态方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `defaultConfig()` | `ControllerConfig` | 返回默认配置实例 |

**核心字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxConcurrentTasks` | `int` | `5` | 最大并发任务数，`0` 表示不限制 |
| `scheduleInterval` | `double` | `1.0` | 调度周期，单位秒，要求 `>= 0.1` |
| `taskTimeout` | `Double` | `null` | 任务超时时间，单位秒，`null` 表示不超时 |
| `defaultTaskPriority` | `int` | `1` | 默认任务优先级，值越大优先级越高 |
| `enableTaskPersistence` | `boolean` | `false` | 是否开启任务状态持久化 |
| `eventQueueSize` | `int` | `10000` | 事件队列容量，要求 `>= 1` |
| `eventTimeout` | `double` | `300` | 事件处理超时时间，要求 `>= 100` |
| `enableIntentRecognition` | `boolean` | `false` | 是否开启基于 LLM 的意图识别 |
| `intentLlmId` | `String` | `""` | 意图识别模型 ID |
| `intentConfidenceThreshold` | `double` | `0.7` | 意图识别置信度阈值，范围 `0.0 ~ 1.0` |
| `intentTypeList` | `List<String>` | `["create_task", "pause_task", "resume_task", "cancel_task", "unknown_task"]` | 允许的意图工具列表 |

---

## 2. 事件与任务调度模块

### 2.1 EventHandler

事件处理器抽象基类，定义了四类控制器事件的统一处理接口。

**包路径**：`com.openjiuwen.core.controller.modules`

**抽象方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `handleInput(EventHandlerInput input)` | `Map<String, Object>` | 处理用户输入事件 |
| `handleTaskInteraction(EventHandlerInput input)` | `Map<String, Object>` | 处理任务交互事件 |
| `handleTaskCompletion(EventHandlerInput input)` | `Map<String, Object>` | 处理任务完成事件 |
| `handleTaskFailed(EventHandlerInput input)` | `Map<String, Object>` | 处理任务失败事件 |

**依赖属性访问**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getConfig()` / `setConfig(ControllerConfig config)` | `ControllerConfig` / `void` | 控制器配置 |
| `getContextEngine()` / `setContextEngine(ContextEngine contextEngine)` | `ContextEngine` / `void` | 上下文引擎 |
| `getAbilityManager()` / `setAbilityManager(Object abilityManager)` | `Object` / `void` | 能力管理器 |
| `getTaskManager()` / `setTaskManager(TaskManager taskManager)` | `TaskManager` / `void` | 任务管理器 |
| `getTaskScheduler()` / `setTaskScheduler(TaskScheduler taskScheduler)` | `TaskScheduler` / `void` | 任务调度器 |

### 2.2 EventHandlerInput

事件处理输入对象，封装待处理的 `Event` 和会话对象。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
EventHandlerInput(Event event, AgentSessionApi session)
```

**字段访问方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getEvent()` | `Event` | 当前事件 |
| `getSession()` | `AgentSessionApi` | 当前会话 |

### 2.3 EventHandlerWithIntentRecognition

内置事件处理器实现，基于 `IntentRecognizer` 将输入事件转换成任务创建、暂停、恢复、补充和取消等操作。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
EventHandlerWithIntentRecognition(IntentRecognizer.ModelProvider modelProvider)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `initRecognizer()` | `void` | 在依赖注入完成后初始化意图识别器 |
| `handleInput(EventHandlerInput input)` | `Map<String, Object>` | 识别输入事件意图并并发处理任务动作 |
| `handleTaskInteraction(EventHandlerInput input)` | `Map<String, Object>` | 将任务交互信息写入 session stream |
| `handleTaskCompletion(EventHandlerInput input)` | `Map<String, Object>` | 将任务结果写入 session stream |
| `handleTaskFailed(EventHandlerInput input)` | `Map<String, Object>` | 将任务失败信息写入 session stream |

### 2.4 EventQueue

基于 topic 的内部事件总线。topic 格式为 `{agentId}_{sessionId}_{eventType}`。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
EventQueue(ControllerConfig config)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `start()` | `void` | 启动事件队列 |
| `stop()` | `void` | 停止事件队列并清空订阅 |
| `subscribe(String agentId, String sessionId)` | `void` | 为指定 agent/session 注册全部事件处理订阅 |
| `unsubscribe(String agentId, String sessionId)` | `void` | 取消指定 agent/session 的订阅 |
| `publishEvent(String agentId, AgentSessionApi session, Event event)` | `void` | 同步发布事件并执行对应处理器 |
| `unsubscribeAll()` | `void` | 清空全部订阅 |
| `setEventHandler(EventHandler eventHandler)` | `void` | 设置事件处理器 |
| `getConfig()` / `setConfig(ControllerConfig config)` | `ControllerConfig` / `void` | 获取或更新配置 |

### 2.5 IntentRecognizer

使用 LLM Tool Calling 对 `InputEvent` 进行意图识别，输出 `Intent` 列表。

**包路径**：`com.openjiuwen.core.controller.modules`

**内部接口**
```java
@FunctionalInterface
public interface ModelProvider {
    Model getModel(String modelId, AgentSessionApi session);
}
```

**构造方法**
```java
IntentRecognizer(
    ControllerConfig config,
    TaskManager taskManager,
    Object abilityManager,
    ContextEngine contextEngine,
    IntentRecognizer.ModelProvider modelProvider
)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `recognize(Event event, AgentSessionApi session)` | `List<Intent>` | 从输入事件和上下文中识别用户意图 |

### 2.6 IntentToolkits

意图工具集，负责构造 OpenAI 兼容工具 schema，并把模型工具调用结果转换成 `Intent`。

**包路径**：`com.openjiuwen.core.controller.modules`

**内部记录**
```java
public record IntentResult(Intent intent, String message) {}
```

**构造方法**
```java
IntentToolkits(Event event, double confidenceThreshold)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `createTask(double confidence, String taskDescription)` | `IntentResult` | 创建新任务意图 |
| `pauseTask(double confidence, String taskId)` | `IntentResult` | 创建暂停任务意图 |
| `cancelTask(double confidence, String taskId)` | `IntentResult` | 创建取消任务意图 |
| `resumeTask(double confidence, String taskId)` | `IntentResult` | 创建恢复任务意图 |
| `unknownTask(double confidence, String questionForUser)` | `IntentResult` | 生成澄清型意图 |
| `createDependentTask(double confidence, String taskDescription, List<String> dependentTaskIds)` | `IntentResult` | 创建依赖前置任务的新任务 |
| `modifyTask(double confidence, String taskId, String newTaskDescription)` | `IntentResult` | 创建任务修改意图 |
| `supplementTask(double confidence, String taskId, String supplementInfo)` | `IntentResult` | 创建任务补充信息意图 |
| `getOpenaiToolSchemas(List<String> choices)` | `List<Map<String, Object>>` | 生成 OpenAI Function Tool schema 列表 |
| `dispatch(String toolName, Map<String, Object> arguments)` | `IntentResult` | 将工具调用名和参数映射成具体意图 |

### 2.7 TaskExecutor

任务执行器抽象基类，不同 `taskType` 需要实现自己的执行器。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
TaskExecutor(TaskExecutorDependencies dependencies)
```

**抽象方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `executeAbility(String taskId, AgentSessionApi session)` | `Iterator<ControllerOutputChunk>` | 执行任务并持续输出 chunk |
| `canPause(String taskId, AgentSessionApi session)` | `PauseCheckResult` | 判断任务是否可暂停 |
| `pause(String taskId, AgentSessionApi session)` | `boolean` | 执行暂停 |
| `canCancel(String taskId, AgentSessionApi session)` | `CancelCheckResult` | 判断任务是否可取消 |
| `cancel(String taskId, AgentSessionApi session)` | `boolean` | 执行取消 |

**返回记录**
```java
public record PauseCheckResult(boolean canPause, String reason) {}
public record CancelCheckResult(boolean canCancel, String reason) {}
```

### 2.8 TaskExecutorDependencies

任务执行器依赖容器。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
TaskExecutorDependencies(
    ControllerConfig config,
    Object abilityManager,
    ContextEngine contextEngine,
    TaskManager taskManager,
    EventQueue eventQueue
)
```

**访问方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getConfig()` | `ControllerConfig` | 控制器配置 |
| `getAbilityManager()` | `Object` | 能力管理器 |
| `getContextEngine()` | `ContextEngine` | 上下文引擎 |
| `getTaskManager()` | `TaskManager` | 任务管理器 |
| `getEventQueue()` | `EventQueue` | 事件队列 |

### 2.9 TaskExecutorRegistry

任务执行器注册表，按任务类型维护构造器。

**包路径**：`com.openjiuwen.core.controller.modules`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addTaskExecutor(String taskType, Function<TaskExecutorDependencies, TaskExecutor> builder)` | `void` | 注册执行器工厂 |
| `removeTaskExecutor(String taskType)` | `void` | 移除执行器工厂 |
| `getTaskExecutor(String taskType, TaskExecutorDependencies dependencies)` | `TaskExecutor` | 创建执行器实例 |

### 2.10 TaskFilter

任务查询条件对象，支持按任务 ID、会话 ID、状态、优先级和根任务进行过滤。

**包路径**：`com.openjiuwen.core.controller.modules`

**核心字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `Object` | 支持 `String` 或 `List<String>` |
| `sessionId` | `String` | 会话 ID |
| `userId` | `String` | 预留用户过滤字段 |
| `priority` | `Object` | 支持 `Integer` 或 `"highest"` |
| `status` | `TaskStatus` | 任务状态 |
| `withChildren` | `boolean` | 查询结果是否递归包含子任务 |
| `isRoot` | `boolean` | 是否仅匹配根任务 |

**静态工厂**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `byTaskId(String taskId)` | `TaskFilter` | 按单个任务 ID 过滤 |
| `byTaskIds(List<String> taskIds)` | `TaskFilter` | 按多个任务 ID 过滤 |
| `bySessionId(String sessionId)` | `TaskFilter` | 按会话过滤 |
| `byStatus(TaskStatus status)` | `TaskFilter` | 按状态过滤 |
| `byRoot()` | `TaskFilter` | 仅根任务 |
| `byHighestPriority()` | `TaskFilter` | 仅最高优先级任务 |
| `builder()` | `TaskFilter.Builder` | 通用构造器 |

**读取方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getTaskIdList()` | `List<String>` | 将 `taskId` 统一转换为列表 |
| `getTaskId()` | `Object` | 原始 `taskId` |
| `getSessionId()` | `String` | 会话 ID |
| `getUserId()` | `String` | 用户 ID |
| `getPriority()` | `Object` | 原始优先级 |
| `getPriorityAsInt()` | `Integer` | 整型优先级 |
| `isHighestPriority()` | `boolean` | 是否请求最高优先级 |
| `getStatus()` | `TaskStatus` | 状态 |
| `isWithChildren()` | `boolean` | 是否带子任务 |
| `isRoot()` | `boolean` | 是否根任务 |

### 2.11 TaskManager

任务管理器，负责任务 CRUD、状态迁移、优先级维护和父子任务关系管理。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
TaskManager(ControllerConfig config)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getState()` | `TaskManagerState` | 导出当前任务管理状态 |
| `loadState(TaskManagerState state)` | `void` | 从序列化状态恢复 |
| `clearState()` | `void` | 清空所有任务状态 |
| `addTask(Task task)` / `addTask(List<Task> tasks)` | `void` | 添加任务 |
| `getTask(TaskFilter taskFilter)` | `List<Task>` | 查询任务，返回副本列表 |
| `popTask(TaskFilter taskFilter)` | `List<Task>` | 查询并移除任务 |
| `updateTask(Task task)` / `updateTask(List<Task> tasks)` | `boolean` | 更新任务 |
| `removeTask(TaskFilter taskFilter)` | `void` | 删除任务 |
| `getChildTask(String taskId, boolean recursive)` | `List<Task>` | 查询指定任务的子任务 |
| `getChildTask(List<String> taskIds, boolean recursive)` | `List<Task>` | 批量查询子任务 |
| `updateTaskStatus(String taskId, TaskStatus status)` | `void` | 更新任务状态 |
| `updateTaskStatus(String taskId, TaskStatus status, String errorMessage)` | `void` | 更新状态并附带错误信息 |
| `updateTaskStatus(List<String> taskIds, TaskStatus status, boolean withChildren, boolean recursive, String errorMessage)` | `void` | 批量更新状态 |
| `setPriority(String taskId, int priority, boolean withChildren, boolean recursive)` | `void` | 设置任务优先级 |
| `setPriority(List<String> taskIds, int priority, boolean withChildren, boolean recursive)` | `void` | 批量设置优先级 |

### 2.12 TaskManagerState

任务管理器状态快照，用于持久化与恢复。

**包路径**：`com.openjiuwen.core.controller.modules`

**核心字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `tasks` | `Map<String, Task>` | 任务表 |
| `priorityIndex` | `Map<Integer, List<String>>` | 优先级索引 |
| `parentToChildren` | `Map<String, Set<String>>` | 父任务到子任务映射 |
| `childrenToParent` | `Map<String, String>` | 子任务到父任务映射 |
| `rootTasks` | `Set<String>` | 根任务集合 |

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toMap()` | `Map<String, Object>` | 转换为可持久化 Map |
| `fromMap(Map<String, Object> data)` | `TaskManagerState` | 从 Map 恢复状态 |

### 2.13 TaskScheduler

任务调度器，负责扫描 `SUBMITTED` 任务、并发执行、处理超时、暂停/取消以及事件回传。

**包路径**：`com.openjiuwen.core.controller.modules`

**构造方法**
```java
TaskScheduler(
    ControllerConfig config,
    TaskManager taskManager,
    ContextEngine contextEngine,
    Object abilityManager,
    EventQueue eventQueue,
    BaseCard card
)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `pauseTask(String taskId)` | `boolean` | 暂停正在执行的任务 |
| `cancelTask(String taskId)` | `boolean` | 取消正在执行的任务 |
| `start()` | `void` | 启动调度线程 |
| `stop()` | `void` | 停止调度线程 |
| `getConfig()` / `setConfig(ControllerConfig config)` | `ControllerConfig` / `void` | 获取或更新配置 |
| `getSessions()` | `Map<String, AgentSessionApi>` | 当前活跃会话表 |
| `getTaskManager()` | `TaskManager` | 获取任务管理器 |
| `getTaskExecutorRegistry()` | `TaskExecutorRegistry` | 获取任务执行器注册表 |

---

## 3. Schema 数据模型

### 3.1 DataFrame

控制器模块的通用数据帧接口，支持文本、文件和 JSON 三种载体。

**包路径**：`com.openjiuwen.core.controller.schema`

```java
public sealed interface DataFrame
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getType()` | `String` | 返回数据帧类型，取值为 `"text"` / `"file"` / `"json"` |

**内置记录类型**

| 类型 | 字段 | 说明 |
|------|------|------|
| `TextDataFrame` | `text` | 文本输入/输出 |
| `FileDataFrame` | `name`, `mimeType`, `bytes`, `uri` | 文件数据，支持二进制或 URI |
| `JsonDataFrame` | `data` | JSON 对象数据 |

### 3.2 ControllerOutput

控制器同步返回结构。

**包路径**：`com.openjiuwen.core.controller.schema`

**字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `String` | 结果类型，兼容 `EventType` 和特殊常量 |
| `data` | `Object` | 可为 `List<ControllerOutputChunk>` 或 `Map<String, Object>` |
| `inputEventId` | `String` | 对应输入事件 ID |

**构造方法**
```java
ControllerOutput()
ControllerOutput(EventType type, List<ControllerOutputChunk> data)
ControllerOutput(String type, Object data)
```

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getDataAsChunks()` | `List<ControllerOutputChunk>` | 以 chunk 列表读取数据 |
| `getDataAsMap()` | `Map<String, Object>` | 以 Map 读取数据 |

### 3.3 ControllerOutputChunk

流式输出块，继承 `session.stream.OutputSchema`。

**包路径**：`com.openjiuwen.core.controller.schema`

**常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `CONTROLLER_OUTPUT_TYPE` | `"controller_output"` | 控制器流式输出类型 |

**构造方法**
```java
ControllerOutputChunk()
ControllerOutputChunk(int index, ControllerOutputPayload payload)
ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk)
```

**字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `controllerPayload` | `ControllerOutputPayload` | 业务载荷 |
| `lastChunk` | `boolean` | 是否最后一个 chunk |

### 3.4 ControllerOutputPayload

流式输出的实际载荷。

**包路径**：`com.openjiuwen.core.controller.schema`

**常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `TASK_PROCESSING` | `"processing"` | 执行中的中间结果 |
| `ALL_TASKS_PROCESSED` | `"all_tasks_processed"` | 当前会话任务全部处理完成 |

**字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `String` | 载荷类型 |
| `data` | `List<DataFrame>` | 输出数据 |
| `metadata` | `Map<String, Object>` | 附加元数据 |

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `ControllerOutputPayload(EventType eventType, List<DataFrame> data)` | - | 通过事件类型构建载荷 |
| `allTasksProcessed(String message)` | `ControllerOutputPayload` | 生成“全部任务完成”载荷 |

### 3.5 Event 与事件类型

控制器事件基类与事件枚举。

**包路径**：`com.openjiuwen.core.controller.schema`

#### Event

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventType` | `EventType` | 事件类型 |
| `eventId` | `String` | 事件 ID，默认自动生成 UUID |
| `metadata` | `Map<String, Object>` | 扩展元数据 |

#### EventType

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `INPUT` | `"input"` | 输入事件 |
| `TASK_INTERACTION` | `"task_interaction"` | 任务交互事件 |
| `TASK_COMPLETION` | `"task_completion"` | 任务完成事件 |
| `TASK_FAILED` | `"task_failed"` | 任务失败事件 |

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getValue()` | `String` | 获取字符串值 |
| `fromValue(String value)` | `EventType` | 从字符串解析枚举 |

### 3.6 InputEvent

用户输入事件，是控制器的标准输入模型。

**包路径**：`com.openjiuwen.core.controller.schema`

**字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `inputData` | `List<DataFrame>` | 输入数据列表 |

**构造方法**
```java
InputEvent()
InputEvent(List<DataFrame> inputData)
```

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `fromUserInput(Object userInput)` | `InputEvent` | 从 `String`、`Map` 或已有 `InputEvent` 构建输入事件 |

### 3.7 Intent 与 IntentType

用户意图数据模型及其枚举。

**包路径**：`com.openjiuwen.core.controller.schema`

#### Intent

| 字段 | 类型 | 说明 |
|------|------|------|
| `intentType` | `IntentType` | 意图类型 |
| `event` | `Event` | 来源事件 |
| `targetTaskId` | `String` | 目标任务 ID |
| `targetTaskDescription` | `String` | 目标任务描述 |
| `dependTaskId` | `List<String>` | 依赖任务 ID 列表 |
| `supplementaryInfo` | `String` | 任务补充信息 |
| `modificationDetails` | `String` | 任务修改内容 |
| `confidence` | `double` | 意图置信度 |
| `metadata` | `Map<String, Object>` | 扩展元数据 |
| `clarificationPrompt` | `String` | 不明确意图时给用户的澄清问题 |

**构造方法**
```java
Intent(IntentType intentType, Event event, String targetTaskId)
Intent(
    IntentType intentType,
    Event event,
    String targetTaskId,
    String targetTaskDescription,
    List<String> dependTaskId,
    String supplementaryInfo,
    String modificationDetails,
    double confidence,
    String clarificationPrompt
)
```

#### IntentType

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `CREATE_TASK` | `"create_task"` | 创建任务 |
| `PAUSE_TASK` | `"pause_task"` | 暂停任务 |
| `RESUME_TASK` | `"resume_task"` | 恢复任务 |
| `CONTINUE_TASK` | `"continue_task"` | 基于前置任务继续执行 |
| `SUPPLEMENT_TASK` | `"supplement_task"` | 补充任务信息 |
| `CANCEL_TASK` | `"cancel_task"` | 取消任务 |
| `MODIFY_TASK` | `"modify_task"` | 修改任务 |
| `SWITCH_TASK` | `"switch_task"` | 切换任务 |
| `UNKNOWN_TASK` | `"unknown_task"` | 无法确定，需要用户澄清 |

### 3.8 Task 与 TaskStatus

任务模型及其状态枚举。

**包路径**：`com.openjiuwen.core.controller.schema`

#### Task

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `String` | 所属会话 ID |
| `taskId` | `String` | 任务 ID |
| `taskType` | `String` | 任务类型，对应执行器注册 key |
| `description` | `String` | 任务描述 |
| `priority` | `int` | 任务优先级 |
| `inputs` | `List<Object>` | 任务输入 |
| `outputs` | `List<ControllerOutputChunk>` | 任务输出块 |
| `status` | `TaskStatus` | 当前状态 |
| `parentTaskId` | `String` | 父任务 ID |
| `contextId` | `String` | 关联上下文 ID |
| `inputRequiredFields` | `Object` | 任务需要用户补充的字段 |
| `errorMessage` | `String` | 失败原因 |
| `metadata` | `Map<String, Object>` | 扩展元数据 |
| `extensions` | `Map<String, Object>` | 扩展属性 |

**构造方法**
```java
Task()
Task(String sessionId, String taskId, String taskType)
```

**方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `copy()` | `Task` | 深拷贝任务对象 |
| `validate()` | `void` | 校验任务字段和状态一致性 |
| `toMap()` | `Map<String, Object>` | 转换为可持久化 Map |
| `fromMap(Map<String, Object> data)` | `Task` | 从 Map 恢复任务 |

#### TaskStatus

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `SUBMITTED` | `"submitted"` | 已提交待执行 |
| `WORKING` | `"working"` | 执行中 |
| `PAUSED` | `"paused"` | 已暂停 |
| `INPUT_REQUIRED` | `"input-required"` | 等待用户补充输入 |
| `COMPLETED` | `"completed"` | 已完成 |
| `CANCELED` | `"canceled"` | 已取消 |
| `FAILED` | `"failed"` | 执行失败 |
| `WAITING` | `"waiting"` | 等待依赖或调度 |
| `UNKNOWN` | `"unknown"` | 未知状态 |

### 3.9 任务事件模型

任务生命周期相关的三个事件类型都继承自 `Event`。

**包路径**：`com.openjiuwen.core.controller.schema`

| 类名 | 字段 | 说明 |
|------|------|------|
| `TaskCompletionEvent` | `taskResult`, `task` | 任务完成事件，包含输出结果和任务快照 |
| `TaskInteractionEvent` | `interaction`, `task` | 任务请求用户交互时的事件 |
| `TaskFailedEvent` | `errorMessage`, `task` | 任务失败事件，包含失败原因 |
