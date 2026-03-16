# single_agent 模块 Python / Java API 映射

## 对照范围

- Python: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\single_agent`
- Java: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\singleagent`
- 统计口径:
  - Python 统计包级导出、公共类、公共顶层函数、非 `_` 公共方法
  - Java 统计 `public`/`protected` 类型、`public` 方法、公开静态工厂/工具方法
- 映射约定:
  - `snake_case -> camelCase`
  - `property` / Pydantic 字段 -> getter / setter / builder 字段
  - `async` -> 同步方法
  - Python 装饰器 / context manager -> Java 显式执行器 / `try/finally`
- 默认不单独记为缺漏:
  - Python `__all__` / `__getattr__` 包门面
  - Java Lombok 自动生成的 `getter/setter/equals/hashCode`
  - 仅因语言差异而产生的类型别名差异

## 总体结论

- single_agent 新版主链路已经基本对齐: `AbilityManager`、`AgentCallbackManager`、`BaseAgent`、`ControllerAgent`、`ReActAgent`、`ReActAgentEvolve`、`rail` 数据类型、`schema`、`skills` 主干都能找到 Java 对位实现。
- Java 为适配同步运行时，对 Python 的 `async`、`@rail` 装饰器、`property`、包级懒导出做了显式化改写，整体可视为“结构对齐、语言适配实现”。
- 当前仍有一批未完全对齐点，主要集中在 `BaseAgent` 技能便捷入口、`AgentCallbackContext.lifecycle()`、`AgentRail.skills`、`RemoteSkillUtil` 的公开搜索 API，以及 `legacy` 包若干兼容层 API；详见 `docs/FIXED/single_agent_fixed.md`。

## 1. 包级导出与门面

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `single_agent.__all__ = [AgentCard, ReActAgent, ReActAgentConfig, ReActAgentEvolve, Session, create_agent_session, BaseAgent, AbilityManager, LegacyBaseAgent]` | 无单一 package facade；分别使用 `singleagent.*`、`singleagent.agents.*`、`singleagent.schema.AgentCard`、`core.session.*`、`singleagent.legacy.BaseAgent` | 包门面导出 -> 直接导入具体类 | 适配映射 | Java 不提供 Python 风格 `__all__`/`__getattr__` |
| `single_agent.__getattr__("LegacyBaseAgent")` | `legacy.BaseAgent` | 懒导出旧类 -> 直接使用 legacy 包 | 部分映射 | Java 没有兼容别名 `LegacyBaseAgent` |
| `skills.__all__ = [SkillUtil, SkillManager, GitHubTree, RemoteSkillUtil]` | `skills.SkillUtil`、`SkillManager`、`GitHubTree`、`RemoteSkillUtil` | 包导出 -> 直接导入类型 | 完全映射 | Java 还额外公开 `Skill` |
| `schema.__init__` 空模块 | `schema` 包 | 无额外门面 | 完全映射 | 两侧都没有额外导出行为 |
| `rail.__all__` 导出 `InvokeInputs/ModelCallInputs/ToolCallInputs/RetryRequest/AgentCallbackEvent/AgentCallbackContext/AgentRail/AgentCallback/SyncAgentCallback/AnyAgentCallback/rail` | `rail` 包下同名数据类/枚举/接口 + `RailExecutor` | 类型导出 1:1；`rail` 装饰器 -> `RailExecutor.execute(...)` | 适配映射 | `SyncAgentCallback`/`AnyAgentCallback` 变成 Java 函数式接口/`Consumer` |

## 2. 核心类型

### 2.1 `AbilityExecutionError`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AbilityExecutionError(status, msg=None, details=None, cause=None, tool_message=None, **kwargs)` | `AbilityExecutionError(StatusCode, String, ToolMessage)` / `AbilityExecutionError(StatusCode, String, Throwable, ToolMessage)` | `tool_message -> getToolMessage()` | 部分映射 | Java 保留核心异常与 `toolMessage`，但没有 Python 那样公开的 `details/**kwargs` 构造入口 |

