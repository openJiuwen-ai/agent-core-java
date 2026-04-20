# 团队装配与运行

本页聚焦怎样把多个 Agent 封装成一个 group，并把它作为一个整体运行。当前可验证的团队装配路径可以拆成公共 API、执行入口和示例层三个部分来讲。

## 功能定位

本页回答三个问题：

1. Java 里怎样把多个 Agent 组织成一个团队边界。
2. 这个团队怎样被直接调用，或注册为 `Runner` 可解析的 group 资源。
3. 什么时候该用新版 `BaseGroup`，什么时候只能退回 legacy `ControllerGroup` / example 路径。

## 当前团队装配分层

| 分层 | Java 当前入口 | 负责什么 | 当前定位 |
| --- | --- | --- | --- |
| 团队公共 API | `BaseGroup + GroupCard + GroupConfig` | 团队身份、成员注册、运行阈值、`invoke/stream` 入口 | 推荐主线 |
| 团队会话 | `Session`、`MultiAgentSessions`、`AgentGroupSessionApi` | 会话 ID、状态、环境变量、流式输出 | 推荐主线 |
| 团队执行注册 | `Runner.resourceMgr().addAgentGroup(...)`、`Runner.runAgentGroup(...)` | 把 group 作为运行资源注册与调用 | 可选入口 |
| 路由与协作示例 | `ControllerGroup`、`BaseGroupController`、`examples/groups/hierarchical_group` | 点对点、订阅广播、leader-worker 路由 | 兼容层 / 示例层 |

## 推荐写法：先把团队建成 `BaseGroup`

如果你要写 Java 新文档或新能力，推荐从 `BaseGroup` 开始：

1. 用 `GroupCard` 定义团队身份和成员卡片集合。
2. 用 `GroupConfig` 定义成员上限、并发和超时。
3. 继承 `BaseGroup`，在子类里注册成员并实现自己的路由逻辑。
4. 用 `Session` / `MultiAgentSessions` 组织一次团队执行。

下面是一个最小团队骨架：

```java
final class ReviewGroup extends BaseGroup {

    ReviewGroup(GroupCard card, GroupConfig config, BaseAgent planner, BaseAgent reviewer) {
        super(card, config);
        addAgent(planner, "planner");
        addAgent(reviewer, "reviewer");
    }

    @Override
    public Object invoke(Object message, AgentGroupSessionApi session) {
        return Map.of(
                "group", getGroupId(),
                "members", listAgents(),
                "message", message
        );
    }

    @Override
    public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        return List.of(invoke(message, session)).iterator();
    }
}
```

这条路径的核心优点是边界清晰：团队是谁、有哪些成员、一次团队执行的会话上下文是什么，都落在公开 API 上，而不是藏在 example bridge 里。

## 团队怎么运行

### 方式 1：直接用 group 实例运行

如果调用方已经拿到了 group 实例，最直接的写法是：

```java
Session session = MultiAgentSessions.createAgentGroupSession(
        "review-session-001",
        Map.of("locale", "zh-CN")
);

Object result = Runner.runAgentGroup(group, Map.of("task", "复核回答"), session, null);
```

这种方式最适合本地装配和单进程调用。

### 方式 2：先注册 group，再按资源 ID 调用

如果你希望把团队也放进 `Runner` 的共享资源空间，可以额外注册：

```java
Runner.resourceMgr().addAgentGroup(group.getCard(), () -> group, null);

Object result = Runner.runAgentGroup(
        group.getCard().getId(),
        Map.of("task", "复核回答"),
        session,
        null
);
```

这里有一个值得显式写出来的细节：

- `BaseGroup` 内部的 `groupId` 来自 `GroupCard.name`
- `ResourceMgr.addAgentGroup(...)` 校验的是 `GroupCard.id`

如果你既要把 group 当作团队边界使用，又要把它注册进资源管理器，最稳妥的做法是让 `id` 和 `name` 保持一致，避免运行时一边按 `name` 理解团队，一边按 `id` 注册资源而造成混淆。

## 什么时候需要 `ControllerGroup`

`ControllerGroup` 和 `BaseGroupController` 仍然有价值，但定位不同：

- 当你要解释当前仓库里已经存在的 controller 驱动协作示例时，需要它们。
- 当你需要 `sendToAgent(...)`、`subscribe(...)`、`publish(...)` 这类 legacy 路由语义时，需要它们。
- 当你只是想写新的 Java 多智能体能力，不建议先从这条路径起步。

尤其要注意：

- `ControllerGroup` 是 `@Deprecated` 的兼容层。
- `BaseGroupController` 的优势在于消息队列、点对点转发和广播，而不是新的团队公共 API。
- `examples/groups/hierarchical_group` 之所以仍然使用这套路径，是因为它要在 example 层演示 leader-worker 路由与中断恢复，而不是声明 Java 已经有一个正式的 `HierarchicalGroup` 框架类。

## Java 当前最接近 “团队协作” 的现实入口

如果按“现在仓库里能稳定落地的东西”排序，大致可以这样理解：

1. 需要公共 API 和清晰边界，用 `BaseGroup`
2. 需要把整个团队作为资源暴露给调用方，用 `Runner.resourceMgr().addAgentGroup(...)`
3. 需要 legacy 路由语义或阅读现有示例，用 `ControllerGroup` / `BaseGroupController`
4. 需要 leader-worker 对照示例，看 `examples/groups/hierarchical_group`

Java 当前更偏“公共 API + 运行资源 + 示例映射”三层拆开。

## 当前能力边界

- Java 当前没有额外的高层团队工厂；主线是 `BaseGroup`、`Session` 和 `Runner`。
- `examples/groups` 目前只冻结了 `hierarchical_group` 这一个 leader-worker 示例。
- `ControllerGroup` / `BaseGroupController` 能表达团队协作，但它们属于 legacy 兼容层，不应被写成新的推荐主线。
- 如果后续要把 example 层 leader-worker 能力提升为正式框架能力，优先需要收敛 group/controller/event/session 的公共接口，而不是继续堆更多示例桥接代码。

## 示例入口

- [示例：Groups Java Examples](../../../../examples/groups/README.md)
- [示例：hierarchical_group](../../../../examples/groups/hierarchical_group/README.md)

## 参考入口

- [API 文档：BaseGroup](../API文档/com.openjiuwen.core/multiagent/BaseGroup.md)
- [API 文档：GroupConfig](../API文档/com.openjiuwen.core/multiagent/GroupConfig.md)
- [API 文档：GroupCard](../API文档/com.openjiuwen.core/multiagent/schema/GroupCard.md)
- [API 文档：MultiAgentSessions](../API文档/com.openjiuwen.core/multiagent/MultiAgentSessions.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)
- [API 文档：ResourceMgr](../API文档/com.openjiuwen.core/runner/resourcemanager/ResourceMgr.md)
- [API 文档：ControllerGroup](../API文档/com.openjiuwen.core/multiagent/legacy/ControllerGroup.md)
- [API 文档：BaseGroupController](../API文档/com.openjiuwen.core/multiagent/legacy/BaseGroupController.md)

## 本页说明

- 主题聚焦“如何组织团队、如何运行团队”的 Java 当前主线路径。
- 推荐路径落在 `BaseGroup + Session + Runner`。
- 对 controller 驱动协作只做兼容层说明，把正式公共 API 和 example bridge 清楚分开。
