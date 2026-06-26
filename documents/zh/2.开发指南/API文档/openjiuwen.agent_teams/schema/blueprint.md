# openjiuwen.agent_teams.schema.blueprint

Java 对应包：`com.openjiuwen.agent_teams.schema`

该模块提供团队蓝图规格、可插拔 transport/storage 注册表，以及 `TeamAgentSpec.build()` 构建入口。

## TeamBlueprintPackage

`TeamBlueprintPackage` 对应 Python 模块级导出和注册函数：

- `registerTransport(name, factory)`：注册 transport 配置工厂
- `registerStorage(name, factory)`：注册 storage 配置工厂
- `buildTransport(type, params)`：按类型解析 transport 配置
- `buildStorage(type, params)`：按类型解析 storage 配置
- `EXPORTED_SYMBOLS`：对应 Python `__all__`

内置 transport：

- `inprocess`
- `pyzmq`

内置 storage：

- `sqlite`
- `postgresql`
- `mysql`
- `memory`

## TransportSpec

`TransportSpec` 保存 transport 类型和动态参数：

- `type`
- `params`
- `build()`

内置 transport 会构造 `MessagerTransportConfig`，并将 `backend` 设置为配置类型。

## StorageSpec

`StorageSpec` 保存 storage 类型和动态参数：

- `type`
- `params`
- `build()`

内置 SQL storage 会构造 `DatabaseConfig`。`memory` 保留为动态 map 配置，用于对应 Python 中可注册的 storage BaseModel。

## LeaderSpec

`LeaderSpec` 描述 leader 身份：

- `memberName`，默认 `team_leader`
- `displayName`，默认 `Team Leader`
- `persona`
- `modelName`

## TeamAgentSpec

`TeamAgentSpec` 继承现有 `AgentConfigurator.TeamAgentSpec`，并补齐 blueprint 层字段和构建逻辑：

- `enableTeamPlan`
- `leader`
- `modelPool`
- `modelRouter`
- `modelPoolStrategy`
- `language`
- `transport`
- `storage`

`build()` 会执行：

- `modelPool` 与 `modelRouter` 互斥检查
- `externalCliAgents` 重名检查
- leader / predefined member 保留名检查
- HITT / Bridge capability ceiling 检查
- `spawnMode=inprocess` 时默认补 `TransportSpec(type="inprocess")`
- 解析语言并传播到未指定语言的 role spec
- 展开 router 或复制 pool，构建 `TeamSpec`
- 解析 storage 到 DB 配置
- 使用 `ModelAllocators.buildModelAllocator(...)` 分配 leader model
- 创建并配置 `TeamAgent`

## Python 对应关系

该 Java 实现对应 Python 源文件：

`openjiuwen/agent_teams/schema/blueprint.py`