### 2.2 `AbilityManager`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `add(ability)` | `add(Object ability)` | 同名语义映射 | 完全映射 | 两侧都支持单个或列表能力 |
| `remove(name)` / `remove([names])` | `remove(String)` / `remove(List<String>)` | 同名语义映射 | 完全映射 | MCP server 删除时都会联动清理其工具缓存 |
| `get(name)` | `get(String)` | 同名语义映射 | 完全映射 | - |
| `list()` | `list()` | 同名语义映射 | 完全映射 | - |
| `list_tool_info(names=None, mcp_server_name=None)` | `listToolInfo()` / `listToolInfo(List<String>, String)` | Python 可选参数 -> Java 重载 + 显式参数 | 完全映射 | Java 还会缓存拉回的 MCP `ToolCard` |
| `execute(ctx, tool_call, session, tag=None)` | `execute(AgentCallbackContext, Object, Session, String)` | 同名语义映射 | 完全映射 | 都会为每个 tool call 走 tool rail 生命周期 |
| `_execute_single_tool_call(...)` | `executeSingleToolCall(ToolCall, Session, String)` | 内部私有方法 -> Java 公开方法 | 适配映射 | Java 公开度更高，便于集成 `ToolCallOperator` |
| 无 Python 对位 | `setToolDescription(String, String)` | Java-only 扩展 | Java 扩展 | 给 `ToolRegistry` 使用 |
| 无 Python 对位 | `executeAsToolExecutor(Object, Session)` | Java-only 扩展 | Java 扩展 | 给 `ToolCallOperator`/operator 体系使用 |

### 2.3 `AgentCallbackManager`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `register_callback(event, callback, priority=100)` | `registerCallback(AgentCallbackEvent, Consumer<AgentCallbackContext>, int)` / 重载默认优先级 | 同名语义映射 | 完全映射 | Python 同时支持 sync/async callback；Java 统一为同步 `Consumer` |
| `register_rail(rail, agent)` | `registerRail(AgentRail, Object)` | 同名语义映射 | 完全映射 | 两侧都负责注册 rail callback 与 rail 附带工具 |
| `unregister_rail(rail, agent)` | `unregisterRail(AgentRail, Object)` | 同名语义映射 | 完全映射 | Java 已补齐“移除 rail 回调 + tool”双重注销 |
| `unregister(event, callback)` | `unregister(AgentCallbackEvent, Consumer<AgentCallbackContext>)` | 同名语义映射 | 完全映射 | - |
| `clear(event=None)` | `clear(AgentCallbackEvent)` | `None -> null` | 完全映射 | - |
| `has_hooks(event)` | `hasHooks(AgentCallbackEvent)` | 同名语义映射 | 完全映射 | - |
| `execute(event, ctx)` | `execute(AgentCallbackEvent, AgentCallbackContext)` | 同名语义映射 | 完全映射 | 两侧都统一走 agent-id 前缀事件名 |

