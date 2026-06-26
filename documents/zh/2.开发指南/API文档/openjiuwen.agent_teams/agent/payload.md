# openjiuwen.agent_teams.agent.SpawnPayloadBuilder

`SpawnPayloadBuilder` 对应 Python 模块 `openjiuwen/agent_teams/agent/payload.py`，维护 teammate spawn 的跨进程 wire format。

## 主要能力

- `buildSpawnPayload`：输出固定顶层键 `coordination` 和 `query`；`coordination` 保留 `team_name`、`display_name`、`leader_member_name`、`member_name`、`role`、`persona`、`transport`。
- `buildMemberContext`：基于 `TeamMemberSpec` 构造 teammate runtime context，并继承 leader context 的 team spec、DB config 和稳定 messager config。
- `buildMemberMessagerConfig`：为每个 member name 分配稳定 direct port；重复查询同一 member 复用端口；成员 transport 继承 leader pub/sub 地址并移除 `pubsub_bind` metadata。
- `buildSpawnConfig`：输出 `SpawnAgentConfig`，其中 payload 的 `spec` 和 `context` 使用 Python `model_dump(mode="json")` 对齐的 snake_case map。
- `buildMemberLoggingConfig`：复制当前 logging config，并把 file sink target 重写到 `teammates/<member>/<filename>`，`stdout` 和 `stderr` 不重写。

## Wire Contract

`buildSpawnPayload` 的输出被 spawned process 的 `TeamAgent.from_spawn_payload` 消费。新增、删除或重命名 key 都会破坏跨进程兼容，因此 Java 测试冻结了 payload 顶层键和 coordination 键集合。
