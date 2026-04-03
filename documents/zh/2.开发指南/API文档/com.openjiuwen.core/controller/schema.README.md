# schema

`com.openjiuwen.core.controller.schema` 定义控制器在输入、意图识别、任务跟踪和输出流式返回过程中使用的核心数据结构。

## Types

| 类型 | 说明 |
|---|---|
| [`ControllerOutput`](./schema/ControllerOutput.md) | 控制器批量调用返回的聚合结果。 |
| [`ControllerOutputChunk`](./schema/ControllerOutputChunk.md) | 会话流中的单个输出块。 |
| [`ControllerOutputPayload`](./schema/ControllerOutputPayload.md) | 输出块中的业务载荷，包含类型、数据和元数据。 |
| [`DataFrame`](./schema/DataFrame.md) | 控制器输入输出统一使用的数据帧接口。 |
| [`Event`](./schema/Event.md) | 控制器事件基类。 |
| [`EventType`](./schema/EventType.md) | 输入、交互、完成和失败事件类型枚举。 |
| [`InputEvent`](./schema/InputEvent.md) | 控制器的主输入事件，承载 `DataFrame` 列表。 |
| [`Intent`](./schema/Intent.md) | 意图识别后的结构化任务操作对象。 |
| [`IntentType`](./schema/IntentType.md) | 控制器支持的意图类型枚举。 |
| [`Task`](./schema/Task.md) | 任务模型，记录输入、输出、状态和父子关系。 |
| [`TaskCompletionEvent`](./schema/TaskCompletionEvent.md) | 任务完成事件。 |
| [`TaskFailedEvent`](./schema/TaskFailedEvent.md) | 任务失败事件。 |
| [`TaskInteractionEvent`](./schema/TaskInteractionEvent.md) | 任务请求用户交互时产生的事件。 |
| [`TaskStatus`](./schema/TaskStatus.md) | 任务状态枚举。 |

## Notes

- `DataFrame`、`InputEvent`、`Intent` 和 `Task` 构成了控制器从用户输入到任务调度的主数据链路。
- `ControllerOutputPayload`、`ControllerOutputChunk` 与完成/失败/交互事件一起构成了控制器的输出协议。
