# com.openjiuwen.core.multi_agent.legacy.ControllerGroup

## class ControllerGroup

```java
@Deprecated
public class ControllerGroup extends LegacyBaseGroup
```

`ControllerGroup` 是 legacy 分组实现，负责把 `invoke(...)` / `stream(...)` 的实际路由逻辑委托给 `BaseGroupController`。

## 构造方法

### `public ControllerGroup(AgentGroupConfig config, BaseGroupController groupController)`

创建控制器驱动的分组；若传入 `groupController`，构造期间会自动调用 `setupFromGroup(this)`。

### `public ControllerGroup(AgentGroupConfig config)`

创建尚未绑定控制器的分组；后续若直接执行将抛出异常。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 把 `GroupEvent` / `Map` / `String` 输入归一化为 `GroupEvent`，再委托给 `groupController.invoke(...)`。 |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 在虚拟线程中运行控制器，并通过 `session` 的流写入器向调用者暴露流式迭代器。 |
| `getGroupController()` | `BaseGroupController` | 返回构造时绑定的控制器。 |

## 说明

- 当 `message` 是 `Map` 时，会通过 `GroupEvent.fromMap(...)` 做兼容转换；当 `message` 是 `String` 时，会生成默认会话 ID 为 `default_session` 的用户事件。
- 如果调用方未提供 `session`，该类会依据 `event.getConversationId()` 自动创建一个 `AgentGroupSessionApi`。
- `ControllerGroupTranslationTest` 验证了 `stream(...)` 可以把内部 Agent 产生的 `OutputSchema` 块持续转发给外层调用者。