### 2.4 `BaseAgent`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(card)` | `BaseAgent(AgentCard)` | 构造语义一致 | 完全映射 | 都创建 `AbilityManager`、`AgentCallbackManager` 并延迟初始化 skill |
| `lazy_init_skill()` | `lazyInitSkill()` | 同名语义映射 | 完全映射 | 都基于配置中的 `sys_operation_id` 初始化/刷新 `SkillUtil` |
| `configure(config)` | `configure(Object)` | 抽象配置接口 | 完全映射 | - |
| `config` property | `getConfig()` | 属性 -> getter | 适配映射 | - |
| `ability_manager` property | `getAbilityManager()` | 属性 -> getter | 完全映射 | - |
| `agent_callback_manager` property | `getAgentCallbackManager()` | 属性 -> getter | 完全映射 | - |
| `card` attribute | `getCard()` | 字段 -> getter | 完全映射 | - |
| `_skill_util` | `getSkillUtil()` / `setSkillUtil()` | 内部字段 -> getter/setter | 适配映射 | Java 暴露度更高 |
| `register_skill(skill_path)` | 无 BaseAgent 同名公开方法 | 可由 `getSkillUtil().registerSkills(...)` 间接完成 | 部分映射 | Java 缺少 BaseAgent 级便捷入口 |
| `register_remote_skills(skills_dir, github_tree, token="")` | 无 BaseAgent 同名公开方法 | 可由 `getSkillUtil().registerRemoteSkills(...)` 间接完成 | 部分映射 | Java 缺少 BaseAgent 级便捷入口 |
| `register_callback(event, callback, priority=100)` | `registerCallback(event, callback, priority)` | 同名语义映射 | 完全映射 | - |
| `register_rail(rail)` | `registerRail(AgentRail)` | 同名语义映射 | 完全映射 | - |
| `unregister_rail(rail)` | `unregisterRail(AgentRail)` | 同名语义映射 | 完全映射 | - |
| `_execute_callbacks(event, inputs, session=None, context=None, **kwargs)` | `fireCallbackEvent(event, ctx)` + 由调用方构建 `AgentCallbackContext` | 内部辅助函数 -> 显式上下文触发 | 适配映射 | Java 没有独立 `_executeCallbacks(...)` 辅助方法 |
| `invoke(inputs, session=None)` | `invoke(Object, Session)` | 抽象调用接口 | 完全映射 | - |
| `stream(inputs, session=None, stream_modes=None)` | `stream(Object, Session, List<StreamMode>)` | 抽象流式接口 | 完全映射 | - |

### 2.5 `ControllerAgent`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(card, controller, config=None)` | `ControllerAgent(AgentCard, Controller)` / `ControllerAgent(AgentCard, Controller, ControllerConfig)` | 默认 config -> 重载构造器 | 完全映射 | - |
| `_create_default_config()` | 构造器内 `new ControllerConfig()` | 内部默认配置工厂 | 适配映射 | Java 未单独暴露该内部方法 |
| `configure(dict | BaseModel)` | `configure(Object)` | 同名语义映射 | 完全映射 | Java 支持 `ControllerConfig` 或 `Map` merge |
| `controller` property | `getController()` | 属性 -> getter | 完全映射 | - |
| `context_engine` attribute | `getContextEngine()` | 字段 -> getter | 完全映射 | Java 公开度更高 |
| `release_session(session_id)` | `releaseSession(String)` | 同名语义映射 | 完全映射 | Java 已补齐先退订 event queue 再释放 Runner session |
| `invoke(inputs, session, **kwargs)` | `invoke(Object, Session)` | 同名主语义映射 | 完全映射 | Java 统一转成 `InputEvent.fromUserInput(...)` |
| `stream(inputs, session, stream_modes=None, **kwargs)` | `stream(Object, Session, List<StreamMode>)` | 同名主语义映射 | 完全映射 | - |

## 3. agents 子包

### 3.1 `ReActAgentConfig`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 字段 `mem_scope_id/model_name/model_provider/api_key/api_base/prompt_template_name/prompt_template/max_iterations/model_client_config/model_config_obj/sys_operation_id/context_engine_config/context_processors` | 字段 `memScopeId/modelName/modelProvider/apiKey/apiBase/promptTemplateName/promptTemplate/maxIterations/modelClientConfig/modelConfigObj/sysOperationId/contextEngineConfig/contextProcessors` | `snake_case -> camelCase` | 完全映射 | `context_processors: List[Tuple[str, BaseModel]]` 在 Java 中弱化为 `List<Object>` |
| `configure_model()` | `configureModel()` | 同名语义映射 | 完全映射 | - |
| `configure_model_provider()` | `configureModelProvider()` | 同名语义映射 | 完全映射 | - |
| `configure_prompt()` | `configurePrompt()` | 同名语义映射 | 完全映射 | - |
| `configure_prompt_template()` | `configurePromptTemplate()` | 同名语义映射 | 完全映射 | - |
| `configure_context_engine(max_context_message_num=200, default_window_round_num=10, enable_reload=False)` | `configureContextEngine(Integer, Integer, boolean)` | 默认参数 -> 可空参数 + 默认值回填 | 完全映射 | - |
| `configure_mem_scope()` | `configureMemScope()` | 同名语义映射 | 完全映射 | - |
| `configure_max_iterations()` | `configureMaxIterations()` | 同名语义映射 | 完全映射 | - |
| `configure_model_client()` | `configureModelClient()` | 同名语义映射 | 完全映射 | - |
| `configure_context_processors()` | `configureContextProcessors()` | 同名语义映射 | 完全映射 | - |

