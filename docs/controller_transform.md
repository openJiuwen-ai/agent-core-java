# Controller模块 Python→Java 转换报告

## 1. 概述

本报告记录了将Python版Controller模块（openjiuwen.core.controller）转换为Java版的完整过程、技术决策和映射关系。

- **Python源码位置**: `agent-core-main/agent-core-v0.1.7/openjiuwen/core/controller/`
- **Java目标位置**: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/controller/`
- **Python源文件**: 32个 `.py` 文件，共 6854 行
- **Java目标文件**: 29个 `.java` 文件，共 4699 行
- **Java JDK版本**: 21
- **编译状态**: ✅ 通过（仅剩2个unused-field警告）

---

## 2. 文件映射关系

### 2.1 Schema层（数据模型）

| Python源文件 | Java目标文件 | 行数 | 说明 |
|---|---|---|---|
| `schema/dataframe.py` (82行) | `schema/DataFrame.java` (62行) | — | `Union[TextDataFrame, FileDataFrame, JsonDataFrame]` → `sealed interface` + 3个 `record` 内部类 |
| `schema/event.py` (137行) | `schema/Event.java` (62行) | — | Event基类 |
| — | `schema/EventType.java` (49行) | — | Python `str,Enum` → Java `enum`，含 `getValue()`/`fromValue()` |
| `schema/event.py` | `schema/InputEvent.java` (60行) | — | 从event.py中拆分 |
| `schema/event.py` | `schema/TaskInteractionEvent.java` (47行) | — | 从event.py中拆分 |
| `schema/event.py` | `schema/TaskCompletionEvent.java` (47行) | — | 从event.py中拆分 |
| `schema/event.py` | `schema/TaskFailedEvent.java` (43行) | — | 从event.py中拆分 |
| `schema/intent.py` (189行) | `schema/Intent.java` (206行) | — | 含完整校验逻辑 |
| — | `schema/IntentType.java` (66行) | — | Python `str,Enum` → Java `enum` |
| `schema/task.py` (171行) | `schema/Task.java` (266行) | — | 含 `toMap()`/`fromMap()` 序列化方法 |
| — | `schema/TaskStatus.java` (50行) | — | Python `str,Enum` → Java `enum` |
| `schema/controller_output.py` (78行) | `schema/ControllerOutputPayload.java` (87行) | — | 含 `allTasksProcessed()` 静态工厂方法 |
| — | `schema/ControllerOutputChunk.java` (57行) | — | 继承 `OutputSchema`，含 `controllerPayload` 强类型字段 |
| — | `schema/ControllerOutput.java` (52行) | — | 批量输出 |

**Schema层合计**: Python 5文件 657行 → Java 14文件 1154行

### 2.2 Config层

| Python源文件 | Java目标文件 | 行数 | 说明 |
|---|---|---|---|
| `config.py` (134行) | `ControllerConfig.java` (178行) | — | 所有配置参数全部保留 |

### 2.3 Modules层（核心模块）

| Python源文件 | Java目标文件 | 行数 | 说明 |
|---|---|---|---|
| `modules/event_handler.py` (161行) | `modules/EventHandler.java` (106行) | — | `ABC` → `abstract class` |
| — | `modules/EventHandlerInput.java` (33行) | — | Python元组 → Java封装类 |
| `modules/event_handler.py` + `intent_detection_controller.py` | `modules/EventHandlerWithIntentRecognition.java` (256行) | — | 合并意图识别事件处理逻辑 |
| `modules/event_queue.py` (293行) | `modules/EventQueue.java` (192行) | — | Python `MessageQueueInMemory` → Java同步分发 |
| `modules/intent_recognizer.py` (417行) | `modules/IntentRecognizer.java` (224行) | — | 含 `ModelProvider` 函数式接口解耦LLM |
| `modules/intent_toolkits.py` (392行) | `modules/IntentToolkits.java` (307行) | — | 8种意图工具Schema + dispatch |
| `modules/task_executor.py` (135行) | `modules/TaskExecutor.java` (91行) | — | `ABC` → `abstract class`，含 `PauseCheckResult`/`CancelCheckResult` 内部record |
| — | `modules/TaskExecutorDependencies.java` (58行) | — | Python `dataclass` → 不可变Java类 |
| — | `modules/TaskExecutorRegistry.java` (61行) | — | 类型安全的执行器注册表 |
| `modules/task_manager.py` (842行) | `modules/TaskManager.java` (628行) | — | 完整CRUD + `ReentrantLock` |
| — | `modules/TaskManagerState.java` (152行) | — | 状态持久化，含 `toMap()`/`fromMap()` |
| — | `modules/TaskFilter.java` (201行) | — | Predicate + Builder模式 |
| `modules/task_scheduler.py` (859行) | `modules/TaskScheduler.java` (634行) | — | 虚拟线程 + 超时看门狗 |

**Modules层合计**: Python 7文件 3099行 → Java 13文件 2943行

### 2.4 Controller主类

| Python源文件 | Java目标文件 | 行数 | 说明 |
|---|---|---|---|
| `controller.py` (357行) + `base.py` (473行) + `intent_detection_controller.py` (579行) | `Controller.java` (424行) | — | 合并为统一入口 |

---

## 3. 关键技术决策

### 3.1 异步模型：asyncio → Virtual Threads

| Python | Java | 说明 |
|---|---|---|
| `asyncio.Lock` | `ReentrantLock` | 协程锁 → 线程锁 |
| `asyncio.create_task()` | `Thread.ofVirtual().start()` | 协程任务 → 虚拟线程 |
| `asyncio.Task` | `Thread` | 可取消的执行单元 |
| `asyncio.sleep()` | `ScheduledExecutorService.scheduleWithFixedDelay()` | 定时调度 |
| `async for` | `Iterator<>` | 异步迭代 → 同步迭代器 |
| `asyncio.Queue` | 同步分发（简化） | Python的publish_event await结果，本质是同步 |
| `await task.cancel()` | `Thread.interrupt()` | 取消机制 |
| `async def` → `await` | 普通方法调用 | 虚拟线程下无需async/await |

### 3.2 类型系统

| Python | Java | 说明 |
|---|---|---|
| `Union[A, B, C]` | `sealed interface permits A, B, C` | 密封接口+record |
| `Optional[T]` | `T`（可为null） | Java无Optional包装 |
| `BaseModel` (Pydantic) | POJO + 手动验证 | 构造函数中调用`validate()` |
| `@dataclass` | 普通Java类 | 构造函数+getter/setter |
| `str, Enum` | `enum` + `getValue()`/`fromValue()` | 双向转换 |
| `Dict[str, Any]` | `Map<String, Object>` | — |
| `Callable` / `Awaitable` | `Function<>` / `Consumer<>` | 函数式接口 |
| `Tuple[Event, Session]` | `EventHandlerInput` 封装类 | 类型安全 |

### 3.3 序列化

| Python | Java | 说明 |
|---|---|---|
| `model_dump()` | `toMap()` | 序列化为Map |
| `model_validate()` | `fromMap()` | 从Map反序列化 |
| `json.loads()` | `ObjectMapper.readValue()` | JSON反序列化（Jackson） |

### 3.4 错误处理

| Python | Java | 说明 |
|---|---|---|
| `raise AgentError(...)` | `throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_*, ...)` | 统一异常工厂 |
| `try/except` | `try/catch` | — |
| 日志 `logger.error(...)` | `Loggers.CONTROLLER.error(...)` | 统一日志门面 |

### 3.5 模块化拆分

Python将多个概念混合在单个文件中，Java按单一职责拆分：

- **Python `schema/event.py`** (384行) → Java拆分为 `Event.java` + `EventType.java` + `InputEvent.java` + `TaskInteractionEvent.java` + `TaskCompletionEvent.java` + `TaskFailedEvent.java` (共6个文件)
- **Python `schema/task.py`** (171行) → Java拆分为 `Task.java` + `TaskStatus.java` (2个文件)
- **Python `task_manager.py`** (842行) → Java拆分为 `TaskManager.java` + `TaskManagerState.java` + `TaskFilter.java` (3个文件)
- **Python `task_executor.py`** (135行) → Java拆分为 `TaskExecutor.java` + `TaskExecutorDependencies.java` + `TaskExecutorRegistry.java` (3个文件)

---

## 4. 重要设计适配

### 4.1 OutputSchema基类的payload类型

`OutputSchema.getPayload()` 返回 `Object` 类型。为解决类型安全问题，`ControllerOutputChunk` 增加了 `controllerPayload` 字段（类型为 `ControllerOutputPayload`），同时在 `setControllerPayload()` 中同步更新基类的 `payload`。所有内部代码使用 `getControllerPayload()` 而非 `getPayload()` 以获取强类型访问。

### 4.2 AgentSessionApi 与 Session 接口

`AgentSessionApi` 不实现 `Session` 接口（它包装了内部的 `AgentSession`），但 `ContextEngine.createContext()` 要求 `Session` 参数。解决方案：
- `IntentRecognizer` 仅使用 `contextEngine.getContext()` 获取已有上下文
- 上下文的创建由上层调用方（Controller/TaskScheduler）负责

### 4.3 AbilityManager 类型

Python中 `AbilityManager` 来自 `single_agent` 模块（controller的上层依赖），Java中尚未实现该模块。解决方案：将所有 `abilityManager` 参数类型定义为 `Object`，待 `single_agent` 模块实现后替换为具体类型。

### 4.4 MessageQueue 简化

Python的 `EventQueue` 基于 `MessageQueueInMemory`（异步消息队列），但 `publish_event` 方法实际上 `await` 等待处理完成，本质是同步调用。Java版简化为直接同步分发：`publishEvent()` 直接调用订阅处理器的 `accept()` 方法。

### 4.5 任务超时机制

Python使用 `asyncio.wait_for(task, timeout=...)`。Java使用 `ScheduledExecutorService` 看门狗模式：
1. 任务在虚拟线程上执行
2. 独立的看门狗线程在超时后调用 `currentThread.interrupt()`
3. `finally` 块中取消看门狗并关闭其线程池

### 4.6 IntentRecognizer的LLM解耦

Python通过 `Runner` 类直接访问LLM。Java定义了 `ModelProvider` 函数式接口：
```java
@FunctionalInterface
public interface ModelProvider {
    Model getModel(String modelId, AgentSessionApi session);
}
```
由上层注入具体实现，避免对Runner的直接依赖。

---

## 5. Java文件目录结构

```
controller/
├── Controller.java                     (424行) - 主控制器
├── ControllerConfig.java               (178行) - 配置类
├── modules/
│   ├── EventHandler.java               (106行) - 事件处理器抽象基类
│   ├── EventHandlerInput.java           (33行) - 事件处理输入封装
│   ├── EventHandlerWithIntentRecognition.java (256行) - 意图识别事件处理器
│   ├── EventQueue.java                 (192行) - 事件队列
│   ├── IntentRecognizer.java           (224行) - 意图识别器
│   ├── IntentToolkits.java             (307行) - 意图工具集
│   ├── TaskExecutor.java                (91行) - 任务执行器抽象基类
│   ├── TaskExecutorDependencies.java    (58行) - 执行器依赖容器
│   ├── TaskExecutorRegistry.java        (61行) - 执行器注册表
│   ├── TaskFilter.java                 (201行) - 任务查询过滤器
│   ├── TaskManager.java               (628行) - 任务管理器
│   ├── TaskManagerState.java           (152行) - 任务状态持久化
│   └── TaskScheduler.java             (634行) - 任务调度器
└── schema/
    ├── ControllerOutput.java            (52行) - 批量输出
    ├── ControllerOutputChunk.java       (57行) - 流式输出块
    ├── ControllerOutputPayload.java     (87行) - 输出载荷
    ├── DataFrame.java                   (62行) - 数据帧密封接口
    ├── Event.java                       (62行) - 事件基类
    ├── EventType.java                   (49行) - 事件类型枚举
    ├── InputEvent.java                  (60行) - 输入事件
    ├── Intent.java                     (206行) - 意图数据模型
    ├── IntentType.java                  (66行) - 意图类型枚举
    ├── Task.java                       (266行) - 任务数据模型
    ├── TaskCompletionEvent.java         (47行) - 任务完成事件
    ├── TaskFailedEvent.java             (43行) - 任务失败事件
    ├── TaskInteractionEvent.java        (47行) - 任务交互事件
    └── TaskStatus.java                  (50行) - 任务状态枚举
