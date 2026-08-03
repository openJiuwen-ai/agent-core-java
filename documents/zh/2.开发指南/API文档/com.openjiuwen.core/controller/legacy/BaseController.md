# com.openjiuwen.harness.tools.browser_move.controllers.BaseController

## abstract class BaseController

```java
public abstract class BaseController
```

`BaseController` 是旧版控制器体系的抽象基类，使用 `MessageQueueInMemory` 为每个 `conversationId` 建立一个内存 topic，并把输入事件转成队列消息再回调到 `handleEvent()`。

## 核心状态

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `Object` | 旧版控制器配置对象。 |
| `contextEngine` | `ContextEngine` | 上下文引擎引用。 |
| `msgQueue` | `MessageQueueInMemory` | 内存消息队列。 |
| `subscriptions` | `Map<String, SubscriptionBase>` | `conversationId` 到订阅对象的映射。 |
| `group` | `Object` | 所属 group 引用，用于点对点发送和广播。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setupFromAgent(Object agent)` | `void` | 通过 getter 或反射字段从 agent 中提取配置和 `ContextEngine`。 |
| `invoke(Map<String, Object> inputs, Session session)` | `Map<String, Object>` | 为当前 `conversationId` 建立订阅、构造 `InvokeQueueMessage` 并同步等待处理结果。 |
| `createMessage(Map<String, Object> inputs)` | `Event` | 把输入转换成 `Event.createUserEvent(...)`。 |
| `cleanupConversation(String conversationId)` | `void` | 取消订阅并回收指定会话的 topic。 |
| `stop()` | `void` | 停止所有 legacy 订阅并关闭消息队列。 |
| `setGroup(Object group)` | `void` | 注入 group 引用。 |
| `sendToAgent(String agentId, Event event, Session session)` | `Object` | 委托 group controller 做点对点路由。 |
| `publish(Event event, Session session)` | `List<Object>` | 委托 group controller 做广播路由。 |

## 说明

- `handleEvent(Event event, Session session)` 是受保护的抽象扩展点，由子类实现真正的事件处理逻辑。
- topic 名称固定为 `controller_messages_{conversationId}`。
- `invoke()` 会同步等待消息队列 handler 的返回值；如果返回的不是 `Map`，会包装成 `{"output": result}`。
- `sendToAgent()` 和 `publish()` 依赖外部 group controller；若当前控制器不在 group 中，会抛出运行时异常。
