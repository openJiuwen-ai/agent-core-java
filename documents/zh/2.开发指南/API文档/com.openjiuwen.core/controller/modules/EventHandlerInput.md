# com.openjiuwen.core.controller.modules.EventHandlerInput

## class EventHandlerInput

```java
public class EventHandlerInput
```

`EventHandlerInput` 是传给事件处理器的不可变入参对象，封装了当前事件和所属会话。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | `Event` | 当前待处理的控制器事件。 |
| `session` | `AgentSessionApi` | 当前会话对象，用于读写流、状态和 `sessionId`。 |

## 构造方法

### `public EventHandlerInput(Event event, AgentSessionApi session)`

把事件对象与会话对象组合为单个处理器入参。

## 说明

- 该类型只有 getter，没有 setter；一旦构造完成，事件和会话引用不会再被修改。
- `EventQueue.publishEvent()` 在同步分发事件前会统一构造该对象并传给处理器。
