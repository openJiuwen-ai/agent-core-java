# controller 模块 Python / Java API 映射

## 对照范围

- Python 基线：`F:\oepnjiuwen\agent-core-python\openjiuwen\core\controller`
- Java 对照：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\controller`

## 说明

- 本文覆盖 `controller` 新版主干、`schema`、`modules`、`legacy` 四部分。
- Python 的 `snake_case` 一般映射到 Java 的 `camelCase`。
- Python `async` / `AsyncIterator` 一般映射到 Java 同步方法 / `Iterator` / Virtual Thread。
- Python `Pydantic` / `dataclass` 字段，一般映射为 Java 字段 + `getter/setter` / `record` 访问器。
- Python 下划线私有方法只在它影响对外扩展点或缺漏判断时列出。

## 总体结论

- 新版 `controller` 主干已经形成稳定映射，`Controller`、`TaskManager`、`TaskScheduler`、`EventQueue`、`IntentRecognizer`、`schema` 模型大多都有明确 Java 对位实现。
- Java 版不是机械逐文件翻译，而是做了三类重构：同步化改写、类型拆分、以及将部分 Python 同文件定义拆到独立 Java 类中。
- 真正的缺口主要不在新版主干，而在 `legacy` 兼容层和少量 API 语义差异。第二轮仍缺的真实项见 `docs/FIXED/controller_fixed.md`。

## 1. 包级入口与导出

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `controller.__init__.__all__` | `com.openjiuwen.core.controller.*` 显式类导入 | Python 包级重导出 -> Java 显式 import | 部分映射 | Java 无与 Python `__all__` 等价的统一门面 |
| `controller.__init__` 中 `schema/modules/base/config` 重导出 | Java 顶层类 + 子包类 | `ControllerConfig`、`Controller`、`EventQueue`、`TaskManager` 等可分别导入 | 适配映射 | Java 按包直引，不做聚合导出 |
| `controller.__init__` 中 legacy 重导出 | `legacy.*` 子包 | `BaseController`、`IntentDetectionController`、`legacy.event.Event`、`legacy.task.Task` 等分包可用 | 部分映射 | Java legacy 面存在，但并未保持 Python 的完整导出面 |

## 2. 顶层主类

### 2.1 ControllerConfig

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ControllerConfig(BaseModel)` | `ControllerConfig` | 字段 1:1 映射到 Java 字段 + `getter/setter` | 完全映射 | `max_concurrent_tasks -> maxConcurrentTasks` 等均已存在 |
| `max_concurrent_tasks` | `getMaxConcurrentTasks()/setMaxConcurrentTasks()` | 属性 -> getter/setter | 适配映射 | - |
| `schedule_interval` | `getScheduleInterval()/setScheduleInterval()` | 属性 -> getter/setter | 适配映射 | Java 保留最小值校验 |
| `task_timeout` | `getTaskTimeout()/setTaskTimeout()` | 属性 -> getter/setter | 适配映射 | Java 同样保留 `>= 600` 约束 |
| `default_task_priority` | `getDefaultTaskPriority()/setDefaultTaskPriority()` | 属性 -> getter/setter | 适配映射 | - |
| `enable_task_persistence` | `isEnableTaskPersistence()/setEnableTaskPersistence()` | 属性 -> getter/setter | 适配映射 | - |
| `event_queue_size` | `getEventQueueSize()/setEventQueueSize()` | 属性 -> getter/setter | 适配映射 | - |
| `event_timeout` | `getEventTimeout()/setEventTimeout()` | 属性 -> getter/setter | 适配映射 | - |
| `enable_intent_recognition` | `isEnableIntentRecognition()/setEnableIntentRecognition()` | 属性 -> getter/setter | 适配映射 | - |
| `intent_llm_id` | `getIntentLlmId()/setIntentLlmId()` | 属性 -> getter/setter | 适配映射 | - |
| `intent_confidence_threshold` | `getIntentConfidenceThreshold()/setIntentConfidenceThreshold()` | 属性 -> getter/setter | 适配映射 | - |
| `intent_type_list` | `getIntentTypeList()/setIntentTypeList()` | 属性 -> getter/setter | 适配映射 | Java 默认值与 Python 一致，仅预置基础意图集合 |
| 无直接对位 | `defaultConfig()` | Java 便捷静态工厂 | Java 扩展 | Python 直接实例化，Java 额外提供默认构造入口 |

