# Session 模块 Python / Java API 映射

## 对照范围

- Python：`agent-core-python/openjiuwen/core/session/**`
- Java：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/session/**`
- 统计口径：
  - Python 统计模块级公开函数、公开类、类上的公开方法
  - Java 统计 `public` 类型、`public` 方法，以及承担 Python 语义的桥接/替代 API
  - 对 Python `async` API，若 Java 以同步方式承载且语义一致，记为“已映射但执行模型变化”

## 复核结论

- Java 版 `session` 主体框架已经覆盖了 Python 版的大部分核心能力：外部 Session API、internal session、state、stream、callback、interaction、基础 checkpointer、tracer 主干都已落地。
- 需要特别区分三类情况：
  - 已对齐：类与方法都存在，仅命名从 `snake_case` 变为 `camelCase`
  - 语义对齐但入口不同：例如 Python async stream / interaction，在 Java 中改为同步 iterator、consumer 或同步方法
  - 真实缺口：Python 有公开 API 或完整子体系，Java 仍未提供同等入口或只保留了部分内部实现

## 1. 模块级映射总览

| Python 模块 | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `agent_group.py` | `AgentGroupSessionApi` | 基本映射 | 外部 AgentGroup Session API 已有；Java 额外暴露 `getInner()`。 |
| `agent.py` | `AgentSessionApi` | 基本映射 | 外部 Agent Session API 已齐；async stream / interaction 改为同步。 |
| `workflow.py` | `WorkflowSessionApi` | 完全映射 | 工厂函数改为静态 `create(...)`。 |
| `node.py` | `NodeSessionApi` | 部分映射 | 主 API 已有；依赖的 `NodeSession.skip_trace()` 未补齐。 |
| `session.py` | `BaseSession` / `ProxySession` / `Session` | 部分映射 | `BaseSession`、`ProxySession` 已有；Python 废弃类 `Session` 在 Java 中变为最小兼容接口。 |
| `internal/agent.py` | `internal/AgentSession` | 基本映射 | 主生命周期对象已映射。 |
| `internal/workflow.py` | `internal/WorkflowSession` / `NodeSession` / `SubWorkflowSession` | 部分映射 | 主类已齐；`skip_trace`、`SubWorkflowSession.close()` 等细节仍有差异。 |
| `internal/wrapper.py` | `internal/WrappedSession` / `StateSession` / `RouterSession` | 基本映射 | 大部分方法已齐；少量 helper 为 no-op。 |
| `interaction/*` | `interaction/*` | 基本映射 | async 交互改为同步阻塞 / 直接抛异常。 |
| `state/*` | `state/*` | 基本映射 | 主状态接口与内存实现已齐；`Transformer` 改为 `Function`。 |
| `stream/*` | `stream/*` | 基本映射 | Python 的三个具体 writer 子类在 Java 中收敛为泛型 `StreamWriter`。 |
| `callback/*` | `callback/*` | 基本映射 | Python `trigger_event` 装饰器改为 Java `@TriggerEvent` 注解。 |
| `checkpointer/base.py` | `checkpointer/Checkpointer` / `Storage` | 完全映射 | 常量和 key builder 已补齐。 |
| `checkpointer/checkpointer.py` | `CheckpointerFactory` / `CheckpointerProvider` | 部分映射 | 工厂主体已齐，但 `CheckpointerConfig` 未提供 Java 对位类型。 |
| `checkpointer/inmemory.py` | `InMemoryCheckpointer` | 基本映射 | 内存 checkpoint 主逻辑已落地。 |
| `checkpointer/persistence.py` | 无同层实现 | 缺失 | Python 持久化 checkpointer 子体系未移植。 |
| `config/base.py` | `config/Config` | 部分映射 | 主配置类已齐；`workflow_session_vars` contextvars 未对位。 |
| `constants.py` | `constants/SessionConstants` | 完全映射 | Java 还额外补了 `LOOP_ID`、`INDEX`。 |
| `store.py` | `store/Store` / `FileStore` / `MemoryStore` | 完全映射 | - |
| `tracer/data.py` | `tracer/InvokeType` / `NodeStatus` | 完全映射 | - |
| `tracer/span.py` | `tracer/Span` / `TraceAgentSpan` / `TraceWorkflowSpan` / `SpanManager` | 基本映射 | span 数据结构已齐。 |
| `tracer/handler.py` | `TraceBaseHandler` / `TraceAgentHandler` / `TraceWorkflowHandler` / `TracerHandlerName` | 部分映射 | 主事件处理链路已齐，但 `on_llm_request` 未补。 |
| `tracer/tracer.py` | `tracer/Tracer` | 基本映射 | `trigger(...)` 已有；`sync_trigger(...)` 被同步 trigger 语义吸收。 |
| `tracer/workflow_tracer.py` | `tracer/TracerWorkflowUtils` | 完全映射 | - |
| `tracer/decorator.py` | 无同层实现 | 缺失 | Python 的 trace 装饰器包装函数族未移植。 |
| `utils.py` | `utils/SessionUtils` | 部分映射 | 主路径/嵌套结构工具已齐；`create_wrapper_class` 等 helper 未全部公开对位。 |

## 2. 顶层外部 Session API

### 2.1 `agent_group.Session` -> `AgentGroupSessionApi`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Session.__init__(session_id=None, envs=None)` | `AgentGroupSessionApi(...)` | 完全映射 | 同样支持空 `session_id` 自动生成。 |
| `get_session_id()` | `getSessionId()` | 完全映射 | - |
| `get_env(key, default)` | `getEnv(key, defaultValue)` | 完全映射 | - |
| `create_agent_group_session(...)` | `AgentGroupSessionApi.create(...)` | 完全映射 | 模块级工厂转为静态工厂。 |
| 无 | `getInner()` | Java 扩展 | 返回内部 `AgentSession`。 |

### 2.2 `agent.Session` -> `AgentSessionApi`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Session.__init__(session_id=None, envs=None, card=None)` | `AgentSessionApi(...)` | 完全映射 | - |
| `get_session_id()` | `getSessionId()` | 完全映射 | - |
| `get_env(key, default=None)` | `getEnv(key)` / `getEnv(key, defaultValue)` | 完全映射 | Java 补了重载。 |
| `get_envs()` | `getEnvs()` | 完全映射 | - |
| `get_agent_id()` | `getAgentId()` | 完全映射 | - |
| `get_agent_name()` | `getAgentName()` | Java 扩展 | Python 类本身有同名 getter；Java 也暴露。 |
| `get_agent_description()` | `getAgentDescription()` | Java 扩展 | Python 类本身有同名 getter；Java 也暴露。 |
| `update_state(data)` | `updateState(data)` | 完全映射 | 更新全局 state。 |
| `get_state(key=None)` | `getState(Object)` / `getState(String)` | 完全映射 | - |
| `dump_state()` | `dumpState()` | 完全映射 | - |
| `async write_stream(data)` | `writeStream(data)` | 语义映射 | async -> sync。 |
| `async write_custom_stream(data)` | `writeCustomStream(data)` | 语义映射 | async -> sync。 |
| `stream_iterator()` | `streamIterator()` | 语义映射 | Python 返回 `AsyncIterator`，Java 返回阻塞 `Iterator`。 |
| 无 | `streamOutput(Consumer<Object>)` | Java 扩展 | callback 风格消费流。 |
| `async pre_run(**kwargs)` | `preRun(inputs)` | 语义映射 | Java 直接接收 inputs。 |
| `async post_run()` | `postRun()` | 语义映射 | - |
| `create_workflow_session()` | `createWorkflowSession()` | 完全映射 | - |
| `async interact(value)` | `interact(value)` | 语义映射 | Java 以同步异常/中断方式承载。 |
| `create_agent_session(...)` | `AgentSessionApi.create(...)` | 完全映射 | - |
| 无 | `getInner()` | Java 扩展 | 返回内部 `AgentSession`。 |

### 2.3 `workflow.Session` -> `WorkflowSessionApi`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Session.__init__(parent=None, session_id=None, envs=None)` | `WorkflowSessionApi(...)` | 完全映射 | - |
| `get_callback_manager()` | `getCallbackManager()` | 完全映射 | - |
| `get_session_id()` | `getSessionId()` | 完全映射 | - |
| `get_envs()` | `getEnvs()` | 完全映射 | - |
| `get_parent()` | `getParent()` | 完全映射 | - |
| `set_workflow_card(card)` | `setWorkflowCard(card)` | 完全映射 | - |
| `get_workflow_card()` | `getWorkflowCard()` | 完全映射 | - |
| `create_workflow_session(...)` | `WorkflowSessionApi.create(...)` | 完全映射 | - |

### 2.4 `node.Session` -> `NodeSessionApi`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Session.__init__(session, stream_mode=False)` | `NodeSessionApi(session, streamMode)` | 完全映射 | - |
| `get_workflow_id()` | `getWorkflowId()` | 完全映射 | - |
| `get_component_id()` | `getComponentId()` | 完全映射 | - |
| `get_component_type()` | `getComponentType()` | 完全映射 | - |
| `get_component_descrip()` | `getComponentDescrip()` | 完全映射 | - |
| `async trace(data)` | `trace(data)` | 语义映射 | 但 Python 会先判断 `skip_trace()`，Java 缺这层控制。 |
| `async trace_error(error)` | `traceError(error)` | 语义映射 | 同上。 |
| `async interact(value)` | `interact(value)` | 语义映射 | 流式场景不支持交互。 |
| `get_executable_id()` | `getExecutableId()` | 完全映射 | - |
| `get_session_id()` | `getSessionId()` | 完全映射 | - |
| `update_state(data)` | `updateState(data)` | 完全映射 | - |
| `get_state(key=None)` | `getState(key)` | 完全映射 | - |
| `update_global_state(data)` | `updateGlobalState(data)` | 完全映射 | - |
| `get_global_state(key=None)` | `getGlobalState(key)` | 完全映射 | - |
| `dump_state()` | `dumpState()` | 完全映射 | - |
| `async write_stream(data)` | `writeStream(data)` | 语义映射 | - |
| `async write_custom_stream(data)` | `writeCustomStream(data)` | 语义映射 | - |
| `get_callback_manager()` | `getCallbackManager()` | 基本映射 | Java 返回 `Object`，Python 为具体 manager。 |
| `get_env(key)` | `getEnv(key)` | 完全映射 | - |
| Python 私有 `_stream_writer()` / `_custom_writer()` | Java 私有 `getStreamWriter()` / `getCustomWriter()` | 语义映射 | 都是内部 helper。 |
| 无 | `getInner()` | Java 扩展 | 返回内部 `NodeSession`。 |

## 3. Core Session 与 Internal Session

### 3.1 `session.BaseSession` -> `BaseSession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `config()` | `config()` | 完全映射 | - |
| `state()` | `state()` | 完全映射 | - |
| `tracer()` | `tracer()` | 完全映射 | - |
| `stream_writer_manager()` | `streamWriterManager()` | 完全映射 | - |
| `callback_manager()` | `callbackManager()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `checkpointer()` | `checkpointer()` | 完全映射 | - |
| `actor_manager()` | 无基类对位 | 缺口 | Java 只在部分具体 session 上实现。 |
| `async close()` | `close()` | 语义映射 | async -> sync。 |
| Python 无 | `getSessionId()` / `getState()` / `updateState()` / `setCurrentOperatorId()` / `getCurrentOperatorId()` | Java 兼容层 | 为兼容 `Session` 接口额外补出。 |

### 3.2 `session.ProxySession` -> `ProxySession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(stub=None)` | `ProxySession(...)` | 完全映射 | - |
| `set_session(stub)` | `setSession(stub)` | 完全映射 | - |
| `config()` | `config()` | 完全映射 | - |
| `state()` | `state()` | 完全映射 | - |
| `tracer()` | `tracer()` | 完全映射 | - |
| `stream_writer_manager()` | `streamWriterManager()` | 完全映射 | - |
| `callback_manager()` | `callbackManager()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `checkpointer()` | `checkpointer()` | 完全映射 | - |
| 无 | `getStub()` | Java 扩展 | 暴露当前代理目标。 |

### 3.3 `session.Session` -> `Session`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| Python 废弃类 `Session()` | `Session` 接口 | 语义替代 | Java 不是废弃类，而是供 `ContextEngine` 使用的最小接口。 |
| Python 警告性构造 | 无 | 不对位 | 这里是设计替换，不应误判为实现缺漏。 |

### 3.4 `internal.agent.AgentSession` -> `internal.AgentSession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(session_id, config=None, checkpointer=None, card=None)` | `AgentSession(...)` | 完全映射 | - |
| `config()` | `config()` | 完全映射 | - |
| `state()` | `state()` | 完全映射 | - |
| `tracer()` | `tracer()` | 完全映射 | - |
| `span()` | `span()` | 完全映射 | - |
| `stream_writer_manager()` | `streamWriterManager()` | 完全映射 | - |
| `callback_manager()` | `callbackManager()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `checkpointer()` | `checkpointer()` / `checkpointerTyped()` | 完全映射 | Java 多补 typed getter。 |
| `create_workflow_session()` | `createWorkflowSession()` | 完全映射 | - |
| `agent_id()` | `agentId()` | 完全映射 | - |
| 无 | `agentName()` / `agentDescription()` / `tracerTyped()` | Java 扩展 | - |

### 3.5 `internal.workflow.WorkflowSession` -> `internal.WorkflowSession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(workflow_id='', parent=None, session_id=None, state=None, callback_manager=None)` | `WorkflowSession(...)` | 完全映射 | - |
| `set_stream_writer_manager(...)` | `setStreamWriterManager(...)` | 完全映射 | - |
| `set_tracer(...)` | `setTracer(...)` | 完全映射 | - |
| `set_actor_manager(...)` | `setActorManager(...)` | 完全映射 | - |
| `set_workflow_id(...)` | `setWorkflowId(...)` | 完全映射 | - |
| `actor_manager()` | `actorManager()` | 完全映射 | - |
| `config()` | `config()` | 完全映射 | - |
| `state()` | `state()` | 完全映射 | - |
| `tracer()` | `tracer()` | 完全映射 | - |
| `stream_writer_manager()` | `streamWriterManager()` | 完全映射 | - |
| `callback_manager()` | `callbackManager()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `checkpointer()` | `checkpointer()` | 完全映射 | - |
| `workflow_id()` | `workflowId()` | 完全映射 | - |
| `main_workflow_id()` | `mainWorkflowId()` | 完全映射 | - |
| `workflow_nesting_depth()` | `workflowNestingDepth()` | 完全映射 | - |
| `async close()` | `close()` | 语义映射 | - |
| `parent()` | `parent()` | 完全映射 | - |

### 3.6 `internal.workflow.NodeSession` -> `internal.NodeSession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(session, node_id, node_type=None, skip_trace=False)` | `NodeSession(session, nodeId, nodeType)` | 部分映射 | `skip_trace` 构造参数缺失。 |
| `node_id()` | `nodeId()` | 完全映射 | - |
| `node_type()` | `nodeType()` | 完全映射 | - |
| `executable_id()` | `executableId()` | 完全映射 | - |
| `parent_id()` | `parentId()` | 完全映射 | - |
| `workflow_id()` | `workflowId()` | 完全映射 | - |
| `main_workflow_id()` | `mainWorkflowId()` | 完全映射 | - |
| `workflow_nesting_depth()` | `workflowNestingDepth()` | 完全映射 | - |
| `actor_manager()` | 无 | 缺口 | Java `NodeSession` 未公开该方法。 |
| `parent()` | `parent()` | 完全映射 | - |
| `tracer()` | `tracer()` | 完全映射 | - |
| `state()` | `state()` | 完全映射 | - |
| `config()` | `config()` | 完全映射 | - |
| `stream_writer_manager()` | `streamWriterManager()` | 完全映射 | - |
| `callback_manager()` | `callbackManager()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `checkpointer()` | `checkpointer()` | 完全映射 | - |
| `node_config()` | `nodeConfig()` | 完全映射 | - |
| `skip_trace()` | 无 | 缺口 | Java 没有跳过 trace 的标志与 accessor。 |
| 模块级 `create_parent_id(...)` | `NodeSession` 私有静态 helper | 语义映射 | 已内收。 |
| 模块级 `create_executable_id(...)` | `NodeSession` 私有静态 helper | 语义映射 | 已内收。 |

### 3.7 `internal.workflow.SubWorkflowSession` -> `internal.SubWorkflowSession`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `__init__(session, workflow_id, actor_manager=None)` | `SubWorkflowSession(session, nodeId, nodeType, workflowId)` | 部分映射 | Java 构造签名更偏显式。 |
| `workflow_id()` | `workflowId()` | 完全映射 | - |
| `workflow_nesting_depth()` | `workflowNestingDepth()` | 完全映射 | - |
| `main_workflow_id()` | 继承 `mainWorkflowId()` | 完全映射 | - |
| `actor_manager()` | `actorManager()` | 完全映射 | - |
| `async close()` | 无 override | 缺口 | Java 未像 Python 一样在子工作流 close 时关闭 actor manager。 |
| 无 | `setActorManager(...)` | Java 扩展 | 便于后置注入。 |

### 3.8 `internal.wrapper.*` -> `internal.*`

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `WrappedSession.get_workflow_config()` | `getWorkflowConfig()` | 完全映射 | - |
| `get_agent_config()` | `getAgentConfig()` | 完全映射 | Java 返回 `Config.MetadataLike`。 |
| `get_env()` | `getEnv()` | 完全映射 | - |
| `base()` | `base()` | 完全映射 | `RouterSession.base()` 两侧都无有效实现。 |
| `executable_id()` | `executableId()` | 完全映射 | - |
| `session_id()` | `sessionId()` | 完全映射 | - |
| `user_id()` | `userId()` | 完全映射 | 默认空值。 |
| `update_state()` | `updateState()` | 完全映射 | - |
| `get_state()` | `getState()` | 完全映射 | - |
| `update_global_state()` | `updateGlobalState()` | 完全映射 | `RouterSession` 中都为 no-op。 |
| `get_global_state()` | `getGlobalState()` | 完全映射 | - |
| `stream_writer()` | `streamWriter()` | 完全映射 | - |
| `custom_writer()` | `customWriter()` | 完全映射 | - |
| `async write_stream()` | `writeStream()` | 语义映射 | - |
| `async write_custom_stream()` | `writeCustomStream()` | 语义映射 | - |
| `async trace()` | `trace()` | 语义映射 | - |
| `async trace_error()` | `traceError()` | 语义映射 | - |
| `async interact()` | `interact()` | 语义映射 | - |
| `async post_run()` | `postRun()` | 语义映射 | 默认 no-op。 |
| `async pre_run(**kwargs)` | `preRun(Map<String,Object>)` | 语义映射 | 默认 no-op。 |
| `async release(session_id)` | `release(sessionId)` | 语义映射 | 默认 no-op。 |

## 4. Config / Constants / State / Store / Utils

### 4.1 Config 与常量

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `MetadataLike` | `Config.MetadataLike` | 语义映射 | `TypedDict` -> 静态内部类。 |
| `_try_set_env(...)` | `Config.trySetEnv(...)` 私有方法 | 语义映射 | 作为内部实现保留。 |
| `_load_env_configs()` | `Config.loadEnvConfigs()` 私有方法 | 语义映射 | - |
| `workflow_session_vars` | 无 | 缺口 | Java 只读取 `System.getenv()`，没有 contextvars 覆盖层。 |
| `Config.set_envs()` | `setEnvs()` | 完全映射 | - |
| `Config.get_env()` | `getEnv()` | 完全映射 | - |
| `Config.get_envs()` | `getEnvs()` | 完全映射 | - |
| `Config.get_workflow_config()` | `getWorkflowConfig()` | 完全映射 | - |
| `Config.get_agent_config()` | `getAgentConfig()` | 完全映射 | - |
| `Config.set_agent_config()` | `setAgentConfig()` | 完全映射 | - |
| `Config.add_workflow_config()` | `addWorkflowConfig()` | 完全映射 | - |
| `constants.py` 全部常量 | `SessionConstants` | 完全映射 | Java 还额外补了 `LOOP_ID`、`INDEX`。 |

### 4.2 State

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `ReadableStateLike` | `ReadableState` | 完全映射 | `get` / `get_by_prefix` -> `get` / `getByPrefix`。 |
| `Transformer = Callable[[ReadableStateLike], Any]` | `Function<Object, Object>` | 语义映射 | Java 未保留单独别名类型。 |
| `RecoverableStateLike` | `RecoverableState` | 完全映射 | `get_state` / `set_state` -> `getState` / `setState`。 |
| `StateLike` | `StateLike` | 完全映射 | `get_by_transformer` -> `getByTransformer`。 |
| `CommitStateLike` | `CommitStateLike` | 完全映射 | `update_by_id` / `commit` / `rollback` / `get_updates` / `set_updates` 对齐。 |
| `InMemoryStateLike` | `InMemoryStateLike` | 完全映射 | - |
| `InMemoryCommitState` | `InMemoryCommitState` | 完全映射 | - |
| `State` | `State` | 基本映射 | 主方法对齐；Java 增加 `TRACE_STATE_KEY`。 |
| `state.agent_state.StateCollection` | `AgentStateCollection` | 完全映射 | - |
| `state.workflow_state.StateCollection` | `WorkflowStateCollection` | 完全映射 | - |
| `CommitState` | `WorkflowCommitState` | 完全映射 | Java 改名避免歧义。 |
| `InMemoryState` | `InMemoryState` | 完全映射 | Java 用工厂式静态创建。 |

### 4.3 Store 与 Utils

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Store.read()` / `write()` | `Store.read()` / `write()` | 完全映射 | - |
| `FileStore` | `FileStore` | 完全映射 | 仍是占位实现。 |
| `MemoryStore` | `MemoryStore` | 完全映射 | - |
| `is_ref_path()` | `isRefPath()` | 完全映射 | - |
| `extract_origin_key()` | `extractOriginKey()` | 完全映射 | - |
| `split_nested_path()` | `splitNestedPath()` | 完全映射 | - |
| `get_value_by_nested_path()` | `getValueByNestedPath()` | 完全映射 | - |
| `root_to_path()` | `rootToPath()` | 完全映射 | - |
| `update_dict()` | `updateDict()` | 完全映射 | - |
| `expand_nested_structure()` | `expandNestedStructure()` | 完全映射 | - |
| `get_by_schema()` | `getBySchema()` | 完全映射 | - |
| `EndFrame` | `SessionUtils.EndFrame` | 完全映射 | - |
| `create_wrapper_class()` | 无 | 缺口 | Python tracer 装饰链依赖此 helper。 |
| `delete_by_key()` | `SessionUtils.deleteByKey(...)` 私有方法 | 可见性差异 | Java 仅保留内部实现，不是公开 API。 |
| `update_by_key()` | `SessionUtils.updateByKey(...)` 私有方法 | 可见性差异 | 同上。 |
| `_safe_extend_container()` | 无 | 缺口 | Java 无同名 helper。 |
| `root_to_index()` | 无 | 缺口 | Java 仅在 `rootToPath(...)` 内处理 list index。 |

## 5. Stream / Callback / Interaction / Checkpointer / Tracer

### 5.1 Stream

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `StreamMode` | `StreamMode` | 基本映射 | Java 把 `BaseStreamMode` 吸收到同一个 enum。 |
| `BaseStreamMode` | `StreamMode.OUTPUT/TRACE/CUSTOM` | 语义映射 | 无单独类型。 |
| `OutputSchema` | `OutputSchema` | 完全映射 | - |
| `TraceSchema` | `TraceSchema` | 完全映射 | - |
| `CustomSchema` | `CustomSchema` | 完全映射 | - |
| `StreamSchemas` | `StreamSchema` | 语义映射 | Python 联合类型 -> Java marker interface。 |
| `AsyncStreamQueue` | `AsyncStreamQueue` | 语义映射 | async queue -> 同步阻塞队列。 |
| `StreamEmitter` | `StreamEmitter` | 完全映射 | - |
| `StreamWriter` | `StreamWriter` | 基本映射 | async 写入改为同步。 |
| `OutputStreamWriter` | `StreamWriter<OutputSchema>` | 语义映射 | Java 收敛为泛型 writer。 |
| `TraceStreamWriter` | `StreamWriter<TraceSchema>` | 语义映射 | - |
| `CustomStreamWriter` | `StreamWriter<CustomSchema>` | 语义映射 | - |
| `StreamWriterManager.create_manager()` | `createManager()` | 完全映射 | - |
| `stream_emitter()` | `getStreamEmitter()` | 完全映射 | - |
| `async stream_output(...)` | `streamOutput(...)` / `streamIterator(...)` | 语义映射 | async generator -> callback / iterator。 |
| `add_writer()` / `get_writer()` / `remove_writer()` | `addWriter()` / `getWriter()` / `removeWriter()` | 完全映射 | - |
| `get_output_writer()` / `get_trace_writer()` / `get_custom_writer()` | `getOutputWriter()` / `getTraceWriter()` / `getCustomWriter()` | 完全映射 | - |
| 无 | `collectStreamOutput()` | Java 扩展 | 阻塞收集所有流项。 |

### 5.2 Callback

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `trigger_event` | `@TriggerEvent` | 语义映射 | 装饰器 -> 注解。 |
| `BaseHandler.event_name()` | `eventName()` | 完全映射 | - |
| `BaseHandler.get_trigger_events()` | `getTriggerEvents()` | 完全映射 | - |
| `CallbackManager.trigger(...)` | `trigger(...)` | 语义映射 | async -> sync；Java 还支持 `snake_case` 到 `camelCase` 解析。 |
| `CallbackManager.register(...)` | `register(...)` | 完全映射 | - |
| Python 私有 `_instantiation_handler` / `_init_handler` | Java 私有 helper | 语义映射 | Java 内部通过 `findMethod`、`resolveEventName` 承载。 |

### 5.3 Interaction

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `InteractiveInput` | `InteractiveInput` | 完全映射 | - |
| `InteractionOutput` | `InteractionOutput` | 完全映射 | - |
| `BaseInteraction._init_interactive_inputs()` | `initInteractiveInputs()` | 完全映射 | Java 转为私有 helper。 |
| `_get_next_interactive_input()` | `getNextInteractiveInput()` | 完全映射 | - |
| `async wait_user_inputs()` | `waitUserInputs()` | 语义映射 | - |
| `async user_latest_input()` | `userLatestInput()` | 语义映射 | - |
| `AgentInterrupt` | `AgentInterrupt` | 完全映射 | - |
| `WorkflowInteraction.wait_user_inputs()` | `WorkflowInteraction.waitUserInputs()` | 语义映射 | Java 通过 `GraphInterruptRuntimeWrapper` 传播 checked exception。 |
| `WorkflowInteraction.user_latest_input()` | `userLatestInput()` | 语义映射 | - |
| `SimpleAgentInteraction.wait_user_inputs()` | `waitUserInputs()` | 语义映射 | - |
| `AgentInteraction.wait_user_inputs()` | `waitUserInputs()` | 语义映射 | - |

### 5.4 Checkpointer

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `Checkpointer.get_thread_id()` | `getThreadId()` | 完全映射 | - |
| `pre_workflow_execute()` | `preWorkflowExecute()` | 完全映射 | async -> sync。 |
| `post_workflow_execute()` | `postWorkflowExecute()` | 完全映射 | - |
| `pre_agent_execute()` | `preAgentExecute()` | 完全映射 | - |
| `interrupt_agent_execute()` | `interruptAgentExecute()` | 完全映射 | - |
| `post_agent_execute()` | `postAgentExecute()` | 完全映射 | - |
| `session_exists()` | `sessionExists()` | 完全映射 | - |
| `release()` | `release()` | 完全映射 | - |
| `graph_store()` | `graphStore()` | 完全映射 | - |
| `Storage.save/recover/clear/exists` | `Storage.save/recover/clear/exists` | 完全映射 | async -> sync。 |
| `build_key()` | `Checkpointer.buildKey()` | 完全映射 | - |
| `build_key_with_namespace()` | `Checkpointer.buildKeyWithNamespace()` | 完全映射 | - |
| `CheckpointerProvider.create(conf)` | `CheckpointerProvider.create(conf)` | 语义映射 | async -> sync。 |
| `CheckpointerFactory.register()` | `register()` | 完全映射 | - |
| `CheckpointerFactory.create(CheckpointerConfig)` | `create(String type, Map<String,Object> conf)` | 部分映射 | Java 缺 `CheckpointerConfig` 类型壳。 |
| `set_default_checkpointer()` | `setDefaultCheckpointer()` | 完全映射 | - |
| `set_checkpointer()` | `setCheckpointer()` | 完全映射 | - |
| `get_checkpointer()` | `getCheckpointer()` | 完全映射 | - |
| `InMemoryCheckpointer` | `InMemoryCheckpointer` | 基本映射 | 主逻辑对齐。 |
| `checkpointer.persistence.*` | 无 | 缺失 | 持久化实现整组未移植。 |

### 5.5 Tracer

| Python API | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `InvokeType` | `InvokeType` | 完全映射 | - |
| `NodeStatus` | `NodeStatus` | 完全映射 | - |
| `Span` | `Span` | 完全映射 | `snapshot()` 等 helper 已有。 |
| `TraceAgentSpan` | `TraceAgentSpan` | 完全映射 | - |
| `TraceWorkflowSpan` | `TraceWorkflowSpan` | 完全映射 | - |
| `SpanManager` | `SpanManager` | 完全映射 | - |
| `TracerHandlerName` | `TracerHandlerName` | 完全映射 | - |
| `TraceBaseHandler.emit_stream_writer()` | `emitStreamWriter()` | 语义映射 | sync 化。 |
| `_format_data()` | `formatData()` | 完全映射 | protected helper。 |
| `_send_data()` | `sendData()` | 完全映射 | - |
| `_get_elapsed_time()` | `getElapsedTime()` | 完全映射 | - |
| `_get_node_status()` | `getNodeStatus()` | 完全映射 | - |
| `TraceAgentHandler` 事件链 | `TraceAgentHandler` 事件链 | 部分映射 | 主 start/end/error 已齐，但 `on_llm_request` 缺失。 |
| `TraceWorkflowHandler` | `TraceWorkflowHandler` | 基本映射 | 主 workflow/component tracing 已齐。 |
| `Tracer.init()` | `init()` | 完全映射 | - |
| `register_workflow_span_manager()` | `registerWorkflowSpanManager()` | 完全映射 | - |
| `get_workflow_span()` | `getWorkflowSpan()` | 完全映射 | - |
| `async trigger(...)` | `trigger(...)` | 语义映射 | Java 本身就是同步回调触发。 |
| `sync_trigger(...)` | 无单独方法 | 语义吸收 | Java 触发链路本身同步执行。 |
| `pop_workflow_span()` | `popWorkflowSpan()` | 完全映射 | - |
| `TracerWorkflowUtils.*` | `TracerWorkflowUtils.*` | 完全映射 | workflow trace helper 已齐。 |
| `decorate_model_with_trace()` | 无 | 缺失 | Python tracer 装饰器体系未移植。 |
| `decorate_tool_with_trace()` | 无 | 缺失 | - |
| `decorate_workflow_with_trace()` | 无 | 缺失 | - |
| `trace()` / `async_trace()` / `trace_stream()` / `async_trace_stream()` | 无 | 缺失 | - |

## 6. 第二轮复核确认的真实缺口

| 类别 | 缺口 | Python 现状 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| 持久化 checkpoint | `checkpointer.persistence` 子体系 | 有 `BaseStorage`、`AgentStorage`、`WorkflowStorage`、`GraphStore`、`PersistenceCheckpointer`、`PersistenceCheckpointerProvider`、`_enable_sqlite_wal` | 无同层实现 | 只能使用内存 checkpoint，无法对齐 Python 持久化恢复能力 |
| Checkpointer 配置对象 | `CheckpointerConfig` | 独立 Pydantic 配置对象，`CheckpointerFactory.create()` 直接接收 | 只支持 `create(String type, Map<String,Object> conf)` | 工厂 API 入口不完全兼容 |
| Tracer 装饰器 | `tracer.decorator` 整组函数 | 顶层导出且可直接包装 model/tool/workflow | 无 | Agent 侧自动 trace 包装能力缺失 |
| Tracer 事件 | `TraceAgentHandler.on_llm_request` | 已实现 | 无 | LLM 请求阶段的增量 trace 数据无法完全对齐 |
| NodeSession trace 开关 | `NodeSession(..., skip_trace=False)` + `skip_trace()` | 已实现 | 无 | `NodeSessionApi.trace/traceError/interact` 无法按 Python 语义跳过 trace |
| 子工作流关闭 | `SubWorkflowSession.close()` | 会关闭 actor manager | 无 override | 子工作流 actor manager 生命周期不完全对齐 |
| Config 上下文变量 | `workflow_session_vars` | 允许 contextvars 覆盖环境配置 | 无 | Java 只能读取进程环境变量，缺少运行时上下文覆盖层 |
| Utils 包装器 | `create_wrapper_class()` | tracer decorator 依赖 | 无 | 与 tracer 装饰器缺失形成一组能力缺口 |
| Utils 公开 helper | `root_to_index()`、`_safe_extend_container()` | 存在 | 无 | 嵌套 list 路径处理的公开 helper 不完整 |
| Utils 可见性 | `delete_by_key()`、`update_by_key()` | Python 为模块公开函数 | Java 仅保留私有 helper | 严格按公开 API 对齐时仍有可见性差异 |
| BaseSession 抽象面 | `actor_manager()` | 基类有定义 | 基类无 | 以 `BaseSession` 为多态入口时，Java 缺少统一 actor manager API |

## 7. 不应再误判为缺漏的项

- `BaseStreamMode`：Java 已把 `OUTPUT/TRACE/CUSTOM` 折叠进同一个 `StreamMode` enum。
- `StreamSchemas`：Java 用 `StreamSchema` marker interface 承载联合类型语义，不是缺失。
- `OutputStreamWriter` / `TraceStreamWriter` / `CustomStreamWriter`：Java 统一为泛型 `StreamWriter`，能力仍在。
- `trigger_event`：Java 用 `@TriggerEvent` 注解替代 Python 装饰器。
- `Transformer`：Java 用 `Function<Object, Object>` 承载，而不是单独类型别名。
- `session.Session`：Java `Session` 是兼容接口，不是 Python 那个废弃类的直接翻译。
- async API：大量 Python async 方法在 Java 中被同步实现承接，这属于执行模型差异，不应直接判为缺失。

## 8. 建议优先级

1. `P0`：补齐 `checkpointer.persistence` 子体系，至少先把 `PersistenceCheckpointer`、`PersistenceCheckpointerProvider`、`CheckpointerConfig` 落地。
2. `P0`：补齐 `tracer.decorator` 与 `TraceAgentHandler.onLlmRequest`，恢复 agent-side trace 自动包装能力。
3. `P1`：给 `NodeSession` 补 `skipTrace` 构造参数与 accessor，并在 `NodeSessionApi` 调用链里恢复 Python 的 trace 跳过逻辑。
4. `P1`：在 `SubWorkflowSession.close()` 中补 actor manager shutdown。
5. `P2`：补齐 `workflow_session_vars`、`create_wrapper_class`、`root_to_index` 等工具/配置层公开 helper。