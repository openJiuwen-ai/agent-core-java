# openjiuwen.agent_teams.external.cli_agent.spawn

## CliAgentSpawn

`CliAgentSpawn` 对应 Python 源文件 `openjiuwen/agent_teams/external/cli_agent/spawn.py`，负责把 `TeamRuntimeContext` 转换成第三方 CLI 成员运行时。

核心行为：

- `descriptorFromContext` 构建 external CLI 加入团队所需的 `TeamJoinDescriptor`，并把非空 `direct_addr` 改为 `tcp://127.0.0.1:*`，避免外部 CLI 进程和成员 shell 绑定同一 ROUTER 地址。
- `TeamJoinDescriptor.toEnv` 生成 `OPENJIUWEN_TEAM_JOIN` 环境变量，值为紧凑 JSON。
- `buildCliRuntime` 根据 `CliAgentAdapter.supportsStdinInjection` 选择长驻进程 `ExternalCliRuntime` 或逐轮重启的 `ReinvokeCliRuntime`。
- 启动环境先继承当前进程环境并剥离 adapter 声明的父 CLI session 前缀，再合并 `extraEnv`，最后合并 descriptor env，确保团队身份信息不可被 extra env 覆盖。
- 当 adapter 没有 launch-time MCP 注入参数时，执行 out-of-band MCP 注册命令；没有注册机制时只记录告警，保持 Python 的 best-effort 语义。

动态边界说明：

- `BuildOptions` 对应 Python `build_cli_runtime` 的关键字参数，用于传入 cwd、命令覆盖、MCP 配置、系统提示词和额外环境变量。
- 由于 `descriptor.py` 是后续独立任务，当前文件内保留 scoped `TeamJoinDescriptor` 支撑 `spawn.py` 的直接行为；后续独立 descriptor 翻译可再收敛为共享类型。