### 2.2 Controller

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Controller.__init__()` | `Controller()` | 构造器对位 | 完全映射 | 二者都做延迟依赖注入 |
| `init(card, config, ability_manager, context_engine)` | `init(card, config, abilityManager, contextEngine)` | `init -> init` | 完全映射 | Java 用 `BaseCard` / `Object abilityManager` |
| `event_queue` | `getEventQueue()` | 属性 -> getter | 适配映射 | - |
| `config` / `config.setter` | `getConfig()/setConfig()` | 属性 -> getter/setter | 完全映射 | 同样会向 `taskManager/eventQueue/taskScheduler/eventHandler` 级联更新 |
| `context_engine` / `context_engine.setter` | `getContextEngine()/setContextEngine()` | 属性 -> getter/setter | 适配映射 | - |
| `ability_manager` / `ability_manager.setter` | `getAbilityManager()/setAbilityManager()` | 属性 -> getter/setter | 适配映射 | Java 以 `Object` 保存 |
| `task_manager` | `getTaskManager()` | 属性 -> getter | 适配映射 | - |
| `task_scheduler` | `getTaskScheduler()` | 属性 -> getter | 适配映射 | - |
| `event_handler` | `getEventHandler()` | 属性 -> getter | 适配映射 | - |
| `set_event_handler(event_handler)` | `setEventHandler(eventHandler)` | `set_event_handler -> setEventHandler` | 完全映射 | 依赖注入逻辑一致 |
| `add_task_executor(task_type, builder)` | `addTaskExecutor(taskType, builder)` | `add_task_executor -> addTaskExecutor` | 完全映射 | 都支持 fluent 风格返回自身 |
| `remove_task_executor(task_type)` | `removeTaskExecutor(taskType)` | `remove_task_executor -> removeTaskExecutor` | 完全映射 | - |
| `_restore_task_manager_state(session)` | `restoreTaskManagerState(session)` | 私有恢复逻辑对位 | 完全映射 | Java 去掉 async，但语义一致 |
| `_save_task_manager_state(session)` | `saveTaskManagerState(session)` | 私有保存逻辑对位 | 完全映射 | - |
| `get_task_executor(...)` | `getTaskExecutor(taskType, dependencies)` | 获取执行器入口保留 | 部分映射 | Python 该方法签名本身与 registry 用法不完全一致；Java 改成更清晰的 `taskType + dependencies` |
| `start()` | `start()` | `start -> start` | 完全映射 | 都会启动 `eventQueue + taskScheduler` |
| `stop()` | `stop()` | `stop -> stop` | 完全映射 | - |
| `invoke(inputs, session, **kwargs)` | `invoke(inputs, session)` | `invoke -> invoke` | 部分映射 | Java 去掉 `**kwargs`，并同步收集 `stream()` 输出 |
| `stream(inputs, session, stream_modes=None, **kwargs)` | `stream(inputs, session, streamModes)` | `stream -> stream` | 部分映射 | Java 用 `Iterator<Object>` 包装流；去掉 `kwargs`，`streamModes` 也不再可省略 |
| Python `stream()` 中事件循环切换自动重建 | 无直接对位 | 无同名生命周期逻辑 | 适配差异 | Java 同步模型下无 event loop 绑定问题 |
| 无直接对位 | `cleanup(...)`、`emitCompletionSignalIfIdle(...)`、`ControllerStreamIterator` | Java 内部辅助逻辑 | Java 扩展 | 用于同步流收尾和空闲会话完成信号 |

## 3. modules

### 3.1 事件处理与队列

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `EventHandlerInput(BaseModel)` | `EventHandlerInput` | `event/session` 字段 -> Java 构造器 + getter | 完全映射 | - |
| `EventHandler(ABC)` | `EventHandler` | `handle_input/handle_task_interaction/handle_task_completion/handle_task_failed -> handleInput/handleTaskInteraction/handleTaskCompletion/handleTaskFailed` | 完全映射 | 依赖字段同样保留 |
| `config/context_engine/task_manager/ability_manager/task_scheduler` 属性 | `get*/set*` | 属性 -> getter/setter | 适配映射 | Java 保持同等注入面 |
| `EventQueue(config)` | `EventQueue(config)` | 构造器对位 | 完全映射 | - |
| `config` / `config.setter` | `getConfig()/setConfig()` | 属性 -> getter/setter | 适配映射 | - |
| `set_event_handler()` | `setEventHandler()` | `set_event_handler -> setEventHandler` | 完全映射 | - |
| `start()` / `stop()` | `start()` / `stop()` | 生命周期方法对位 | 完全映射 | Java `stop()` 同时清空订阅 |
| `_subscribe_event()` / `_unsubscribe_event()` | `subscribeEvent()` / `unsubscribeEvent()` | 私有 helper 对位 | 适配映射 | Java 用内部 `TopicSubscription` 代替 Python MQ subscription wrapper |
| `subscribe(agent_id, session_id)` | `subscribe(agentId, sessionId)` | `subscribe -> subscribe` | 部分映射 | Python 返回 `(subscriptions, topics)`；Java 返回 `void` |
| `unsubscribe(agent_id, session_id)` | `unsubscribe(agentId, sessionId)` | `unsubscribe -> unsubscribe` | 部分映射 | Python 返回 topic 字典；Java 返回 `void` |
| `publish_event(agent_id, session, event)` | `publishEvent(agentId, session, event)` | `publish_event -> publishEvent` | 完全映射 | 都是同步等待 handler 完成后返回 |
| `unsubscribe_all()` | `unsubscribeAll()` | `unsubscribe_all -> unsubscribeAll` | 完全映射 | - |
| `_build_topic()` | `buildTopic()` | `snake_case -> camelCase` | 完全映射 | - |

### 3.2 意图识别

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `IntentToolkits.__init__(event, confidence_threshold)` | `IntentToolkits(event, confidenceThreshold)` | 构造器对位 | 完全映射 | - |
| `_low_confidence_intent()` | `lowConfidenceIntent()` | 私有 helper 对位 | 完全映射 | - |
| `create_task/pause_task/cancel_task/resume_task/unknown_task/create_dependent_task/modify_task/supplement_task` | `createTask/pauseTask/cancelTask/resumeTask/unknownTask/createDependentTask/modifyTask/supplementTask` | 意图构造方法一一对应 | 完全映射 | Java 返回 `IntentResult` record，等价于 Python `(Intent, str)` |
| `get_openai_tool_schemas(choices=None)` | `getOpenaiToolSchemas(choices)` | `get_openai_tool_schemas -> getOpenaiToolSchemas` | 完全映射 | Java 会按 `choices` 过滤 |
| 无直接对位 | `dispatch(toolName, arguments)` | Java 分发器 | Java 扩展 | Python 用 `getattr(toolkits, tool_call.name)` 动态反射 |
| `IntentRecognizer.__init__(config, task_manager, ability_manager, context_engine)` | `IntentRecognizer(config, taskManager, abilityManager, contextEngine, modelProvider)` | 构造器主干一致 | 部分映射 | Java 额外显式注入 `ModelProvider`，避免直接依赖 `Runner` |
| `_prepare_user_message(query)` | `prepareUserMessage(query)` | 私有 helper 对位 | 完全映射 | - |
| `recognize(event, session)` | `recognize(event, session)` | `recognize -> recognize` | 部分映射 | Java 不自动创建 `ModelContext`，而是要求调用方预先准备 |
| `EventHandlerWithIntentRecognition.__init__()` | `EventHandlerWithIntentRecognition(modelProvider)` | 构造器语义对位 | 部分映射 | Java 需要外部提供 model provider |
| `handle_input()` | `handleInput()` | `handle_input -> handleInput` | 完全映射 | Java 用 Virtual Thread 并发处理每个 intent |
| `handle_task_interaction()` | `handleTaskInteraction()` | `handle_task_interaction -> handleTaskInteraction` | 完全映射 | - |
| `handle_task_completion()` | `handleTaskCompletion()` | `handle_task_completion -> handleTaskCompletion` | 完全映射 | - |
| `handle_task_failed()` | `handleTaskFailed()` | `handle_task_failed -> handleTaskFailed` | 完全映射 | - |
| `_process_create_task_intent()` 等 8 个私有处理器 | `processCreateTaskIntent()` 等 8 个私有处理器 | 私有处理流程一一对应 | 完全映射 | 创建、暂停、恢复、继续、补充、取消、修改、未知意图均已覆盖 |
| 无直接对位 | `initRecognizer()` | Java 延迟初始化 recognizer | Java 扩展 | Python 在 `__init__` 中直接构造，但当时依赖尚未注入，Java 这里更稳妥 |

### 3.3 任务执行与调度

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `TaskExecutorDependencies` dataclass | `TaskExecutorDependencies` | 字段 1:1 -> getter | 完全映射 | Java 从 dataclass 改为不可变依赖载体 |
| `modules.task_executor.TaskExecutor` | `TaskExecutor` | `execute/can_pause/pause/can_cancel/cancel -> executeAbility/canPause/pause/canCancel/cancel` | 部分映射 | Java 仅保留一份抽象执行器 API；Python 还额外存在单独 `task_executor.py` 版本 |
| `modules.task_scheduler.TaskExecutor` | `TaskExecutor` | `execute_ability -> executeAbility`；其余方法同名驼峰映射 | 完全映射 | Java 的 `TaskExecutor` 实际对应 Python 调度器内部那一版 |
| `TaskExecutorRegistry` | `TaskExecutorRegistry` | `add_task_executor/remove_task_executor/get_task_executor -> addTaskExecutor/removeTaskExecutor/getTaskExecutor` | 完全映射 | - |
| `TaskManagerState(BaseModel)` | `TaskManagerState` | `tasks/priority_index/parent_to_children/children_to_parent/root_tasks` -> getter/setter + `toMap/fromMap` | 完全映射 | Java 补了序列化辅助方法 |
| `TaskFilter(BaseModel)` | `TaskFilter` | 字段映射 + `validate_at_least_one_filter -> validate()` | 完全映射 | Java 额外提供 `byTaskId/bySessionId/byStatus/byRoot/byHighestPriority` 和 builder |
| `TaskManager.__init__(config)` | `TaskManager(config)` | 构造器对位 | 完全映射 | - |
| `config` / `config.setter` | `getConfig()/setConfig()` | 属性 -> getter/setter | 适配映射 | - |
| `get_state/load_state/clear_state` | `getState/loadState/clearState` | 方法一一对应 | 完全映射 | Java 同步锁替代 asyncio.Lock |
| `add_task/get_task/pop_task/update_task/remove_task/get_child_task/update_task_status/set_priority` | `addTask/getTask/popTask/updateTask/removeTask/getChildTask/updateTaskStatus/setPriority` | 主要任务管理 API 一一对应 | 完全映射 | Java 还拆出单任务和多任务重载 |
| `_collect_all_children()` | `collectAllChildren()` | 私有 helper 对位 | 完全映射 | - |
| `TaskScheduler.__init__(...)` | `TaskScheduler(...)` | 构造器对位 | 完全映射 | - |
| `config` / `sessions` / `task_manager` / `task_executor_registry` | `getConfig()/setConfig()/getSessions()/getTaskManager()/getTaskExecutorRegistry()` | 属性 -> getter/setter | 适配映射 | - |
| `_handle_task_execution_failure()` | `handleTaskExecutionFailure()` | 私有失败处理逻辑对位 | 完全映射 | - |
| `_execute_task_wrapper()` | `executeTaskWrapper()` | 私有包裹执行逻辑对位 | 完全映射 | Java 用 watchdog + 线程中断替代 `asyncio.wait_for` |
| `execute_task()` | `executeTask()` | `execute_task -> executeTask` | 完全映射 | - |
| `_are_all_tasks_completed()` | `areAllTasksCompleted()` | 私有完成判定逻辑对位 | 完全映射 | - |
| `_ensure_session_completion_signal()` | `ensureSessionCompletionSignal()` | 私有完成信号逻辑对位 | 完全映射 | - |
| `_publish_task_event()` | `publishTaskEvent()` | 私有任务事件发布对位 | 完全映射 | - |
| `pause_task()` | `pauseTask()` | `pause_task -> pauseTask` | 完全映射 | - |
| `cancel_task()` | `cancelTask()` | `cancel_task -> cancelTask` | 完全映射 | - |
| `schedule()` | `scheduleLoop()` + `start()` | 调度循环逻辑保留 | 适配映射 | Python 用 async loop；Java 用 `ScheduledExecutorService` |
| `_wait_all_tasks_complete()` | `stop()` 内 join + 清理 | 停机等待行为保留 | 适配映射 | Java 未保留单独公开/私有同名方法 |
| `start()` / `stop()` | `start()` / `stop()` | 生命周期方法对位 | 完全映射 | - |

## 4. schema

### 4.1 DataFrame / Event / Intent / Task / Output

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `BaseDataFrame` / `TextDataFrame` / `FileDataFrame` / `JsonDataFrame` | `DataFrame` sealed interface + `TextDataFrame` / `FileDataFrame` / `JsonDataFrame` record | Python 模型字段 -> Java record 组件与 `getType()` | 完全映射 | Java 把 Python 四个类收束到一个 sealed 层次 |
| `EventType` | `EventType` | 枚举值 -> `getValue()/fromValue()/toString()` | 完全映射 | - |
| `Event(BaseModel)` | `Event` | `event_type/event_id/metadata` -> getter/setter | 完全映射 | - |
| `InputEvent.from_user_input()` | `InputEvent.fromUserInput()` | `from_user_input -> fromUserInput` | 完全映射 | - |
| `TaskInteractionEvent` | `TaskInteractionEvent` | `interaction/task` -> getter/setter | 完全映射 | - |
| `TaskCompletionEvent` | `TaskCompletionEvent` | `task_result/task` -> getter/setter | 完全映射 | - |
| `TaskFailedEvent` | `TaskFailedEvent` | `error_message/task` -> getter/setter | 完全映射 | - |
| `IntentType` | `IntentType` | 枚举值 -> `getValue()/fromValue()/toString()` | 完全映射 | - |
| `Intent(BaseModel)` | `Intent` | 字段 1:1 + `_validate -> validate()` | 完全映射 | Java 用构造器和 setter 驱动校验 |
| `TaskStatus` | `TaskStatus` | 枚举值 -> `getValue()/fromValue()/toString()` | 完全映射 | - |
| `Task(BaseModel)` | `Task` | 字段 1:1 + `validate()` | 完全映射 | Java 额外补了 `copy()/toMap()/fromMap()` |
| `ControllerOutputPayload(BaseModel)` | `ControllerOutputPayload` | `type/data/metadata` -> getter/setter | 完全映射 | Java 额外提供 `TASK_PROCESSING`、`ALL_TASKS_PROCESSED` 常量 |
| 无直接对位 | `ControllerOutputPayload.allTasksProcessed()` | Java 静态工厂 | Java 扩展 | Python 通过直接实例化构造完成消息 |
| `ControllerOutputChunk(OutputSchema)` | `ControllerOutputChunk` | `payload -> controllerPayload`；`last_chunk -> lastChunk` | 部分映射 | Java 字段名比 Python 更显式 |
| `ControllerOutput(BaseModel)` | `ControllerOutput` | `type/data/input_event_id` -> getter/setter | 完全映射 | Java 额外区分 `getDataAsChunks()/getDataAsMap()` |

## 5. legacy

### 5.1 controller / intent_detection_controller

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.controller.BaseController` | `legacy.BaseController` | 主体骨架对位 | 部分映射 | Java 保留消息队列式控制器，但实现明显简化 |
| `__init__(config, context_engine, session)` | `BaseController()` / `BaseController(config, contextEngine)` | 构造器对位 | 部分映射 | Java 去掉 `session` 入参 |
| `setup_from_agent()` | `setupFromAgent()` | `setup_from_agent -> setupFromAgent` | 完全映射 | 都会从 agent 注入配置与上下文 |
| `_get_or_create_subscription()` | `getOrCreateSubscription()` | 私有 lazy subscription 对位 | 完全映射 | - |
| `invoke(inputs, session)` | `invoke(inputs, session)` | `invoke -> invoke` | 完全映射 | - |
| `_handle_message_wrapper()` | subscription handler lambda | 内部消息包裹逻辑保留 | 适配映射 | Java 直接在订阅 handler 中做拆包 |
| `handle_event()` | `handleEvent()` | 抽象分发入口对位 | 完全映射 | - |
| `create_message()` | `createMessage()` | `create_message -> createMessage` | 完全映射 | - |
| `cleanup_conversation()` | `cleanupConversation()` | `cleanup_conversation -> cleanupConversation` | 部分映射 | Java 只做 `deactivate`，未像 Python 那样显式 `unsubscribe` |
| `stop()` | `stop()` | `stop -> stop` | 完全映射 | - |
| `set_group()` | `setGroup()` | `set_group -> setGroup` | 完全映射 | - |
| `send_to_agent()` | `sendToAgent()` | `send_to_agent -> sendToAgent` | 部分映射 | Python 委派 group controller；Java 直接抛 `UnsupportedOperationException` |
| `publish()` | `publish()` | `publish -> publish` | 部分映射 | Python 委派 group controller；Java 直接抛 `UnsupportedOperationException` |
| `legacy.intent_detection_controller.RunningTaskInfo` | `IntentDetectionController.RunningTaskInfo` | 数据结构对位 | 完全映射 | Python `asyncio_task` -> Java `Future<?>` |
| `legacy.intent_detection_controller.TaskQueue` | `IntentDetectionController.TaskQueue` | `register_task/cancel_running_task/unregister_task/find_task/has_running_task -> registerTask/cancelRunningTask/unregisterTask/findTask/hasRunningTask` | 完全映射 | Java 用同步 `Future.cancel(true)` 替代 async cancel |
| `legacy.intent_detection_controller.IntentType` | `IntentDetectionController.IntentType` | 枚举值映射 | 完全映射 | `ExecNewTask -> EXEC_NEW_TASK` 等 |
| `legacy.intent_detection_controller.Intent` | `IntentDetectionController.Intent` | `intent_type/task/workflow/metadata` -> getter/setter | 完全映射 | Java 用 Lombok builder |
| `IntentDetectionController.invoke()` | 无直接覆写 | 无同名实时中断入口 | 缺失 | Python 覆写 `invoke()` 以取消旧 handler / workflow task；Java 没有 |
| `handle_event()` | `handleEvent()` | 事件分发入口对位 | 部分映射 | Java 去掉 `MessageUtils.add_user_message()` 与 handler 注册追踪 |
| `_handle_new_task/_handle_resume/_handle_cancel/_handle_default_response/_handle_unknown_intent` | `handleNewTask/handleResume/handleCancel/handleDefaultResponse/handleUnknownIntent` | 私有/保护处理器基本对位 | 部分映射 | Java 缺少 Python 复杂 resume 输入重建与 workflow 中断态恢复逻辑 |
| `intent_detection()` | `intentDetection()` | 抽象方法对位 | 完全映射 | - |
| `exec_task()` | `execTask()` | 抽象方法对位 | 完全映射 | - |
| `interrupt_task()` | `interruptTask()` | 抽象方法对位 | 完全映射 | 但 Java 当前主流程未真正调用它 |