### 3.2 `ReActAgent`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(card)` | `ReActAgent(AgentCard)` | 构造语义一致 | 完全映射 | 都初始化默认配置、`ContextEngine`、memory scope、LLM lazy state |
| `_create_default_config()` | `createDefaultConfig()` | 内部配置工厂 | 适配映射 | Java 公开为 `protected` |
| `configure(config)` | `configure(Object)` | 同名语义映射 | 完全映射 | - |
| `config` property | `getConfig()` | 属性 -> getter | 完全映射 | - |
| `context_engine` attribute | `getContextEngine()` | 字段 -> getter | 完全映射 | - |
| `_get_llm()` | `getLlm()` | 内部 lazy getter | 适配映射 | Java 公开为 `protected` |
| `_call_model(ctx, context, system_messages, tools)` | `callModel(...)` | 同名内部语义映射 | 完全映射 | - |
| `_railed_model_call(ctx)` + `@rail(...)` | `railedModelCall(ctx)` + `RailExecutor.execute(...)` | 装饰器 -> 显式执行器 | 适配映射 | `BEFORE/AFTER_MODEL_CALL`、`ON_MODEL_EXCEPTION` 生命周期一致 |
| `_execute_tool_call(ctx, tool_calls, session, context)` | `executeToolCall(...)` | 同名内部语义映射 | 完全映射 | - |
| `_warn_missing_skill_read_file_tool()` | `warnMissingSkillReadFileTool()` | 同名内部语义映射 | 完全映射 | - |
| `_init_context(session)` | `initContext(Session)` | 同名内部语义映射 | 完全映射 | Java 已补齐 reload tool 注册/移除 |
| `invoke(inputs, session=None)` | `invoke(Object, Session)` | 同名主语义映射 | 完全映射 | Java 已补齐 `AFTER_INVOKE` 前恢复 `InvokeInputs` |
| `stream(inputs, session=None, stream_modes=None)` | `stream(Object, Session, List<StreamMode>)` | 同名主语义映射 | 完全映射 | Python 异步迭代 -> Java `Iterator<Object>` |

