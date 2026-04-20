# 组运行时职责与通信协作

这里重点说明 Java 多智能体里的运行时职责分配：成员管理、会话状态、消息路由、共享资源以及组内通信协作。当前这些能力分别落在分组、会话、controller 和资源管理器上。

## 功能定位

先看两件事：

1. 成员管理、消息路由、发布订阅、生命周期在 Java 里分别落到哪里。
2. 组内 Agent 的发送、发布和恢复协作当前怎样实现。

## 能力映射

| 运行时职责 | Java 当前入口 | 真实职责 |
| --- | --- | --- |
| 成员注册 | `BaseGroup` | 维护成员映射、暴露 `addAgent/removeAgent/listAgents` |
| 分组会话与上下文 | `Session`、`MultiAgentSessions`、`AgentGroupSessionApi` | 保存会话 ID、环境变量、状态和流式输出能力 |
| 路由 / 广播 | `ControllerGroup` + `BaseGroupController` | 处理 `GroupEvent`、订阅关系、点对点和广播分发 |
| 共享资源 | `Runner.resourceMgr()` | 共享注册 workflow、tool、model、sysop 等资源 |
| 可运行协作样例 | `examples/groups/hierarchical_group` | 展示 controller 驱动的 leader-worker 协作 |

## Java 里如何承担这些职责

### 1. 成员管理由 `BaseGroup` 承担

新版 `BaseGroup` 已经负责：

- 按注册顺序维护 `Map<String, BaseAgent>`
- 校验重复成员 ID 和 `maxAgents` 上限
- 同步更新 `GroupCard.getAgentCards()`
- 在可能的情况下自动把 group 引用注入成员 controller

也就是说，Java 当前把“团队里有哪些成员”这部分职责收敛在分组对象本身，而不是再单独拆出一层运行时类型。

### 2. 会话、状态和流式输出由 `Session` 系列承担

`MultiAgentSessions.createAgentGroupSession(...)` 和 `Session.create(...)` 提供的是分组会话入口。它们继承的 `AgentGroupSessionApi` 会继续沿用单 Agent session 的状态、环境变量和流式输出辅助能力。

这意味着 Java 当前不是“runtime 持有 session”，而是“group 执行时显式传入 session”：

```java
Session session = MultiAgentSessions.createAgentGroupSession(
        "conversation-001",
        Map.of("tenant", "demo")
);

Object result = group.invoke(message, session);
```

### 3. 点对点和广播主要由 legacy controller 承担

如果你需要显式的消息路由 / 广播语义，当前最贴近仓库事实的入口是 `BaseGroupController`：

- `invoke(...)` 会懒启动组内 `MessageQueueInMemory`，把 `GroupEvent` 和 session 放进队列再等待结果。
- `subscribe(...)` / `unsubscribe(...)` 维护“消息类型 -> 订阅成员”关系。
- `sendToAgent(...)` 会把事件转换为目标 Agent 输入，转发流式输出，并在有交互中断时保留完整 chunk 列表。
- `publish(...)` 会按 `customEventType` 找到订阅者，并用虚拟线程并发调用所有目标成员。

`ControllerGroup` 的职责则更偏封装层：它负责把外部传入的 `GroupEvent`、`Map` 或 `String` 规范化，再把真正的路由逻辑委托给 `BaseGroupController`。

### 4. 共享资源不等于组内消息运行时

`Runner.resourceMgr()` 很重要，但它承担的是共享资源注册职责。它更像 Java 里的共享资源注册中心：

- 注册和查询 workflow
- 注册和查询 tool
- 注册 sysop、MCP tool 等跨 Agent 复用资源

如果 group 内成员依赖相同的工具、workflow 或远端能力，通常要通过 `Runner.resourceMgr()` 暴露这些共享资源；但它本身不是组内 P2P / Pub-Sub 的消息运行时。

## 如何理解组内通信能力

Java 当前没有一个公开的 mixin 或基类，让任意 `BaseAgent` 直接获得 `send()`、`publish()`、`subscribe()` 这一组方法。因此更准确的说法是：

- Java 里的成员通常仍然是普通 `BaseAgent`。
- 组内谁接收消息、谁负责广播、谁负责恢复上一次中断，往往由 `BaseGroupController` 或示例内的 leader/controller 决定。
- `examples/groups/hierarchical_group` 里的 `HierarchicalGroupController` 和 `HierarchicalLeaderAgent` 展示了这种写法：leader 负责路由决策，controller 负责把消息送到合适 worker，并在用户回复后继续恢复到上一次中断的成员。

这里说的“组内通信能力”不是一个单独类型，而是一组由 controller、session 和成员协同完成的职责分配。

## 当前能力状态

- Java 当前没有单独拆分的通信 mixin 或独立组运行时类型；公开主线是 `BaseGroup + Session`。
- `ControllerGroup` 和 `BaseGroupController` 都属于带 `@Deprecated` 的兼容路径，主要用于历史兼容和 example 映射。
- `examples/groups/hierarchical_group` 里的 leader 使用示例内的确定性关键词路由，不是完整的 LLM intent detection。
- 如果你在写新能力，优先沿着 `BaseGroup + GroupCard + GroupConfig + Session` 主线扩展；如果你在解释现有协作示例，再补充 controller 路由层即可。

## 示例入口

- [示例：Groups Java Examples](../../../../examples/groups/README.md)
- [示例：hierarchical_group](../../../../examples/groups/hierarchical_group/README.md)

## 参考入口

- [API 文档：multiagent 模块总览](../API文档/com.openjiuwen.core/multiagent.README.md)
- [API 文档：Session](../API文档/com.openjiuwen.core/multiagent/Session.md)
- [API 文档：MultiAgentSessions](../API文档/com.openjiuwen.core/multiagent/MultiAgentSessions.md)
- [API 文档：BaseGroupController](../API文档/com.openjiuwen.core/multiagent/legacy/BaseGroupController.md)
- [API 文档：ControllerGroup](../API文档/com.openjiuwen.core/multiagent/legacy/ControllerGroup.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)

## 使用边界

- 这里聚焦运行时职责和组内通信协作在 Java 中的真实落点。
- 职责拆分给 `BaseGroup`、`Session`、legacy controller 和 `Runner.resourceMgr()`。
- 文中所有说法都能回到当前仓库的源码、API 文档或 `examples/groups` 中找到对应依据。
