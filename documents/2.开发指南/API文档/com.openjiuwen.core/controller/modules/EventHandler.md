# com.openjiuwen.core.controller.modules.EventHandler

## abstract class EventHandler

```java
public abstract class EventHandler
```

`EventHandler` 是控制器事件处理器的抽象基类，定义了输入事件、任务交互事件、任务完成事件和任务失败事件的统一处理接口。

## 注入依赖

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 控制器配置。 |
| `contextEngine` | `ContextEngine` | 上下文引擎。 |
| `abilityManager` | `Object` | 业务能力管理器。 |
| `taskManager` | `TaskManager` | 任务管理器。 |
| `taskScheduler` | `TaskScheduler` | 任务调度器。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setConfig(ControllerConfig config)` | `void` | 设置控制器配置。 |
| `setContextEngine(ContextEngine contextEngine)` | `void` | 注入上下文引擎。 |
| `setAbilityManager(Object abilityManager)` | `void` | 注入能力管理器。 |
| `setTaskManager(TaskManager taskManager)` | `void` | 注入任务管理器。 |
| `setTaskScheduler(TaskScheduler taskScheduler)` | `void` | 注入任务调度器。 |
| `handleInput(EventHandlerInput inputs)` | `Map<String, Object>` | 处理输入事件。 |
| `handleTaskInteraction(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务交互事件。 |
| `handleTaskCompletion(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务完成事件。 |
| `handleTaskFailed(EventHandlerInput inputs)` | `Map<String, Object>` | 处理任务失败事件。 |

## 说明

- `Controller.setEventHandler()` 会在绑定处理器时完成上述依赖注入，因此业务实现通常不需要自己管理生命周期。
- 四个 `handle*` 方法都是抽象方法；具体控制器需要根据自己的任务模型实现事件路由逻辑。