### 3.3 `ReActAgentEvolve`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(card)` | `ReActAgentEvolve(AgentCard)` | 构造语义一致 | 完全映射 | 都初始化 `LLMCallOperator` / `ToolCallOperator` 相关状态 |
| `configure(config)` | `configure(Object)` | 同名语义映射 | 完全映射 | - |
| `_normalize_user_input(inputs)` | `normalizeUserInput(Object)` | 同名内部语义映射 | 完全映射 | Java 为私有静态方法 |
| `_on_llm_parameter_updated(target, value)` | `onLlmParameterUpdated(String, Object)` | 同名内部语义映射 | 完全映射 | - |
| `_resolve_llm_model_name()` | `resolveModelName()` | 同名内部语义映射 | 完全映射 | - |
| `_get_llm_op()` | `getLlmOp()` | 同名内部语义映射 | 完全映射 | - |
| `_get_llm()` | `getLlm()` | 同名内部语义映射 | 适配映射 | Java 为 `protected` |
| `_get_skill_messages()` | `getSkillMessages()` | 同名内部语义映射 | 完全映射 | - |
| `get_operators()` | `getOperators()` | 同名语义映射 | 完全映射 | - |
| `register_skill(skill_path)` | `registerSkill(Object)` | 同名语义映射 | 完全映射 | Python 是 `async`，Java 是同步 |
| `_init_context(session)` | `initContext(Session)` | 同名内部语义映射 | 完全映射 | Java 已补齐 reload tool 接入 |
| `_prepare_model_call(...)` | `prepareModelCall(...)` | 同名内部语义映射 | 完全映射 | - |
| `_railed_model_call(...)` | `railedModelCall(...)` | 同名内部语义映射 | 完全映射 | 仍通过 `RailExecutor` 触发生命周期 |
| `_prepare_tool_call(...)` | `prepareToolCall(...)` | 同名内部语义映射 | 完全映射 | - |
| `invoke(inputs, session=None)` | `invoke(Object, Session)` | 同名主语义映射 | 完全映射 | - |
| `stream(inputs, session=None, stream_modes=None)` | `stream(Object, Session, List<StreamMode>)` | 同名主语义映射 | 完全映射 | - |

## 4. rail 子包

### 4.1 数据类型与事件枚举

| Python API | Java API | 方法/字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `InvokeInputs(query, conversation_id=None, result=None)` | `InvokeInputs(query, conversationId, result)` | `conversation_id -> conversationId` | 完全映射 | - |
| `ModelCallInputs(messages=[], tools=None, response=None)` | `ModelCallInputs(messages, tools, response)` | 同名语义映射 | 完全映射 | `tools` 在 Java 中具体化为 `List<ToolInfo>` |
| `ToolCallInputs(tool_call=None, tool_name="", tool_args=None, tool_result=None, tool_msg=None)` | `ToolCallInputs(toolCall, toolName, toolArgs, toolResult, toolMsg)` | 同名语义映射 | 完全映射 | - |
| `RetryRequest(delay_seconds=0.0)` | `RetryRequest(delaySeconds)` | `delay_seconds -> delaySeconds` | 完全映射 | - |
| `EventInputs = Union[...]` | `EventInputs` marker interface | Python 联合类型 -> Java 标记接口 | 适配映射 | - |
| `AgentCallbackEvent` 8 个枚举值 | `AgentCallbackEvent` 8 个枚举值 | `.value -> getValue()/toString()` | 完全映射 | - |

### 4.2 `AgentCallbackContext`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 字段 `agent/event/inputs/config/session/context/extra/exception/retry_attempt` | 同名 builder 字段 + getter/setter | `retry_attempt -> retryAttempt` | 完全映射 | - |
| `fire(event)` | `fire(AgentCallbackEvent)` | 同名语义映射 | 完全映射 | - |
| `request_retry(delay_seconds=0.0)` | `requestRetry(double)` | 同名语义映射 | 完全映射 | - |
| `consume_retry_request()` | `consumeRetryRequest()` | 同名语义映射 | 完全映射 | - |
| `lifecycle(before, after)` | 无同名公开方法 | 由各调用点手工 `fire(before)` + `try/finally fire(after)` 实现 | 部分映射 | Java 缺少独立 context-manager 风格 API |

### 4.3 `AgentRail`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `priority` | `getPriority()/setPriority()` | 属性 -> getter/setter | 完全映射 | - |
| `tools` property | `getTools()` | 属性 -> getter | 完全映射 | - |
| `skills` property | 无同名公开字段/方法 | - | 部分映射 | Java 当前只保留 rail tools，没有保留 reserved skills 容器 |
| `before_invoke/after_invoke/before_model_call/after_model_call/on_model_exception/before_tool_call/after_tool_call/on_tool_exception` | `beforeInvoke/afterInvoke/beforeModelCall/afterModelCall/onModelException/beforeToolCall/afterToolCall/onToolException` | 8 个 hook 一一对应 | 完全映射 | - |
| `get_callbacks()` | `getCallbacks()` | 同名语义映射 | 完全映射 | 都只提取子类真正覆写的方法 |

