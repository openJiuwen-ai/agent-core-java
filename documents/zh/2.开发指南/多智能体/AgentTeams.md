# 团队装配与运行

本页聚焦 Java 多智能体里的团队装配与运行方式。当前主线不是额外的团队工厂，而是用 `BaseGroup` 把多个 `BaseAgent` 封装成一个 group，再结合 `Session`、`MultiAgentSessions` 和 `Runner` 完成一次团队执行。

## 功能定位

本页回答三个问题：

1. Java 里怎样把多个 Agent 组织成一个清晰的团队边界。
2. 这个团队怎样被直接调用，或注册为 `Runner` 可解析的 group 资源。
3. 什么时候应该使用 `BaseGroup` 主线，什么时候应该进入 controller 或示例层协作路径。

## 核心概念

| 对象 | 作用 | 你需要关心什么 |
| --- | --- | --- |
| `BaseGroup` | 团队边界与执行入口 | 在子类里注册成员，并实现 `invoke/stream` |
| `BaseAgent` | 团队成员 | 负责真正的业务处理能力 |
| `GroupCard` | 团队身份卡片 | 定义 `id`、`name`、`description`、`agentCards` |
| `GroupConfig` | 团队运行参数 | 控制成员上限、并发消息数和超时时间 |
| `Session`、`MultiAgentSessions`、`Runner` | 团队会话与调用入口 | 创建团队会话，并执行或注册 group |

### 团队角色

Java 当前公共 API 并没有额外定义固定的团队角色类型，角色分工通常由你的团队装配方式决定：

- 如果你走 `BaseGroup` 主线，谁负责接收请求、谁负责路由、谁负责执行业务，都由 `invoke/stream` 和成员注册方式决定。
- 如果你走 `ControllerGroup` 路径，`BaseGroupController` 更像团队调度中枢，负责消息转发和协作组织。
- 如果你阅读 `hierarchical_group` 示例，`HierarchicalLeaderAgent` 扮演 leader，业务 worker 负责实际处理任务。

### 团队运行生命周期

一次典型的团队执行，通常会经历下面几个阶段：

1. 创建 `GroupCard` 和 `GroupConfig`。
2. 继承 `BaseGroup` 并注册成员 Agent。
3. 创建 `Session` 或通过 `MultiAgentSessions` 生成团队会话。
4. 调用 `Runner.runAgentGroup(...)` 触发 `invoke/stream`。
5. 在会话中保存状态、环境变量和流式输出，直到本轮执行结束。

这里最重要的心智模型是：

- `BaseGroup` 负责把多个 Agent 收拢成一个整体。
- `GroupCard` 说明这个整体是谁。
- `GroupConfig` 说明这个整体怎么跑。
- `Session` 负责承载一次团队执行的状态、环境变量和流式输出上下文。
- `Runner` 负责真正触发执行，并决定是直接运行实例，还是按资源 ID 解析 group。

## 快速开始

下面的最小示例只聚焦团队装配。示例默认 `planner` 和 `reviewer` 已经是上层创建好的 `BaseAgent` 实例。

```java
import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.multiagent.GroupConfig;
import com.openjiuwen.core.multiagent.MultiAgentSessions;
import com.openjiuwen.core.multiagent.Session;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

```java
GroupCard card = GroupCard.builder()
        .id("review_group")
        .name("review_group")
        .description("负责规划与复核的协作组")
        .build();

GroupConfig config = new GroupConfig()
        .configureMaxAgents(2)
        .configureTimeout(20.0)
        .configureConcurrency(8);

ReviewGroup group = new ReviewGroup(card, config, planner, reviewer);

Session session = MultiAgentSessions.createAgentGroupSession(
        "review-session-001",
        Map.of("locale", "zh-CN")
);

Object result = Runner.runAgentGroup(
        group,
        Map.of("task", "复核回答"),
        session,
        null
);
```

这段示例体现的是 Java 当前团队主线：

- 先定义团队身份。
- 再定义运行阈值。
- 再把成员注册进 `BaseGroup` 子类。
- 最后用团队会话和 `Runner` 触发执行。

`invoke(...)` 和 `stream(...)` 就是你编排团队协作逻辑的地方。最小示例里只是把成员列表和输入原样返回；在真实项目中，这里通常会接入成员路由、上下文传递和结果聚合。

## 配置与运行方式

### 团队身份与运行阈值

`GroupCard` 负责团队身份，`GroupConfig` 负责运行参数。两者关注点不同：

- `GroupCard` 继承 `BaseCard`，主要承载 `id`、`name`、`description`。
- `GroupCard` 还额外维护 `agentCards`、`topic`、`version` 和 `tags`。
- `GroupConfig` 默认提供 `maxAgents = 10`、`maxConcurrentMessages = 100`、`messageTimeout = 30.0`。
- `BaseGroup.addAgent(...)` 注册成员后，会自动把成员的 `AgentCard` 追加到 `card.getAgentCards()`。
- `Session` 是 `com.openjiuwen.core.multiagent` 包下的会话别名，底层仍然继承 `AgentGroupSessionApi`。

有一个需要显式记住的细节：

- `BaseGroup` 内部的 `groupId` 来自 `GroupCard.name`。
- `Runner.resourceMgr().addAgentGroup(...)` 注册资源时校验的是 `GroupCard.id`。

因此，如果这个团队既要作为 group 实例直接运行，又要注册到资源管理器中，建议把 `id` 和 `name` 设成同一个值，减少运行时歧义。

### 方式 1：直接运行 group 实例

如果调用方已经拿到了 group 实例，最直接的写法是：

```java
Session session = MultiAgentSessions.createAgentGroupSession(
        "review-session-001",
        Map.of("locale", "zh-CN")
);

