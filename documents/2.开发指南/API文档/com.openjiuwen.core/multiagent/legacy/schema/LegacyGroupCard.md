# com.openjiuwen.core.multiagent.legacy.schema.LegacyGroupCard

## class LegacyGroupCard

```java
@Deprecated
public class LegacyGroupCard extends BaseCard
```

`LegacyGroupCard` 描述旧版多 Agent 分组的基础身份信息，补充成员卡片列表与分组 topic。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `agentCard` | `List<AgentCard>` | `new ArrayList<>()` | 组内 Agent 卡片列表；字段名保留 legacy 单数写法。 |
| `topic` | `String` | `""` | 分组默认主题。 |

## 继承字段

- 继承 `BaseCard` 的 `id`、`name` 与 `description`。

## 说明

- 类型使用 Lombok 自动生成常规访问器、构造器和 builder。
- 这是 legacy 兼容类；新版 `GroupCard` 已把成员字段名调整为更直观的 `agentCards`。
