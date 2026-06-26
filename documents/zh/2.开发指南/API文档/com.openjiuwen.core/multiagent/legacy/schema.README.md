# schema

`com.openjiuwen.core.multi_agent.legacy.schema` 收纳 legacy 分组卡片类型，以及为历史导入入口保留的别名类。

## Types

| 类型 | 说明 |
|---|---|
| [`EventDrivenGroupCard`](./schema/EventDrivenGroupCard.md) | 旧版 `EventDrivenGroupCard` 导入别名，继承 `LegacyEventDrivenGroupCard`。 |
| [`GroupCard`](./schema/GroupCard.md) | 旧版 `GroupCard` 导入别名，继承 `LegacyGroupCard`。 |
| [`LegacyEventDrivenGroupCard`](./schema/LegacyEventDrivenGroupCard.md) | 真正承载订阅映射字段的 legacy 事件分组卡片。 |
| [`LegacyGroupCard`](./schema/LegacyGroupCard.md) | 承载分组成员卡片列表与 topic 的 legacy 分组卡片。 |

## Notes

- `EventDrivenGroupCard` 与 `GroupCard` 本身没有新增成员，只保留历史导入路径。
- `LegacyCompatibilityAliasTest` 验证了这些别名类型仍可直接读写 `topic` 与 `subscriptions`。
