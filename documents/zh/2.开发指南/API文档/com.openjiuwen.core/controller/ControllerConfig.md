# com.openjiuwen.core.controller.ControllerConfig

## class ControllerConfig

```java
public class ControllerConfig
```

`ControllerConfig` 定义控制器的调度、任务管理、事件队列和意图识别参数，并通过 setter 对关键阈值做基础校验。

## 配置项

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `maxConcurrentTasks` | `int` | `5` | 最大并发任务数；源码注释写明 `0` 表示不限。 |
| `scheduleInterval` | `double` | `1.0` | 调度器扫描 `SUBMITTED` 任务的间隔，单位秒。 |
| `taskTimeout` | `Double` | `null` | 单任务超时时间，单位秒；为 `null` 时不启用超时。 |
| `defaultTaskPriority` | `int` | `1` | 默认任务优先级，数值越大越优先。 |
| `enableTaskPersistence` | `boolean` | `false` | 是否把 `TaskManagerState` 保存到会话状态。 |
| `eventQueueSize` | `int` | `10000` | 事件队列容量配置。 |
| `eventTimeout` | `double` | `300` | 事件处理超时时间，单位秒。 |
| `enableIntentRecognition` | `boolean` | `false` | 是否启用意图识别。 |
| `intentLlmId` | `String` | `""` | 意图识别时使用的模型 ID。 |
| `intentConfidenceThreshold` | `double` | `0.7` | 意图识别置信度阈值。 |
| `intentTypeList` | `List<String>` | `create/pause/resume/cancel/unknown` | 暴露给 `IntentRecognizer` 的工具集合。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `defaultConfig()` | `ControllerConfig` | 返回一个新的默认配置实例。 |
| `setScheduleInterval(double scheduleInterval)` | `void` | 要求 `>= 0.1`，否则抛出 `IllegalArgumentException`。 |
| `setTaskTimeout(Double taskTimeout)` | `void` | 仅允许 `null` 或 `>= 600` 的超时值。 |
| `setEventQueueSize(int eventQueueSize)` | `void` | 要求 `>= 1`。 |
| `setEventTimeout(double eventTimeout)` | `void` | 要求 `>= 100`。 |
| `setIntentConfidenceThreshold(double intentConfidenceThreshold)` | `void` | 要求位于 `0.0` 到 `1.0` 之间。 |

## 说明

- `intentTypeList` 默认只开放 `create_task`、`pause_task`、`resume_task`、`cancel_task` 和 `unknown_task` 五类工具；如果希望启用继续、补充或修改任务，需要显式扩展该列表。
- `TaskScheduler.scheduleLoop()` 当前按 `runningTasks.size() >= maxConcurrentTasks` 判断并发上限，没有对 `0` 做特殊处理；如果要使用“无限并发”语义，需要先验证运行时实现。