Object result = Runner.runAgentGroup(
        group,
        Map.of("task", "复核回答"),
        session,
        null
);
```

这种方式适合本地装配、单模块调用，以及你已经明确持有团队实例的场景。

### 方式 2：注册为资源后按 ID 调用

如果你希望把团队放进 `Runner` 的共享资源空间，供其他模块按资源 ID 获取和执行，可以先注册：

```java
Runner.resourceMgr().addAgentGroup(group.getCard(), () -> group, null);

Object result = Runner.runAgentGroup(
        group.getCard().getId(),
        Map.of("task", "复核回答"),
        session,
        null
);
```

这种方式适合把团队作为公共资源暴露给外部调用方。文档层面的推荐顺序是：

1. 先把团队对象设计清楚。
2. 再决定它是否需要注册到资源管理器。

## 协作入口与适用场景

### `BaseGroup`：正式团队封装主线

当你要做新的 Java 团队能力时，优先使用 `BaseGroup`：

- 团队边界清晰，成员和配置都挂在公开 API 上。
- `invoke/stream` 是稳定的团队执行入口。
- 会话、状态和环境变量由 `Session` 系列对象承载。

如果你的目标是“把多个 Agent 封装成一个正式可调用的团队对象”，这里是默认入口。

### `ControllerGroup` 与 `BaseGroupController`：controller 驱动协作

当你需要更明显的消息路由语义时，可以进入 legacy controller 路径：

- `ControllerGroup` 把 group 的 `invoke/stream` 委托给 `BaseGroupController`。
- `BaseGroupController` 提供 `sendToAgent(...)`、`publish(...)`、`subscribe(...)` 和 `unsubscribe(...)`。
- 这条路径更适合点对点转发、订阅广播和 controller 统一调度。

它的重点是消息路由能力，而不是新的团队公共 API。

### `examples/groups/hierarchical_group`：leader-worker 示例入口

如果你要看一个更接近团队协作的完整样例，当前最直接的入口是 `examples/groups/hierarchical_group`。

这个示例里有三层关键角色：

- `HierarchicalGroupController` 负责 group 级路由，顺序是显式 `receiver_id`、订阅者、默认 leader。
- `HierarchicalLeaderAgent` 负责把请求分发给具体 worker。
- worker 负责实际业务执行，并在需要补充信息时通过会话状态等待用户回复。

这个示例展示的是 leader-worker 协作路径，但它仍然停留在 example 层，不是新的正式团队框架类型。

## 暂未实现的能力

Java 当前多智能体主线已经可以完成团队封装、团队会话和运行入口，但下面这些更高层团队能力暂未实现：

- 统一的团队工厂接口，用一份声明式配置直接创建完整团队。
- 内建的 leader / teammate 生命周期管理框架。
- 团队级持久生命周期模式与恢复接口。
- 预定义团队成员的声明式注册机制。
- 队友执行模式切换一类的现成团队策略开关。
- 内建健康检查和自动恢复机制。
- 独立的团队传输层与存储层配置对象。

因此，当前更稳妥的写法是把团队装配建立在 `BaseGroup + Session + Runner` 上；如果需要更复杂的协作行为，再结合 controller 或示例路径扩展。

## 示例入口

- [示例：Groups Java Examples](../../../../examples/groups/README.md)
- [示例：hierarchical_group](../../../../examples/groups/hierarchical_group/README.md)

## 参考入口

- [API 文档：BaseGroup](../API文档/com.openjiuwen.core/multiagent/BaseGroup.md)
- [API 文档：GroupConfig](../API文档/com.openjiuwen.core/multiagent/GroupConfig.md)
- [API 文档：GroupCard](../API文档/com.openjiuwen.core/multiagent/schema/GroupCard.md)
- [API 文档：MultiAgentSessions](../API文档/com.openjiuwen.core/multiagent/MultiAgentSessions.md)
- [API 文档：Session](../API文档/com.openjiuwen.core/multiagent/Session.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)
- [API 文档：ResourceMgr](../API文档/com.openjiuwen.core/runner/resourcemanager/ResourceMgr.md)
- [API 文档：ControllerGroup](../API文档/com.openjiuwen.core/multiagent/legacy/ControllerGroup.md)
- [API 文档：BaseGroupController](../API文档/com.openjiuwen.core/multiagent/legacy/BaseGroupController.md)

## 本页说明

- 本页只写 Java 当前仓库里已经存在的团队装配、会话和执行入口。
- 推荐主线落在 `BaseGroup + Session + Runner`。
- controller 路径和 leader-worker 示例只作为协作入口说明，不替代正式公共 API。