### 4.4 `rail` 装饰器

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `rail(before=None, after=None, on_exception=None)` | `RailExecutor.execute(ctx, before, after, onException, body)` | 装饰器 -> 静态执行器 | 适配映射 | Java 无装饰器语法，但重试与 before/after/on_exception 语义已保留 |

## 5. schema 子包

| Python API | Java API | 方法/字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentCard(BaseCard)` | `AgentCard extends BaseCard` | 字段 `input_params/output_params -> inputParams/outputParams`；`tool_info() -> toolInfo()` | 部分映射 | Java 当前只接受 `Map<String,Object>`，Python 还接受 `Type[BaseModel]` |
| `Artifact` | `Artifact` | `artifactId/name/description/parts/metadata` 一一对应 | 完全映射 | `Part` 依赖已补齐 |
| `AgentResult` | `AgentResult` | `task_id -> taskId`；`sessionId/status/artifacts/metadata` 一一对应 | 完全映射 | `TaskStatus` 在 Java 中用 `controller.schema.TaskStatus` |

## 6. skills 子包

### 6.1 `Skill`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Skill(BaseModel)` 字段 `name/description/directory` | `Skill` 字段 `name/description/directory` | 同名语义映射 | 完全映射 | Python `directory` 是 `Path`，Java 是 `String` |
| `__str__()` | `toString()` | 同名语义映射 | 完全映射 | - |
| `__repr__()` | 无同名公开方法 | - | 部分映射 | Java 未单独提供 Python 风格摘要表示 |

### 6.2 `SkillManager`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(sys_operation_id)` | `SkillManager(String sysOperationId)` | 构造语义一致 | 完全映射 | - |
| `set_sys_operation_id()` | `setSysOperationId()` | 同名语义映射 | 完全映射 | Java 还公开 `getSysOperationId()` |
| `register(skill_path, session_id=None, overwrite=False)` | `register(String skillPath, String sessionId, boolean overwrite)` / `register(String)` | 同名主语义映射 | 部分映射 | Python 支持 `Path | List[Path]` 且通过 `sys_operation.fs()` 读文件；Java 目前走本地文件系统字符串路径 |
| `unregister(name)` | `unregister(String)` | 同名语义映射 | 完全映射 | - |
| `get(name)` | `get(String)` | 同名语义映射 | 完全映射 | - |
| `get_all()` | `getAll()` | 同名语义映射 | 完全映射 | - |
| `get_names()` | `getNames()` | 同名语义映射 | 完全映射 | - |
| `has(name)` | `has(String)` | 同名语义映射 | 完全映射 | - |
| `clear()` | `clear()` | 同名语义映射 | 完全映射 | - |
| `count()` | `count()` | 同名语义映射 | 完全映射 | - |
| `_load_yaml/_load_description/_create_skill_from_path` | `loadDescription()/createSkillFromPath()/registerRoot()` 等私有方法 | 内部实现改写 | 适配映射 | Java 使用本地文件系统与轻量 YAML front matter 解析 |

### 6.3 `SkillUtil`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(sys_operation_id)` | `SkillUtil(String sysOperationId)` | 构造语义一致 | 完全映射 | - |
| `set_sys_operation_id()` | `setSysOperationId()` | 同名语义映射 | 完全映射 | - |
| `skill_manager` property | `getSkillManager()` | 属性 -> getter | 完全映射 | - |
| `remote_skill_util` property | `getRemoteSkillUtil()` | 属性 -> getter | 完全映射 | - |
| `register_skills(skill_path, agent, session_id=None)` | `registerSkills(Object skillPath, BaseAgent agent)` | 同名主语义映射 | 完全映射 | Java 忽略 `sessionId`，直接委托 `SkillManager` |
| `register_remote_skills(skills_dir, github_tree, token="")` | `registerRemoteSkills(String, GitHubTree, String)` | 同名语义映射 | 完全映射 | - |
| `has_skill()` | `hasSkill()` | 同名语义映射 | 完全映射 | - |
| `get_skill_prompt()` | `getSkillPrompt()` | 同名语义映射 | 完全映射 | - |

