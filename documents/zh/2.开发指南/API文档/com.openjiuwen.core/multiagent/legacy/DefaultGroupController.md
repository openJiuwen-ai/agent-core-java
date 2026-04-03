# com.openjiuwen.core.multiagent.legacy.DefaultGroupController

## class DefaultGroupController

```java
@Deprecated
public class DefaultGroupController extends BaseGroupController
```

`DefaultGroupController` 提供 legacy 分组的默认路由策略：指定 `receiverId` 时点对点转发，未指定时按订阅关系广播。

## 构造方法

### `public DefaultGroupController(LegacyBaseGroup agentGroup)`

创建并立即绑定指定分组。

### `public DefaultGroupController()`

创建稍后再绑定分组的默认控制器。

## 路由逻辑

### `protected Object handleEvent(GroupEvent event, AgentGroupSessionApi session)`

执行默认路由逻辑：

- `event.getReceiverId()` 非空时，直接调用 `sendToAgent(...)`。
- 否则根据 `event.getCustomEventType()` 触发 `publish(...)`。
- 广播结果只有一个订阅者时返回单个对象，多个订阅者时返回结果列表。

## 说明

- 该类没有新增状态，完全复用 `BaseGroupController` 的消息队列和订阅管理能力。
- 这是 legacy 兼容实现，推荐新代码改用新版 `BaseGroup` 体系自行组织路由。
