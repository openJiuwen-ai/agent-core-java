# com.openjiuwen.core.multiagent.legacy.BaseGroup

## abstract class BaseGroup

```java
@Deprecated
public abstract class BaseGroup extends LegacyBaseGroup
```

`BaseGroup` 是 legacy 分组基类的名称兼容层，本质上只是把历史 `BaseGroup` 类型名映射到 `LegacyBaseGroup`。

## 实例化说明

### `protected BaseGroup(AgentGroupConfig config)`

供兼容层子类调用，内部直接使用 legacy 配置初始化父类 `LegacyBaseGroup`。

## 抽象方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | legacy 分组的同步执行入口。 |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | legacy 分组的流式执行入口。 |

## 说明

- 该类型不增加任何新状态，所有 Agent 注册和组 ID 管理由 `LegacyBaseGroup` 承担。
- `LegacyCompatibilityAliasTest` 用匿名子类验证了该别名仍可直接实例化并返回正确的 `groupId`。
