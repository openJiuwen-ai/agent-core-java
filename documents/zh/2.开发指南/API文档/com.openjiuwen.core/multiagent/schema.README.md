# schema

`com.openjiuwen.core.multiagent.schema` 定义新版分组身份卡片与事件驱动卡片，是 `BaseGroup` 新 Card + Config 模式的核心数据模型。

## Types

| 类型 | 说明 |
|---|---|
| [`EventDrivenGroupCard`](./schema/EventDrivenGroupCard.md) | 在 `GroupCard` 基础上补充订阅映射的事件驱动分组卡片。 |
| [`GroupCard`](./schema/GroupCard.md) | 分组名称、成员卡片、topic、版本和标签等身份信息。 |

## Notes

- 这两个类型都基于 Lombok 生成常规访问器和 builder。
- `BaseGroup` 会直接读取并维护 `GroupCard.getAgentCards()` 中的成员卡片列表。
