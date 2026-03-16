# single_agent 模块缺漏复核清单

## 复核范围

- Python 基线: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\single_agent`
- Java 对照: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\singleagent`
- 本文只记录“Java 相对 Python 仍未完全对齐的公开 API / 可见语义差异”
- 默认不计入缺漏:
  - `snake_case -> camelCase`
  - `async -> 同步`
  - `property -> getter/setter`
  - Python `@rail` 装饰器改写为 `RailExecutor.execute(...)`
  - Python 包级 `__all__` / `__getattr__` 改为 Java 显式导入

## 复核结论

- single_agent 新版主流程类已经基本齐备，但仍存在若干公共 API 缺口，尤其是 `BaseAgent` 技能便捷入口、`AgentCallbackContext.lifecycle()`、`AgentRail.skills`、`RemoteSkillUtil` 的公开搜索 API，以及 `legacy` 兼容层的大量旧接口。
- 与之前 `singleagent_needfix.md` 中已经修复的实现缺陷不同，本文关注点是“对外 API 是否还少了类型、方法或关键语义”。

## 已确认不再缺的部分

- `AgentCallbackManager.unregisterRail(...)` 已能同时移除 rail callback 与 rail tools。
- `AgentRail.buildCallback()` 已补 `setAccessible(true)`，反射 rail hook 可访问性问题已修复。
- `ReActAgent` / `ReActAgentEvolve` 已在 `AFTER_INVOKE` 前恢复 `InvokeInputs`。
- `ControllerAgent.configure(Map)`、`releaseSession(...)`、`BaseError` 透传语义已修正。
- `AbilityManager.listToolInfo(...)` 过滤、MCP 工具枚举、`AgentCard.toolInfo()` 参数结构、MCP 名称执行路径已补齐。
- `ReActAgent` / `ReActAgentEvolve` 已接入 context reload tool。
- `RemoteSkillUtil.uploadSkillFromGitHub(...)` 已不再是空壳，实现了 GitHub 搜索、下载和本地写入。

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `BaseAgent.register_skill()` | BaseAgent 公开提供技能注册便捷入口 | Java `BaseAgent` 没有 `registerSkill(...)`，只能手动走 `getSkillUtil().registerSkills(...)` | 依赖 BaseAgent 统一技能入口的调用方无法直接迁移 |
| `P1` | `BaseAgent.register_remote_skills()` | BaseAgent 公开提供远程 skill 注册便捷入口 | Java `BaseAgent` 没有 `registerRemoteSkills(...)` | 调用点必须下沉到 `SkillUtil`，对齐度不足 |
| `P1` | `rail.AgentCallbackContext.lifecycle()` | 提供 before/after 成对生命周期上下文管理 | Java 仅在调用点手写 `fire(before)` + `try/finally fire(after)` | 对外缺少可复用的统一生命周期 API |
| `P1` | `rail.AgentRail.skills` | Rail 可携带 `tools` 和 `skills` 两类附属资源 | Java `AgentRail` 只保留 `tools`，没有 `skills` 字段/访问器 | Python 中预留的 rail-skill 扩展接口无法在 Java 侧表达 |
| `P1` | `skills.GitHubError` | 公开 GitHub 访问专用异常类型 | Java 没有对位类型，统一抛 `RuntimeException` | 调用方无法基于专用异常类型做精细化处理 |
| `P1` | `skills.RemoteSkillUtil.search_github_for_skills()` | 公开返回 skill 文件列表与 skill 根路径 | Java 仅有私有 `searchGitHubForSkills(...)` | Java 只能“直接下载”，不能复用搜索结果做预检查/预览 |
| `P1` | `skills.RemoteSkillUtil._list_github_files()` 可复用能力 | Python 虽以下划线命名，但在远程 skill 工具里构成稳定可调用流程 | Java 仅保留私有 `listGitHubFiles(...)` / `recursivelyListGitHubFiles(...)` | 若需要只枚举 GitHub 文件而不下载，Java 无公开入口 |
| `P1` | `schema.AgentCard.input_params/output_params` | 支持 `dict[str, Any] | Type[BaseModel]` | Java 仅支持 `Map<String,Object>` | 以模型类定义输入/输出 schema 的 Python 用法无法直接迁移 |
| `P1` | `legacy.WorkflowFactory` | 提供并发安全 workflow provider 工厂 | Java `legacy` 无此公开类型 | 旧代码若依赖 legacy provider 模式，无法按原 API 迁移 |
| `P1` | `legacy.workflow_provider(...)` | 装饰器式 workflow provider 工厂 | Java `legacy` 无对位工厂方法 | 旧代码中基于 provider 注册 workflow 的调用方式缺失 |
| `P1` | `legacy.AgentSession` | legacy ReActAgent 公开会话包装器 | Java `legacy` 无对位类型 | 旧代码若显式操作 legacy agent session，迁移受阻 |
| `P1` | `legacy.config.LLMCallConfig` | 公开旧版 LLM call 配置模型 | Java `legacy.config` 无此类型 | 旧配置对象装配无法直接迁移 |
| `P1` | `legacy.config.IntentDetectionConfig` | 公开旧版 intent detection 配置模型 | Java `legacy.config` 无此类型 | 旧配置对象装配无法直接迁移 |
| `P1` | `legacy.config.MemoryConfig` | 公开旧版 memory 配置模型 | Java `legacy.config` 无此类型 | 旧配置对象装配无法直接迁移 |
| `P2` | `legacy.agent.BaseAgent.config()` / `context_engine` | Python legacy base 暴露配置包装器与 context engine | Java `legacy.BaseAgent` 仅保留 `getAgentConfig()`，无 config wrapper / context engine 公开入口 | 旧调用方无法沿用原有读取方式 |
| `P2` | `legacy.agent.BaseAgent.add_prompt()` | 旧版 BaseAgent 可动态追加 prompt | Java `legacy.BaseAgent` 无对位方法 | 动态 prompt 追加 API 缺失 |
| `P2` | `legacy.agent.BaseAgent.remove_workflows()` | 旧版 BaseAgent 支持按 `(id, version)` 删除 workflow | Java `legacy.BaseAgent` 无对位方法 | 旧 workflow 动态解绑 API 缺失 |
| `P2` | `legacy.agent.BaseAgent.bind_workflows()` | `add_workflows()` 的兼容别名 | Java `legacy.BaseAgent` 无对位别名 | 少量旧代码需要改调用名 |
| `P2` | `legacy.agent.BaseAgent.add_plugins()` | 旧版 BaseAgent 支持写入 plugin schema | Java `legacy.BaseAgent` 无对位方法 | 旧 plugin schema 装配流程缺失 |
| `P2` | `legacy.react_agent.LegacyReActAgent.call_model()` | legacy ReActAgent 公开单独模型调用步骤 | Java `legacy.LegacyReActAgent` 仅保留 `invoke/stream` 和静态工厂 | 依赖分步调用 legacy ReAct 流程的代码无法直接迁移 |
| `P2` | `legacy.__all__` 兼容别名层 | `LegacyBaseAgent`、deprecated wrapper、统一导出 | Java 没有 facade/alias 层，只能直接引用具体类 | 兼容体验弱于 Python，但属于兼容层缺口而非核心实现缺陷 |
| `P2` | `skills.SkillManager.register(Path | List[Path], session_id, overwrite)` | 接受 `Path` 或路径列表，并通过 `sys_operation.fs()` 访问文件系统 | Java 仅公开 `register(String, String, boolean)` / `register(String)`，且直接使用本地文件系统 | 远程/抽象文件系统、批量 Path 注册语义仍未对齐 |
| `P2` | `skills.Skill.__repr__()` | 公开摘要化表示 | Java 仅有 `toString()` | 调试输出粒度不完全一致 |
| `P3` | `legacy.AgentConfig.workflows` | `List[WorkflowSchema | WorkflowCard]` | Java 仅 `List<WorkflowSchema>` | 若 legacy 调用方直接写入 `WorkflowCard`，需要自行转换 |
| `P3` | `legacy.LegacyReActAgentConfig.agent_memory_config` | 强类型 `AgentMemoryConfig` | Java 退化为 `Map<String,Object>` | 强类型配置约束弱化 |