### 6.4 `GitHubTree` / `RemoteSkillUtil`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `GitHubError` | 无同名公开类型 | - | 缺失 | Java 当前直接抛 `RuntimeException` |
| `GitHubTree(repo_owner, repo_name, tree_ref="HEAD", directory="")` | `GitHubTree(repoOwner, repoName)` / 全参构造 | 同名语义映射 | 完全映射 | `Path directory` 在 Java 中变为 `String directory` |
| `clone()` | `copy()` | 同名语义映射 | 适配映射 | Java 避免与 `Object.clone()` 语义混淆 |
| `RemoteSkillUtil.sys_operation_id` property | `getSysOperationId()/setSysOperationId()` | 属性 -> getter/setter | 完全映射 | - |
| `download_file_from_github(tree, file_path, token=None)` | `downloadFileFromGitHub(GitHubTree, String, String)` | 同名语义映射 | 完全映射 | - |
| `search_github_for_skills(tree, token=None)` | 无同名公开方法 | 仅保留私有 `searchGitHubForSkills(...)` | 部分映射 | Java 缺少外部可直接复用的搜索 API |
| `_list_github_files/_recursively_list_github_files()` | 无同名公开方法 | 仅保留私有 `listGitHubFiles(...)` / `recursivelyListGitHubFiles(...)` | 适配映射 | 内部能力存在，但未作为公开 API 暴露 |
| `upload_skill_from_github(tree, skills_dir="", token=None)` | `uploadSkillFromGitHub(GitHubTree, String, String)` | 同名语义映射 | 完全映射 | Java 已补齐真实 GitHub 搜索、下载、写盘逻辑 |

## 7. legacy 子包

### 7.1 包级兼容层

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.__all__` 导出 `LegacyReActAgent/create_react_agent_config/LegacyBaseAgent/ControllerAgent/AgentSession/WorkflowFactory/workflow_provider/AgentConfig/LLMCallConfig/IntentDetectionConfig/ConstrainConfig/DefaultResponse/WorkflowAgentConfig/MemoryConfig/LegacyReActAgentConfig/WorkflowSchema/PluginSchema` | `legacy.*` 中只公开 `BaseAgent`、`ControllerAgent`、`LegacyReActAgent`、`ReActAgent`、部分 config/schema 类型 | Python 兼容层 -> Java 精简 legacy 包 | 部分映射 | Java 没有 Python 那层 deprecated alias/facade |
| `_deprecated_class(...)` | 无同名机制 | - | 适配映射 | Java 未实现实例化即发出 deprecation warning 的包装层 |

### 7.2 legacy 基础类与 agent

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.agent.BaseAgent` | `legacy.BaseAgent` | `getAgentConfig()` 对位 `agent_config/config wrapper`；`add_tools -> addTools()`；`add_workflows -> addWorkflows()`；`clear_session -> clearSession()`；`invoke/stream` 抽象接口对齐 | 部分映射 | Java 缺少 `config()`、`context_engine`、`add_prompt()`、`remove_workflows()`、`bind_workflows()`、`add_plugins()` |
| `WorkflowFactory` | 无同名公开类型 | - | 缺失 | Java legacy 不支持 Python 的 workflow provider 工厂类型 |
| `workflow_provider(...)` | 无同名公开函数 | - | 缺失 | Java legacy 不提供装饰器式 provider 工厂 |
| `legacy.agent.ControllerAgent` | `legacy.ControllerAgent` | 构造器、`controller` getter/setter、`invoke`、`stream` 一一对应 | 完全映射 | - |
| `legacy.react_agent.AgentSession` | 无同名公开类型 | - | 缺失 | Java 直接使用 `AgentSessionApi` |
| `legacy.react_agent.TaskSession` | 无同名公开类型 | - | 缺失 | Python 内部兼容会话包装未迁移 |
| `legacy.react_agent.LegacyReActAgent` | `legacy.LegacyReActAgent` | 构造器、`addTools/addWorkflows`、`invoke`、`stream`、`create_react_agent_config -> createReActAgentConfig(...)` | 部分映射 | Java 用现代 `ReActAgent` 作为 delegate，没有公开 Python `call_model()` |
| `legacy.react_agent.ReActAgentConfig = LegacyReActAgentConfig` | `legacy.config.LegacyReActAgentConfig` | 兼容别名 -> 直接使用 legacy 配置类 | 适配映射 | Java 无同名别名 |
| `legacy.ReActAgent` 兼容类 | `legacy.ReActAgent extends LegacyReActAgent` | 同名兼容类 | 完全映射 | - |