### 5.2 legacy.event

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `EventType/EventPriority/SourceType` | `Event.EventType/EventPriority/SourceType` | 枚举值对位 | 完全映射 | Java 内嵌到 `Event` 中 |
| `EventSource` | `Event.EventSource` | `conversation_id/source_type/user_id` -> getter/setter | 完全映射 | - |
| `EventContent.get_query()` | `Event.EventContent.getQueryText()` | `get_query -> getQueryText` | 完全映射 | - |
| `EventContext` | `Event.EventContext` | 字段对位 | 完全映射 | - |
| `Event.create_user_event()` | `Event.createUserEvent()` | `create_user_event -> createUserEvent` | 完全映射 | - |
| `Event.create_task_completed()` | `Event.createTaskCompleted()` | `create_task_completed -> createTaskCompleted` | 完全映射 | - |
| `Event.create_task_interrupted()` | `Event.createTaskInterrupted()` | `create_task_interrupted -> createTaskInterrupted` | 完全映射 | - |
| `Event.create_error_event()` | `Event.createErrorEvent()` | `create_error_event -> createErrorEvent` | 完全映射 | - |
| `Event.create_info_event()` | `Event.createInfoEvent()` | `create_info_event -> createInfoEvent` | 完全映射 | - |
| `Event.create_agent_response()` | 无直接对位 | 无同名工厂 | 缺失 | Java legacy event 缺少 agent response 便捷工厂 |
| `Event.create_agent_handoff()` | 无直接对位 | 无同名工厂 | 缺失 | Java legacy event 缺少 handoff 便捷工厂 |
| `set_correlation()/set_conversation()` | 无直接对位 | 无同名便捷方法 | 缺失 | Java 只能直接改 context 字段 |
| `is_from_user()/is_from_agent()/is_task_related()/is_workflow_related()` | 无直接对位 | 无同名判断方法 | 缺失 | Java 只保留 `getDisplayContent()` |
| `to_dict()` | 无直接对位 | 无同名序列化方法 | 缺失 | Java 依赖 Lombok / Jackson，而非显式 helper |

