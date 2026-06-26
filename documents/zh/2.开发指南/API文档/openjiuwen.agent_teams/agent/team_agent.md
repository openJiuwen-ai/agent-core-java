# openjiuwen.agent_teams.agent.team_agent

## TeamAgent

`TeamAgent` 是 agent teams 的统一运行时门面，对应 Python 源文件 `openjiuwen/agent_teams/agent/team_agent.py`。

Java 实现保持 Python 的组合式结构：构造时创建 `AgentConfigurator`、`TeamAgentState`、`SpawnManager`、`RecoveryManager`、`SessionManager`、`StreamController` 和 `CoordinationKernel`，对外方法主要委托给这些 manager。

核心行为：

- `configure` 先初始化 infra，再初始化 member runtime，并建立 coordination event bus。
- `deliverInput` 复刻 Python 的三段路由：运行中走 `steer/followUp`，过渡中的 round 入 pending queue，空闲时启动新 round。
- `lookupHumanAgentRuntime` 与 `lookupBridgeAgentRuntime` 只返回 in-process spawn 的 live agent，跨进程场景返回空。
- `startCoordination`、`pauseCoordination`、`stopCoordination`、`shutdownSelf` 和 `destroyTeam` 委托 coordination、stream、spawn 与 backend 清理能力。
- `buildSpawnPayload`、`buildMemberContext`、`buildSpawnConfig`、`fromSpawnPayload` 和 `recoverFromSession` 保持 Python spawn/recovery 数据流。
- `persistAllocatorState`、`persistSessionManifest` 和 team DB lifecycle 写入沿用 `TeamRuntimeMetadata`。

动态边界说明：

- Python `inputs`、spawn payload、runtime model、stream chunk 和外部 backend 能力本身是动态对象，Java 保留 `Object` 或 `Map<String, Object>` 边界，并用 `BridgeAgentBackend`、`TeamCleaner`、`InteractiveGateway`、`AutoStartBackend` 等窄接口表达可选 backend 能力。
- 当成员数据库行尚未创建时，Java 使用 no-op member store 表达 Python `TeamMember` 对缺失行的容忍语义，状态更新会变成安全 no-op。
