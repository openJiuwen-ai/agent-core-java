# com.openjiuwen.core.multi_agent.legacy.BaseGroupController

## abstract class BaseGroupController

```java
@Deprecated
public abstract class BaseGroupController
```

`BaseGroupController` 为 legacy `ControllerGroup` 提供消息队列、订阅关系和组内消息转发能力；子类通过覆盖受保护扩展点来决定实际路由逻辑。

## 实例化说明

### `protected BaseGroupController(LegacyBaseGroup agentGroup)`

供子类在创建时直接绑定一个 `LegacyBaseGroup`。

### `protected BaseGroupController()`

供子类延后绑定分组实例，后续可通过 `setupFromGroup(...)` 注入。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setupFromGroup(LegacyBaseGroup group)` | `void` | 注入 `ControllerGroup` 实例，供后续路由和日志使用。 |
| `invoke(GroupEvent event, AgentGroupSessionApi session)` | `Object` | 惰性启动内存消息队列后发布事件并等待结果。 |
| `subscribe(String messageType, List<String> agentIds)` | `void` | 为指定消息类型登记订阅 Agent。 |
| `unsubscribe(String messageType, List<String> agentIds)` | `void` | 取消指定消息类型的订阅。 |
| `getSubscribers(String messageType)` | `List<String>` | 返回当前消息类型的订阅者列表。 |
| `sendToAgent(GroupEvent event, String agentId, AgentGroupSessionApi session)` | `Object` | 以点对点方式流式调用指定 Agent，并把输出透传到分组会话流。 |
| `publish(GroupEvent event, AgentGroupSessionApi session)` | `List<Object>` | 按 `customEventType` 查找订阅者并并发广播。 |
| `stop()` | `void` | 停止消息队列并重置内部启动标志。 |
| `getAgentGroup()` | `LegacyBaseGroup` | 返回当前绑定的分组。 |
| `getSubscriptionsMap()` | `Map<String, List<String>>` | 返回内部订阅映射。 |

## 扩展点

### `protected Object handleEvent(GroupEvent event, AgentGroupSessionApi session)`

由子类实现具体路由逻辑。典型做法是根据 `receiverId` 执行点对点转发，或根据 `customEventType` 调用 `publish(...)` 做广播。

## 说明

- 内部消息队列主题名固定为 `group_messages_<groupId>`，并在首次 `invoke(...)` 时惰性启动。
- `sendToAgent(...)` 会创建子 `AgentSessionApi`，拷贝父会话状态，逐块转发 `agent.stream(...)` 的输出，并在结束后写回最新状态。
- 如果流式结果中存在 `OutputSchema` 且其 `type == INTERACTION`，`sendToAgent(...)` 会返回完整块列表而不是最后一个结果。
- `ControllerGroupTranslationTest` 验证了交互输入对象会原样透传到子 Agent，并且流式块会继续向外层调用者转发。
