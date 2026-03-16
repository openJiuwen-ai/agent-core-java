# multi_agent 模块缺漏复核清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\multi_agent`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\multiagent`
- 本文只记录“Java 相对 Python 仍未完全对齐的公开 API / 可见兼容层差异”
- 默认不计入缺漏:
  - `snake_case -> camelCase`
  - `async -> 同步`
  - 字段 -> getter/setter
  - Python `__all__` 改为 Java 显式导入
  - Java 为运行时适配新增 `GroupEvent`

## 复核结论

- multi_agent 主干类已经齐备，新的 `GroupConfig`、`BaseGroup`、`GroupCard`、`EventDrivenGroupCard` 不存在关键能力缺口。
- 当前缺漏主要集中在“包级门面”和“legacy 兼容体验”，而不是核心分组执行逻辑。
- 如果目标是“功能可用”，当前 Java 版基本满足；如果目标是“对外 API 形态严格贴近 Python”，仍有几项需要补齐。

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | 新版包级会话导出 | `openjiuwen.core.multi_agent.Session` 可直接从 multi_agent 顶层导入 | Java 只能显式使用 `com.openjiuwen.core.session.AgentGroupSessionApi` | 迁移示例和使用体验与 Python 不一致 |
| `P1` | 新版包级会话工厂 | `create_agent_group_session(...)` 顶层可直接导入 | Java 仅有 `AgentGroupSessionApi.create(...)`，未在 `multiagent` 包内提供对应 facade | 需要额外了解 session 包路径，门面层未完全对齐 |
| `P1` | legacy 会话类型 | `openjiuwen.core.multi_agent.legacy.AgentGroupSession` 为独立导出类 | Java 没有 `legacy.AgentGroupSession` 对位类型，只能复用 `AgentGroupSessionApi` | 旧代码若按 legacy 导入路径迁移，无法直接一一替换 |
| `P2` | legacy 包级兼容门面 | `legacy` 模块集中导出 `AgentGroupConfig/BaseGroup/ControllerGroup/AgentGroupSession/GroupCard/...` | Java 无统一 legacy facade/alias 层，调用方需分别导入 `LegacyBaseGroup`、`LegacyGroupCard` 等具体类 | 旧 API 迁移时心智负担更高 |
| `P2` | 新版包级 facade | Python 顶层模块统一重导出 `GroupCard/EventDrivenGroupCard/GroupConfig/BaseGroup/Session/create_agent_group_session` | Java 没有类似 `MultiAgentSessions` 或统一 facade 类 | 示例代码和入口一致性不足 |
| `P2` | legacy 命名兼容 | Python `legacy.BaseGroup`、`legacy.GroupCard` 保持原名 | Java 为避免新旧冲突改成 `LegacyBaseGroup`、`LegacyGroupCard`、`LegacyEventDrivenGroupCard` | 严格按名称迁移时无法做到“同名替换” |
| `P3` | legacy 会话便捷能力 | Python `AgentGroupSession` 继承 `AgentSession`，天然具备父类公开能力 | Java `AgentGroupSessionApi` 是薄包装，更多能力需通过 `getInner()` 下钻 | 调试和高级用法不如 Python/legacy 直观 |

## 已确认不缺的部分

- `GroupConfig` 三个核心字段和三个链式配置方法均已对齐。
- 新版 `BaseGroup` 的 `configure/addAgent/removeAgent/getAgent/getAgentCount/listAgents/invoke/stream` 已全部存在。
- 新版 `GroupCard` / `EventDrivenGroupCard` 字段结构已对齐。
- legacy `AgentGroupConfig`、`ControllerGroup`、`BaseGroupController`、`DefaultGroupController` 主 API 已对齐。
- legacy `schema.GroupCard` / `EventDrivenGroupCard` 已通过 `LegacyGroupCard` / `LegacyEventDrivenGroupCard` 补齐。
- Java 已通过 `GroupEvent` 承接 legacy 路由事件语义，不构成缺失，只是实现载体变化。

## 建议优先级

1. 先补门面层:
   - 在 `multiagent` 包下提供会话 facade，统一承接 `Session` 与 `create_agent_group_session`
   - 视项目命名规范决定是否增加 `MultiAgentSessions` 一类便捷入口
2. 再补 legacy 兼容入口:
   - 为 `legacy.AgentGroupSession` 提供对位别名或薄包装
   - 评估是否需要增加 legacy facade，减少导入路径迁移成本
3. 最后处理命名兼容增强:
   - 视是否接受新增 deprecated alias，补 `BaseGroup` / `GroupCard` 旧名桥接

## 小结

- multi_agent Java 版现在缺的不是“分组跑不起来”，而是“对外入口还不够像 Python”。
- 真正需要补的部分不多，且大都属于低风险门面增强。
- 如果后续目标是严格对齐 Python 文档和示例，优先补会话 facade 与 legacy 会话别名，收益最高。