# openjiuwen.agent_teams.schema.deep_agent_spec

Java 对应包：`com.openjiuwen.agent_teams.schema`

该模块提供可序列化的 DeepAgent 规格、rail/tool 动态注册表，以及构建前的配置解析对象。

## 模块注册表

`DeepAgentSpecPackage` 对应 Python 模块级注册函数：

- `registerRailType(name, factory)`
- `registerToolType(name, factory)`
- `buildRail(type, params, language, workspace)`
- `buildTool(type, params, language, toolId)`
- `EXPORTED_SYMBOLS`

内置 rail 名称包括：

- `task_planning`
- `skill_use`
- `subagent`
- `filesystem`
- `context_engineering`
- `token_tracking`
- `tool_tracking`
- `ask_user`
- `confirm_interrupt`

内置 tool 名称包括：

- `web_search`
- `web_fetch`

## 规格类型

- `TeamModelConfig`：保存 `ModelClientConfig` 和 `ModelRequestConfig`，`build()` 委托现有 `Model` 构造逻辑。
- `VisionModelSpec`：保存视觉模型配置并输出动态配置 map。
- `AudioModelSpec`：保存音频模型配置并输出动态配置 map。
- `WorkspaceSpec`：保存 workspace 根目录、语言和 stable-base 标志，`build()` 生成 `Workspace`。
- `ProgressiveToolSpec`：保存 progressive tool 开关、常显工具、默认工具和最大加载数量。
- `SysOperationSpec`：保存 system operation 配置并输出 sys-operation card map。
- `RailSpec`：通过 rail 注册表解析 rail。
- `BuiltinToolSpec`：通过 tool 注册表解析 builtin tool。
- `SubAgentSpec`：解析 sub-agent model、workspace、rails、tools、skills 和 sys-operation。
- `DeepAgentSpec`：解析主 DeepAgent 的模型、card、prompt、tools、mcps、subagents、rails、workspace、skills、视觉/音频配置和 progressive tool 参数。

## DeepAgentSpec.build()

当前 Java 仓库没有独立的 live `DeepAgent` / `create_deep_agent` 类型可直接构造，因此 `DeepAgentSpec.build()` 返回 `DeepAgentBuildConfig`：这是已解析、可交给运行时工厂的配置对象。它保留 Python `build()` 的参数解析顺序，包括：

- `TeamModelConfig.build()`
- 语言解析
- workspace materialization
- rail materialization
- subagent materialization
- sys-operation card materialization
- builtin tool materialization
- progressive tool kwargs

## Python 对应关系

该 Java 实现对应 Python 源文件：

`openjiuwen/agent_teams/schema/deep_agent_spec.py`
