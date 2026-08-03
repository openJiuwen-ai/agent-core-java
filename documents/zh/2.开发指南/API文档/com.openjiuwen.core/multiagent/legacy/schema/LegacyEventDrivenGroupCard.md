# com.openjiuwen.core.multi_agent.legacy.schema.LegacyEventDrivenGroupCard

## class LegacyEventDrivenGroupCard

```java
@Deprecated
public class LegacyEventDrivenGroupCard extends LegacyGroupCard
```

`LegacyEventDrivenGroupCard` 在 legacy `GroupCard` 基础上增加订阅映射，用于驱动 `DefaultGroupController` 的广播路由。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `subscriptions` | `Map<String, List<String>>` | `new HashMap<>()` | `agentId -> topic 列表` 的订阅关系映射。 |

## 说明

- 类型使用了 `@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor` 与 `@EqualsAndHashCode(callSuper = true)`。
- 由于启用了 Lombok，常规 getter/setter/builder 均由注解自动生成。
- 这是 legacy 兼容类，推荐迁移到新版 `com.openjiuwen.core.multi_agent.schema.EventDrivenGroupCard`。
