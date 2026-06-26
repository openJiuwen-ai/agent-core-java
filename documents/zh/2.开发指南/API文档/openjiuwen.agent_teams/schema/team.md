# openjiuwen.agent_teams.schema.team

Java 对应包：`com.openjiuwen.agent_teams.schema`

该模块提供团队级 schema 值对象，覆盖成员操作结果、团队完成快照、生命周期/角色枚举、桥接成员配置、外部 CLI 成员配置、团队定义和成员运行上下文。

## 主要类型

- `MemberOpResult`：成员变更操作结果，保留 `ok` 和失败原因，并提供 `success()` / `fail(reason)` 工厂。
- `TeamCompletionSnapshot`：团队完成瞬间的成员数和任务数快照。
- `TeamLifecycle`：团队生命周期，取值为 `temporary`、`persistent`。
- `TeamRole`：团队角色，取值为 `leader`、`teammate`、`human_agent`、`bridge_agent`。
- `BridgeMailboxInjectMode`：桥接消息注入方式，取值为 `passthrough`、`rephrase`。
- `TeamMemberSpec`：预定义非 bridge 成员的声明式配置。
- `BridgeMemberSpec`：bridge 成员专用配置，固定 `role_type=bridge_agent`，并保留协议和 adapter 参数。
- `ExternalCliAgentSpec`：外部 CLI agent 启动配置。
- `TeamSpec`：团队名称、展示名称、leader、语言、metadata、模型池和模型池分配策略。
- `TeamRuntimeContext`：单个团队成员的运行上下文，包含角色、成员名、persona、团队配置、messager 配置、数据库配置、成员模型和 CLI agent 名称。

## 运行时桥接

`TeamRole`、`TeamMemberSpec`、`TeamSpec` 和 `TeamRuntimeContext` 提供到 `AgentConfigurator` 内部运行时类型的转换方法，便于 schema 层保持独立，同时复用已有团队运行时配置逻辑。

`TeamRuntimeContext.dbConfig` 使用 `Map<String, Object>` 表示配置边界，以兼容 Python 中 `DatabaseConfig | MemoryDatabaseConfig` 的联合语义。默认值仍等价于 Python 的 `DatabaseConfig()`。

## Python 对应关系

该 Java 实现对应 Python 源文件：

`openjiuwen/agent_teams/schema/team.py`
