# legacy

`com.openjiuwen.core.multi_agent.legacy` 收纳旧版 `AgentGroup` / `ControllerGroup` 模式的兼容入口，用于承接历史导入路径和 Controller 驱动的组内路由逻辑。

## Modules

| 模块 | 说明 |
|---|---|
| [`schema`](./legacy/schema.README.md) | legacy 分组卡片及其历史导入别名。 |

## Types

| 类型 | 说明 |
|---|---|
| [`AgentGroupConfig`](./legacy/AgentGroupConfig.md) | 旧版分组运行参数。 |
| [`AgentGroupSession`](./legacy/AgentGroupSession.md) | 旧版分组会话别名。 |
| [`BaseGroup`](./legacy/BaseGroup.md) | 旧版 `BaseGroup` 名称兼容层。 |
| [`BaseGroupController`](./legacy/BaseGroupController.md) | 负责消息队列路由与订阅管理的抽象控制器。 |
| [`ControllerGroup`](./legacy/ControllerGroup.md) | 将执行逻辑委托给 `BaseGroupController` 的旧版分组实现。 |
| [`DefaultGroupController`](./legacy/DefaultGroupController.md) | 按 `receiverId` 或订阅关系路由消息的默认控制器。 |
| [`GroupEvent`](./legacy/GroupEvent.md) | 旧版组内消息路由事件模型。 |
| [`LegacyBaseGroup`](./legacy/LegacyBaseGroup.md) | 旧版分组抽象基类。 |

## Notes

- 本包全部类型都带有 `@Deprecated`，仅用于兼容历史调用入口。
- `LegacyCompatibilityAliasTest` 验证了会话别名、schema 别名与 `BaseGroup` 类型名仍可按历史入口使用。
