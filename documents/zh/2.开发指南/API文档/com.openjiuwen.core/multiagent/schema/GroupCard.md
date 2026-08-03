# com.openjiuwen.core.multi_agent.schema.GroupCard

## class GroupCard

```java
public class GroupCard extends BaseCard
```

`GroupCard` 是新版多 Agent 分组的身份卡片，描述分组名称、成员、主题、版本和标签等元数据。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `agentCards` | `List<AgentCard>` | `new ArrayList<>()` | 当前分组公开暴露的 Agent 卡片列表。 |
| `topic` | `String` | `""` | 分组默认主题。 |
| `version` | `String` | `"1.0.0"` | 分组卡片版本。 |
| `tags` | `List<String>` | `new ArrayList<>()` | 与分组能力相关的标签集合。 |

## 继承字段

- 继承 `BaseCard` 的 `id`、`name` 与 `description`。

## 说明

- `BaseGroup.addAgent(...)` 会在 Agent 成功注册后自动把对应 `AgentCard` 追加到 `agentCards`。
- 类型使用 Lombok 生成常规访问器、构造器和 builder。
