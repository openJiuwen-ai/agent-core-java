# BaseGroup 与组封装

本页聚焦 `com.openjiuwen.core.multiagent.BaseGroup` 及其配套的 `GroupCard`、`GroupConfig` 和 group session 能力。

## 功能定位

本页聚焦“怎样把多个 Agent 封装成一个可调用的 group”。如果你只想先弄清 Java 多智能体整体长什么样，先回到 [概述](概述.md)；如果你关心运行时职责和消息分发，再读 [组运行时职责与通信协作](TeamRuntime与CommunicableAgent.md)。

## 核心类型

| 类型 | 作用 | 典型用法 |
| --- | --- | --- |
| `BaseGroup` | 分组抽象基类 | 注册成员、实现 `invoke/stream` |
| `GroupCard` | 分组身份卡片 | 写 `id`、`name`、`description`、`agentCards` |
| `GroupConfig` | 运行参数 | 调整成员上限、并发与超时 |
| `AgentGroupSessionApi` / `Session` | 分组会话对象 | 传状态、环境变量、流式输出 |
| `BaseAgent` | group 内成员的共同基类 | 作为 `addAgent(...)` 的输入 |

## 搭建步骤

### 1. 先定义分组身份

`GroupCard` 负责描述这个 group 是谁。它继承 `BaseCard` 的 `id`、`name`、`description`，并额外维护 `agentCards`、`topic`、`version` 和 `tags`。

```java
GroupCard card = GroupCard.builder()
        .id("planning_group")
        .name("planning_group")
        .description("负责规划与复核的协作组")
        .topic("planning")
        .version("1.0.0")
        .build();
```

一个容易忽略的细节是：`BaseGroup` 构造时会把 `card.getName()` 固化为 `groupId`。如果你的 `id` 和 `name` 语义不同，真正参与 group 标识的是 `name`。

### 2. 再准备运行配置

`GroupConfig` 只管运行阈值，不管成员身份。它默认提供：

- `maxAgents = 10`
- `maxConcurrentMessages = 100`
- `messageTimeout = 30.0`

你可以直接用链式方法覆盖默认值：

```java
GroupConfig config = new GroupConfig()
        .configureMaxAgents(4)
        .configureTimeout(20.0)
        .configureConcurrency(16);
```

### 3. 继承 `BaseGroup` 并注册成员

`BaseGroup` 提供的是团队封装骨架：你需要自己在子类里组织成员注册和路由逻辑。

```java
import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.multiagent.GroupConfig;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class PlanningGroup extends BaseGroup {

    PlanningGroup(GroupCard card, GroupConfig config, BaseAgent planner, BaseAgent reviewer) {
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

有几个和源码直接相关的行为需要记住：

- `addAgent(agent)` 默认使用 `agent.getCard().getName()` 作为成员 ID。
- `addAgent(agent, agentId)` 会检查重复 ID 和 `config.getMaxAgents()` 上限。
- 成员注册成功后，`card.getAgentCards()` 会自动同步追加对应 `AgentCard`。
- 如果成员暴露 `getController().setGroup(BaseGroup)`，`BaseGroup` 会尝试自动注入当前 group 引用。

### 4. 用分组会话执行

`BaseGroup.invoke(...)` 和 `stream(...)` 的第二个参数是 `AgentGroupSessionApi`。实际使用时，最方便的入口是 `MultiAgentSessions`：

```java
import com.openjiuwen.core.multiagent.MultiAgentSessions;
import com.openjiuwen.core.multiagent.Session;

Session session = MultiAgentSessions.createAgentGroupSession(
        "planning-001",
        Map.of("locale", "zh-CN")
);

Object result = group.invoke(Map.of("task", "整理需求"), session);
```

`Session` 其实是 `AgentGroupSessionApi` 的包级别别名，目的就是让调用方能继续沿用 `com.openjiuwen.core.multiagent.Session` 这一顶层导入入口。

## 什么时候该用 `BaseGroup`

- 你在写 Java 新版多智能体能力，希望沿着当前 `multiagent` 包的 Card + Config 模式扩展。
- 你需要一个明确的 group 边界，把多个 `BaseAgent` 作为一个整体对外暴露。
- 你想自己控制成员注册、group 配置和执行入口，而不是把路由全交给 legacy controller。

## 常见注意点

- 这里讨论的 Java 真实类型是 `BaseGroup`；代码和文档都应直接使用这个名称。
- `groupId` 固定来自 `GroupCard.name`，不是 `id`。
- `GroupConfig` 本身不做阈值合法性校验；如果你把上限配得不合理，问题会在运行阶段暴露出来。
- `BaseGroup` 只提供封装骨架，不替你生成 leader、worker 或协作策略；这些属于你在子类里的实现责任。

## 示例入口

- [示例：Groups Java Examples](../../../../examples/groups/README.md)
- [示例：hierarchical_group](../../../../examples/groups/hierarchical_group/README.md)

## 参考入口

- [API 文档：BaseGroup](../API文档/com.openjiuwen.core/multiagent/BaseGroup.md)
- [API 文档：GroupConfig](../API文档/com.openjiuwen.core/multiagent/GroupConfig.md)
- [API 文档：GroupCard](../API文档/com.openjiuwen.core/multiagent/schema/GroupCard.md)
- [API 文档：MultiAgentSessions](../API文档/com.openjiuwen.core/multiagent/MultiAgentSessions.md)
- [API 文档：Session](../API文档/com.openjiuwen.core/multiagent/Session.md)
- [API 文档：AgentGroupSessionApi](../API文档/com.openjiuwen.core/session/AgentGroupSessionApi.md)

## 本页说明

- 团队封装主线落在 `BaseGroup + GroupCard + GroupConfig + Session`。
- 协作策略和成员路由由 group 子类自行实现。
- 文中的最小示例只使用当前 Java 仓库里真实存在的构造器、方法签名和会话入口。
