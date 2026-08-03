# com.openjiuwen.core.multi_agent.schema.EventDrivenGroupCard

## class EventDrivenGroupCard

```java
public class EventDrivenGroupCard extends GroupCard
```

`EventDrivenGroupCard` 在新版 `GroupCard` 之上增加订阅映射，可用于描述事件驱动型多 Agent 分组。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `subscriptions` | `Map<String, List<String>>` | `new HashMap<>()` | `agentId -> topic 列表` 的订阅关系映射。 |

## 继承字段

- 继承 `GroupCard` 的 `agentCards`、`topic`、`version` 与 `tags`。
- 间接继承 `BaseCard` 的 `id`、`name` 与 `description`。

## 说明

- 类型使用 `@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor` 与 `@EqualsAndHashCode(callSuper = true)`。
- 当分组需要显式声明“哪个 Agent 订阅哪些 topic”时，可优先使用该类型。
