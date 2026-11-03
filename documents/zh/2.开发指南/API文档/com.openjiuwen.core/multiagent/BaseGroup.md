# com.openjiuwen.core.multi_agent.BaseGroup

## abstract class BaseGroup

```java
public abstract class BaseGroup
```

`BaseGroup` 是新版多 Agent 分组的抽象基类，围绕 `GroupCard + GroupConfig` 模式统一管理 Agent 注册、组配置与同步/流式执行入口。

## 实例化说明

### `protected BaseGroup(GroupCard card, GroupConfig config)`

供子类在自定义分组实现中调用；当 `config == null` 时会回退到默认 `GroupConfig`，并用 `card.getName()` 固化当前 `groupId`。

### `protected BaseGroup(GroupCard card)`

供只需要卡片信息的子类使用，内部会委托到双参数版本并创建默认 `GroupConfig`。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `configure(GroupConfig config)` | `BaseGroup` | 覆盖当前运行配置，并返回自身以便链式调用。 |
| `addAgent(BaseAgent agent, String agentId)` | `BaseGroup` | 向分组注册 Agent；未显式传入 `agentId` 时优先使用 `agent.getCard().getName()`。 |
| `addAgent(BaseAgent agent)` | `BaseGroup` | 使用 Agent 卡片名称作为 ID 的便捷重载。 |
| `removeAgent(String agentId)` | `BaseGroup` | 按 ID 移除 Agent，并同步删除 `card.getAgentCards()` 中同名卡片。 |
| `removeAgent(BaseAgent agent)` | `BaseGroup` | 通过 Agent 实例推断 ID 后执行移除。 |
| `getAgent(String agentId)` | `BaseAgent` | 读取指定 ID 的 Agent；不存在时返回 `null`。 |
| `getAgentCount()` | `int` | 返回当前已注册 Agent 数量。 |
| `listAgents()` | `List<String>` | 以注册顺序返回所有 Agent ID。 |
| `getCard()` | `GroupCard` | 返回当前分组卡片。 |
| `getConfig()` | `GroupConfig` | 返回当前运行配置。 |
| `getGroupId()` | `String` | 返回构造阶段固定下来的分组 ID。 |
| `getAgents()` | `Map<String, BaseAgent>` | 返回内部 Agent 映射。 |
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 子类实现的同步执行入口。 |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 子类实现的流式执行入口。 |

## 说明

- `addAgent(...)` 会校验重复 ID 和 `config.getMaxAgents()` 上限，超出时抛出 `AGENT_GROUP_ADD_RUNTIME_ERROR`。
- 注册成功后，若 Agent 暴露 `getController().setGroup(BaseGroup)`，基类会通过反射自动注入当前分组引用。
- `card.getAgentCards()` 会随着 Agent 的注册与移除自动同步，便于对外暴露最新成员列表。
