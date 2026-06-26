# openjiuwen.agent_teams.agent.AgentConfigurator

`AgentConfigurator` 对应 Python 模块 `openjiuwen/agent_teams/agent/agent_configurator.py`，负责 TeamAgent 的配置、基础设施装配、运行时资源转发和成员启动 payload 构建。

## 主要能力

- `resolveTeamMode` / `_resolveTeamMode`：保留 Python `_resolve_team_mode` 语义，显式 `teamMode` 优先；只有普通预定义队友时推导为 `hybrid`；仅 `human_agent` 或 `bridge_agent` 头像成员时保持 `default`。
- `setupInfra`：构建 blueprint、复制并修正成员 messager 配置、创建 team workspace、装配 team backend，并在非 leader 成员启用 worktree manager。
- `setupAgent`：采用外部传入的 `MemberRuntime`，或构造 Java 侧的配置化运行时，保持 Python 对 human agent、workspace、memory 和 customizer 的分支语义。
- `buildSpawnPayload`、`buildMemberContext`、`buildMemberMessagerConfig`、`buildSpawnConfig`：保留 Python spawn wire format 的关键字段和稳定端口分配规则。

## TeamInfra

`AgentConfigurator.TeamInfra` 对应 Python 模块 `openjiuwen/agent_teams/agent/infra.py` 中的 `TeamInfra` dataclass。它是每个进程内的基础设施容器，保存 message bus、team backend、team workspace manager，以及由 backend 派生但可被测试替身直接注入的 task/message manager。

字段保持 Python 默认值语义：`messager`、`teamBackend`、`workspaceManager`、`taskManager`、`messageManager` 默认为 `null`，`workspaceInitialized` 默认为 `false`。setter 仅保存调用方传入的引用，不创建跨进程共享状态。

## 兼容说明

本类处在 TeamAgent 强连通组件中。为避免扩大当前批次范围，Java 实现将 configurator 直接使用的窄域模型作为嵌套类型保留在同一文件中；后续独立任务仍可翻译 sibling Python 模块的完整类型。