### 7.3 legacy config / schema

| Python API | Java API | 方法/字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentConfig` | `legacy.config.AgentConfig` | 字段 `id/version/description/controller_type/workflows/model/tools` 一一对应 | 部分映射 | Python `workflows` 支持 `WorkflowSchema | WorkflowCard`，Java 仅保留 `WorkflowSchema` |
| `LLMCallConfig` | 无同名公开类型 | - | 缺失 | Java legacy 未保留该兼容配置类 |
| `IntentDetectionConfig` | 无同名公开类型 | - | 缺失 | Java legacy 未保留该兼容配置类 |
| `ConstrainConfig` | `legacy.config.ConstrainConfig` | 字段 `reserved_max_chat_rounds/max_iteration -> reservedMaxChatRounds/maxIteration` | 完全映射 | - |
| `DefaultResponse` | `legacy.config.DefaultResponse` | 字段 `type/text` 一一对应 | 完全映射 | Java 未把 `type` 收窄到 Python `Literal` |
| `MemoryConfig` | 无同名公开类型 | - | 缺失 | Java legacy 未保留该兼容配置类 |
| `WorkflowAgentConfig` | `legacy.config.WorkflowAgentConfig` | `controller_type/start_workflow/end_workflow/global_variables/global_params/constrain/default_response` 一一对应 | 完全映射 | - |
| `LegacyReActAgentConfig` | `legacy.config.LegacyReActAgentConfig` | `controller_type/prompt_template_name/prompt_template/constrain/plugins/memory_scope_id/agent_memory_config/context_window_limit` 基本对齐 | 部分映射 | `agent_memory_config` 在 Java 中退化为 `Map<String,Object>`，不再保留 Python 的 `AgentMemoryConfig` 强类型 |
| `WorkflowSchema` | `legacy.schema.WorkflowSchema` | 字段 `id/name/description/version/inputs` 一一对应 | 完全映射 | - |
| `PluginSchema` | `legacy.schema.PluginSchema` | 字段 `id/version/name/description/inputs/plugin_id -> pluginId` | 完全映射 | - |

## 8. 结论

- 新版 single_agent 主干 API 在 Java 中已经具备可用的类型对位与主流程语义，尤其是 `ReActAgent`、`ReActAgentEvolve`、`AbilityManager`、`AgentCallbackManager`、`rail` 生命周期、`schema` 与 `skills` 主体已经成型。
- 主要差异集中在两类:
  - 语言适配: 包级导出、装饰器、异步、属性访问器。
  - 兼容层收缩: `legacy` 包没有完整复制 Python 的兼容 facade 与所有旧配置类型。
- 当前仍需补齐的公开 API / 重要语义差异已单列在 `docs/FIXED/single_agent_fixed.md`，可直接作为后续修补清单。