```

---

## 6. 依赖的已有Java模块

| 模块 | 类 | 用途 |
|---|---|---|
| `common.exception` | `ErrorHelper`, `StatusCode` | 异常构建 |
| `common.logging` | `Loggers.CONTROLLER` | 日志记录 |
| `common.schema` | `BaseCard` | Agent卡片 |
| `context` | `ContextEngine`, `ModelContext` | 上下文管理 |
| `session` | `AgentSessionApi`, `Session`, `BaseSession` | 会话管理 |
| `session.stream` | `OutputSchema`, `StreamMode` | 流式输出 |
| `foundation.llm` | `Model` | LLM调用 |
| `foundation.llm.schema` | `BaseMessage`, `SystemMessage`, `UserMessage`, `AssistantMessage`, `ToolMessage`, `ToolCall` | 消息类型 |
| Jackson | `ObjectMapper`, `TypeReference` | JSON序列化 |

---

## 7. 已知限制与后续工作

1. **AbilityManager类型**: 当前为 `Object`，待 `single_agent` 模块实现后升级为强类型
2. **AgentCard**: Python有独立的 `AgentCard` 类，Java使用 `BaseCard` 替代（字段兼容）
3. **Planner/AgentReasoner**: Python controller模块含 `planner.py`、`agent_reasoner.py`等推理器子模块（共约123行），当前Java版未包含（这些是0行或空文件的占位）
4. **ReasonerConfig**: Python含 `reasoner_config.py`（66行），当前未转换
5. **Constants**: Python的 `constants.py`（14行）中的常量已内联到对应Java类中
6. **单元测试**: 未包含在此次转换中，建议后续补充

---

## 8. 统计摘要

| 指标 | Python | Java |
|---|---|---|
| 源文件数 | 32 | 29 |
| 总代码行数 | 6854 | 4699 |
| Schema类 | 5文件 | 14文件 |
| 模块类 | 7文件 | 13文件 |
| 主控制器 | 3文件合并 | 1文件 |
| 配置类 | 1文件 | 1文件 |
| 编译错误 | — | 0 |
| 编译警告 | — | 2（unused field） |
