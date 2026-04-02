# com.openjiuwen.core.controller.modules.EventQueue

## class EventQueue

```java
public class EventQueue
```

`EventQueue` 是控制器内部的同步事件分发器。它按 `{agentId}_{sessionId}_{eventType}` 生成 topic，把不同事件类型路由到 `EventHandler` 的四个入口方法。

## 核心状态

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 事件队列配置。 |
| `eventHandler` | `EventHandler` | 当前控制器绑定的事件处理器。 |
| `subscriptions` | `Map<String, TopicSubscription>` | topic 到订阅对象的映射。 |
| `running` | `AtomicBoolean` | 当前队列是否处于运行状态。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setEventHandler(EventHandler eventHandler)` | `void` | 绑定事件处理器。 |
| `start()` | `void` | 标记事件队列为运行状态。 |
| `stop()` | `void` | 停止队列并清空所有 topic 订阅。 |
| `subscribe(String agentId, String sessionId)` | `void` | 为同一个 agent/session 对订阅 `INPUT`、`TASK_INTERACTION`、`TASK_COMPLETION`、`TASK_FAILED` 四类事件。 |
| `unsubscribe(String agentId, String sessionId)` | `void` | 取消上述四类事件订阅。 |
| `publishEvent(String agentId, AgentSessionApi session, Event event)` | `void` | 构造 `EventHandlerInput` 并同步调用目标处理器。 |
| `unsubscribeAll()` | `void` | 停止队列，相当于清空全部订阅。 |

## 说明

- 事件分发是同步完成的；`publishEvent()` 会一直阻塞到处理器执行结束，以保证事件顺序。
- 如果没有找到 topic 对应的订阅，事件会被丢弃并记录 warning，而不是排队等待。
- 处理器抛出的普通异常会被包装成 `AGENT_CONTROLLER_EVENT_HANDLER_ERROR`；订阅失败则包装成 `AGENT_CONTROLLER_EVENT_QUEUE_ERROR`。
