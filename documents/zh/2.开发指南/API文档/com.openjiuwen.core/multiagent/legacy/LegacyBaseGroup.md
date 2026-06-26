# com.openjiuwen.core.multi_agent.legacy.LegacyBaseGroup

## abstract class LegacyBaseGroup

```java
@Deprecated
public abstract class LegacyBaseGroup
```

`LegacyBaseGroup` 是旧版多 Agent 分组的抽象基类，基于 `AgentGroupConfig` 管理成员并暴露同步 / 流式执行接口。

## 实例化说明

### `protected LegacyBaseGroup(AgentGroupConfig config)`

供 legacy 分组子类调用；初始化时会保存配置对象，并把 `config.getGroupId()` 固定为当前 `groupId`。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `addAgent(String agentId, BaseAgent agent)` | `void` | 向分组注册 Agent；重复 ID 或超出 `maxAgents` 时抛出运行时错误。 |
| `getAgentCount()` | `int` | 返回当前已注册 Agent 数量。 |
| `getConfig()` | `AgentGroupConfig` | 返回分组配置。 |
| `getGroupId()` | `String` | 返回固定的分组 ID。 |
| `getAgents()` | `Map<String, BaseAgent>` | 返回内部 Agent 映射。 |
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 子类实现的同步执行入口。 |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 子类实现的流式执行入口。 |

## 说明

- 与新版 `BaseGroup` 不同，legacy 基类只提供 `addAgent(...)`，不负责同步 `GroupCard` 之类的显式身份模型。
- 注册 Agent 时若其控制器暴露 `setGroup(LegacyBaseGroup)`，基类会通过反射自动注入当前分组引用。