### 5.3 legacy.task / reasoner / config / utils / constants

| Python API | Java API | 方法/API 映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.task.TaskStatus` | `legacy.task.Task.TaskStatus` | 枚举值对位 | 完全映射 | Java 合并为嵌套枚举 |
| `legacy.task.DependencyType` | `legacy.task.Task.DependencyType` | 枚举值对位 | 完全映射 | - |
| `legacy.task.TaskDependency` | `legacy.task.Task.TaskDependency` | 字段对位 | 完全映射 | - |
| `legacy.task.TaskInput` | `legacy.task.Task.TaskInput` | 字段对位 | 完全映射 | - |
| `legacy.task.TaskResult` | `legacy.task.Task.TaskResult` | 字段对位 | 完全映射 | - |
| `legacy.task.Task` | `legacy.task.Task` | `set_agent_id -> setAgentId` + 字段对位 | 完全映射 | Java 通过嵌套类型维持同一文件组织 |
| `legacy.reasoner.Planner` concrete class | `legacy.reasoner.Planner` interface | `process_message -> plan/process*` 语义缩减 | 部分映射 | Java 只保留接口，不带 Python 默认实现 |
| `legacy.reasoner.IntentDetector` concrete class | `legacy.reasoner.IntentDetector` interface | `process_message` 等默认实现未保留 | 部分映射 | Java 只保留契约 |
| `legacy.reasoner.AgentReasoner` | `legacy.reasoner.AgentReasoner` | `detect/plan` 主入口对位 | 部分映射 | Java 仅做委派包装，能力深度弱于 Python |
| `legacy.config.get_default_template()` | 无直接对位 | 无同名 helper | 缺失 | Java 没有默认模板函数 |
| `IntentDetectionConfig/PlannerConfig/ProactiveIdentifierConfig/ReflectorConfig/ReasonerConfig` | `ReasonerConfig` | 多配置模型收束为单类 | 部分映射 | Java 缺少 Python 的分层配置对象 |
| `legacy.utils.MessageHandlerUtils` | 无直接对位 | 无同名工具类 | 缺失 | LLM 输入拼装、历史处理、任务解析 helper 未迁移 |
| `legacy.utils.ReasonerUtils` | 无直接对位 | 无同名工具类 | 缺失 | 获取模型、历史拼接等工具逻辑未迁移 |
| `legacy.constants.IntentDetectionConstants` | 无直接对位 | 无同名常量类 | 缺失 | `DEFAULT_CLASS` 常量未保留 |

## 6. Java 侧新增或拆分实现

- `modules.TaskExecutorDependencies`、`TaskExecutorRegistry`、`TaskFilter`、`TaskManagerState`：Python 有同职责对象，但多数与其他类共文件；Java 拆成独立类。
- `ControllerStreamIterator`、`emitCompletionSignalIfIdle()`：用于同步流式收尾，Python 无同名结构。
- `IntentRecognizer.ModelProvider`：Java 用显式 provider 代替 Python 直接依赖 `Runner.resource_mgr`。
- `TaskScheduler.RunningTaskEntry`：Java 用于绑定执行器与运行线程，Python 直接用 `Dict[str, Tuple[TaskExecutor, asyncio.Task]]`。

## 7. 结论

- 新版 `controller` 的类级和方法级主链路已经基本对齐，特别是 `Controller`、`TaskManager`、`TaskScheduler`、`EventQueue`、`IntentRecognizer` 与 `schema` 模型，Java 版已经达到可核对的完整实现状态。
- 当前真正的缺漏主要集中在 `legacy` 层：Python 的兼容 helper、事件便捷方法、group 路由委派、实时中断细节、reasoner 默认实现与工具类，Java 版仍保留明显空洞。
- 第二轮仍需补齐的真实缺口，见 `docs/FIXED/controller_fixed.md`。