## 建议优先级

1. 先补新版 API 缺口:
   - `BaseAgent.registerSkill(...)`
   - `BaseAgent.registerRemoteSkills(...)`
   - `AgentCallbackContext.lifecycle(...)`
   - `AgentRail.skills`
   - `GitHubError` 与 `RemoteSkillUtil.searchGitHubForSkills(...)`
2. 再补高价值 legacy 兼容件:
   - `WorkflowFactory`
   - `workflowProvider(...)`
   - `AgentSession`
   - `LLMCallConfig` / `IntentDetectionConfig` / `MemoryConfig`
3. 最后处理低风险兼容差异:
   - `legacy.BaseAgent` 的附加便捷方法
   - `SkillManager` 的 `Path/List[Path]` 重载
   - `Skill.__repr__()` 等调试友好接口

## 不建议按缺陷处理的差异

- Java 不提供 Python 式 `__all__`、`__getattr__`、deprecated wrapper，这是语言层门面差异。
- Python `async` API 迁移为 Java 同步 API，是 singleagent 当前 Java 运行时的统一设计，不宜单独视为缺陷。
- `rail(...)` 装饰器在 Java 中改成 `RailExecutor.execute(...)`，属于语法载体变化，不是功能缺失。

## 小结

- 现在的 singleagent Java 版已经能承载新版主链路和大部分常用扩展点。
- 真正尚未补齐的部分，更多集中在“便捷入口”和“legacy 兼容层”而不是主运行链路。
- 如果后续目标是严格对齐 Python 公开 API，优先从 `BaseAgent`、`rail`、`RemoteSkillUtil` 和 `legacy` facade 下手，收益最高。