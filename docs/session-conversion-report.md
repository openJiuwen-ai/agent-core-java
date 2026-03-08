# Session 模块 Python → Java 转换报告

## 1. 概述

| 项目 | 说明 |
|------|------|
| 模块名称 | session（会话模块） |
| 架构层级 | Layer 2 — Core Engine |
| 优先级 | P0 |
| Python 源文件数 | 43 |
| Java 生成文件数 | 63 |
| 编译状态 | ✅ 通过 (`mvn compile` 无错误) |
| Java 版本 | 21 |
| 基础包路径 | `com.openjiuwen.core.session` |

## 2. 子系统映射总览

### 2.1 State（状态管理）— 13 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `state/base.py` — `ReadableStateLike` | `state/ReadableState.java` | 只读状态接口 |
| `state/base.py` — `RecoverableStateLike` | `state/RecoverableState.java` | 快照/恢复接口 |
| `state/base.py` — `StateLike` | `state/StateLike.java` | 可变状态接口 |
| `state/base.py` — `CommitStateLike` | `state/CommitStateLike.java` | 提交/回滚状态接口 |
| `state/base.py` — `InMemoryStateLike` | `state/InMemoryStateLike.java` | 内存 StateLike 实现 |
| `state/base.py` — `InMemoryCommitState` | `state/InMemoryCommitState.java` | 内存提交状态实现 |
| `state/base.py` — `State` | `state/State.java` | 主 State 接口（含分区常量） |
| `state/workflow_state.py` — `WorkflowStateCollection` | `state/WorkflowStateCollection.java` | Workflow 状态集合 |
| `state/workflow_state.py` — `CommitState` | `state/WorkflowCommitState.java` | Workflow 提交状态（含 createNodeState） |
| `state/workflow_state.py` — `InMemoryState` | `state/InMemoryState.java` | 内存状态工厂类 |
| `state/agent_state.py` — `StateCollection` | `state/AgentStateCollection.java` | Agent 状态集合 |

### 2.2 Stream（流式输出）— 9 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `stream/base.py` — `BaseStreamMode` | `stream/StreamMode.java` | 流模式枚举 (OUTPUT/TRACE/CUSTOM) |
| `stream/base.py` — `StreamSchema` | `stream/StreamSchema.java` | 流数据 Schema 标记接口 |
| `stream/base.py` — `OutputSchema` | `stream/OutputSchema.java` | 输出 Schema（type/index/payload） |
| `stream/base.py` — `TraceSchema` | `stream/TraceSchema.java` | 追踪 Schema |
| `stream/base.py` — `CustomSchema` | `stream/CustomSchema.java` | 自定义 Schema |
| `stream/queue.py` | `stream/AsyncStreamQueue.java` | 线程安全 BlockingQueue |
| `stream/emitter.py` | `stream/StreamEmitter.java` | 流数据生产者 |
| `stream/writer.py` | `stream/StreamWriter.java` | 带 Schema 验证的流写入器 |
| `stream/manager.py` | `stream/StreamWriterManager.java` | 流写入管理器 |

### 2.3 Callback（回调管理）— 3 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `callback/base.py` — `trigger_event` | `callback/TriggerEvent.java` | 运行时注解（替代 Python 装饰器） |
| `callback/base.py` — `BaseHandler` | `callback/BaseHandler.java` | 处理器抽象基类 |
| `callback/callback_manager.py` | `callback/CallbackManager.java` | 回调注册与触发管理器 |

### 2.4 Config（配置）— 1 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `config/base.py` | `config/Config.java` | 会话配置（环境变量、workflow configs、agent config） |

### 2.5 Session Core（会话核心）— 4 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `session.py` — `Session` (deprecated) | `Session.java` | 已有最小接口（保留兼容） |
| `session.py` — `BaseSession` | `BaseSession.java` | 抽象基类，实现 Session 接口 |
| `session.py` — `ProxySession` | `ProxySession.java` | 代理/委派模式实现 |
| `constants.py` | `constants/SessionConstants.java` | 超时、环境变量常量 |
| `utils.py` | `utils/SessionUtils.java` | 嵌套路径、引用路径、EndFrame 等工具方法 |

### 2.6 Interaction（交互）— 7 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `interaction/interactive_input.py` | `interaction/InteractiveInput.java` | 用户交互输入数据 |
| `interaction/base.py` — `AgentInterrupt` | `interaction/AgentInterrupt.java` | Agent 交互中断异常 |
| `interaction/base.py` — `InteractionOutput` | `interaction/InteractionOutput.java` | 交互输出 POJO |
| `interaction/base.py` — `BaseInteraction` | `interaction/BaseInteraction.java` | 交互抽象基类 |
| `interaction/interaction.py` — `WorkflowInteraction` | `interaction/WorkflowInteraction.java` | Workflow 层交互 |
| `interaction/interaction.py` — `AgentInteraction` | `interaction/AgentInteraction.java` | Agent 层交互 |
| `interaction/interaction.py` — `SimpleAgentInteraction` | `interaction/SimpleAgentInteraction.java` | 简化 Agent 交互 |

### 2.7 Checkpointer（检查点）— 5 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `checkpointer/checkpointer.py` — `Checkpointer` | `checkpointer/Checkpointer.java` | 检查点抽象类 |
| `checkpointer/checkpointer.py` — `CheckpointerFactory` | `checkpointer/CheckpointerFactory.java` | 工厂/注册中心 |
| `checkpointer/checkpointer.py` — `Storage` | `checkpointer/Storage.java` | 存储抽象类 |
| `checkpointer/checkpointer.py` — `CheckpointerProvider` | `checkpointer/CheckpointerProvider.java` | 函数式创建接口 |
| `checkpointer/in_memory_checkpointer.py` | `checkpointer/InMemoryCheckpointer.java` | 内存检查点实现 |

### 2.8 Tracer（追踪）— 12 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `tracer/base.py` — `InvokeType` | `tracer/InvokeType.java` | 调用类型枚举 |
| `tracer/base.py` — `NodeStatus` | `tracer/NodeStatus.java` | 节点状态枚举 |
| `tracer/span.py` — `Span` | `tracer/Span.java` | 基础追踪 Span |
| `tracer/span.py` — `TraceAgentSpan` | `tracer/TraceAgentSpan.java` | Agent 追踪 Span |
| `tracer/span.py` — `TraceWorkflowSpan` | `tracer/TraceWorkflowSpan.java` | Workflow 追踪 Span |
| `tracer/span.py` — `SpanManager` | `tracer/SpanManager.java` | Span 有序集合管理 |
| `tracer/handler.py` — `TracerHandlerName` | `tracer/TracerHandlerName.java` | 处理器名称枚举 |
| `tracer/handler.py` — `TraceBaseHandler` | `tracer/TraceBaseHandler.java` | 追踪处理器基类 |
| `tracer/handler.py` — `TraceAgentHandler` | `tracer/TraceAgentHandler.java` | Agent 追踪处理器 |
| `tracer/workflow_handler.py` | `tracer/TraceWorkflowHandler.java` | Workflow 追踪处理器 |
| `tracer/workflow_tracer.py` | `tracer/TracerWorkflowUtils.java` | Workflow 追踪工具类 |
| `tracer/tracer.py` | `tracer/Tracer.java` | 中央追踪协调器 |

### 2.9 Internal Sessions（内部会话）— 7 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `internal/workflow.py` — `WorkflowSession` | `internal/WorkflowSession.java` | Workflow 会话 |
| `internal/workflow.py` — `NodeSession` | `internal/NodeSession.java` | 节点作用域会话 |
| `internal/workflow.py` — `SubWorkflowSession` | `internal/SubWorkflowSession.java` | 子 Workflow 嵌套会话 |
| `internal/agent.py` — `AgentSession` | `internal/AgentSession.java` | Agent 完整会话 |
| `internal/wrapper.py` — `WrappedSession` | `internal/WrappedSession.java` | 会话包装器基类 |
| `internal/wrapper.py` — `StateSession` | `internal/StateSession.java` | 状态委派会话 |
| `internal/wrapper.py` — `RouterSession` | `internal/RouterSession.java` | 路由会话（大部分方法为 no-op） |

### 2.10 External Session APIs（外部 API）— 3 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `node.py` | `NodeSessionApi.java` | 面向用户的节点会话 API |
| `agent.py` | `AgentSessionApi.java` | 面向用户的 Agent 会话 API |
| `workflow.py` | `WorkflowSessionApi.java` | 面向用户的 Workflow 会话 API |

### 2.11 Store（存储）— 3 文件

| Python 文件 | Java 文件 | 说明 |
|-------------|-----------|------|
| `store.py` — `Store` | `store/Store.java` | 存储抽象基类 |
| `store.py` — `FileStore` | `store/FileStore.java` | 文件存储（占位） |
| `store.py` — `MemoryStore` | `store/MemoryStore.java` | 内存存储 |

## 3. 关键转换决策

### 3.1 异步 → 同步

Python 的 `asyncio` 异步模式全部转换为 Java 同步方法：

- `asyncio.Queue` → `java.util.concurrent.LinkedBlockingQueue`
- `async def` / `await` → 普通同步方法
- `AsyncIterator` → `Consumer<Object>` 回调模式

### 3.2 Pydantic BaseModel → Java POJO

Python Pydantic 模型转换为带有 `fromMap()` 静态工厂方法的 Java POJO，用于运行时验证。

### 3.3 装饰器 → 注解

Python `@trigger_event` 装饰器转换为 Java `@TriggerEvent` 运行时注解，通过反射在 `BaseHandler.getTriggerEvents()` 中发现。

### 3.4 类型别名

Python `Transformer = Callable[[ReadableStateLike], Any]` 直接使用 Java `Function<ReadableState, Object>` 函数式接口，无需额外类。

### 3.5 ContextVar → System.getenv

Python `contextvars.ContextVar` 的环境配置加载简化为 `System.getenv()` + Config 的 envs map。

### 3.6 Session 接口兼容

保留已有 `Session.java` 最小接口（`getSessionId()`、`getState()`、`updateState()`），`BaseSession` 通过 `implements Session` 保持兼容。

### 3.7 Python 命名 → Java 命名

| Python 风格 | Java 风格 |
|-------------|-----------|
| `node.Session` | `NodeSessionApi` |
| `agent.Session` | `AgentSessionApi` |
| `workflow.Session` | `WorkflowSessionApi` |
| `CommitState` | `WorkflowCommitState` |
| `StateCollection` | `AgentStateCollection` |
| `BaseStreamMode` | `StreamMode` |

## 4. 外部依赖处理

### 4.1 已有模块（直接使用）

| 模块 | 用途 |
|------|------|
| `StatusCode` | 错误码枚举 (`com.openjiuwen.core.common.exception.StatusCode`) |
| `ErrorHelper` | 构建错误异常 |
| `BaseError` | 基础异常类 |
| `Loggers` | 日志工具 (`Loggers.SESSION`) |
| `Constant` | 通用常量 (`INTERACTIVE_INPUT`, `INTERACTION`, `END_FRAME`, `LOOP_ID`, `INDEX`) |

### 4.2 占位处理

| 依赖 | 处理方式 |
|------|----------|
| `GraphInterrupt` (graph 模块) | 使用 `RuntimeException("GraphInterrupt: ...")` 占位 |
| `Checkpointer.graphStore()` | 返回 `Object` 占位 |
| `WorkflowConfig.card` | 在 `TracerWorkflowUtils` 中留有 TODO 注释 |
| `AgentCard` | 使用 `Object card` 参数占位 |
| `FileStore` | read/write 方法为 TODO 占位 |

## 5. 目录结构

```
com.openjiuwen.core.session/
├── Session.java                    (已有，保留兼容)
├── BaseSession.java
├── ProxySession.java
├── AgentSessionApi.java
├── NodeSessionApi.java
├── WorkflowSessionApi.java
├── callback/
│   ├── TriggerEvent.java
│   ├── BaseHandler.java
│   └── CallbackManager.java
├── checkpointer/
│   ├── Checkpointer.java
│   ├── CheckpointerFactory.java
│   ├── CheckpointerProvider.java
│   ├── InMemoryCheckpointer.java
│   └── Storage.java
├── config/
│   └── Config.java
├── constants/
│   └── SessionConstants.java
├── interaction/
│   ├── AgentInteraction.java
│   ├── AgentInterrupt.java
│   ├── BaseInteraction.java
│   ├── InteractionOutput.java
│   ├── InteractiveInput.java
│   ├── SimpleAgentInteraction.java
│   └── WorkflowInteraction.java
├── internal/
│   ├── AgentSession.java
│   ├── NodeSession.java
│   ├── RouterSession.java
│   ├── StateSession.java
│   ├── SubWorkflowSession.java
│   ├── WorkflowSession.java
│   └── WrappedSession.java
├── state/
│   ├── AgentStateCollection.java
│   ├── CommitStateLike.java
│   ├── InMemoryCommitState.java
│   ├── InMemoryState.java
│   ├── InMemoryStateLike.java
│   ├── ReadableState.java
│   ├── RecoverableState.java
│   ├── State.java
│   ├── StateLike.java
│   ├── WorkflowCommitState.java
│   └── WorkflowStateCollection.java
├── store/
│   ├── FileStore.java
│   ├── MemoryStore.java
│   └── Store.java
├── stream/
│   ├── AsyncStreamQueue.java
│   ├── CustomSchema.java
│   ├── OutputSchema.java
│   ├── StreamEmitter.java
│   ├── StreamMode.java
│   ├── StreamSchema.java
│   ├── StreamWriter.java
│   ├── StreamWriterManager.java
│   └── TraceSchema.java
├── tracer/
│   ├── InvokeType.java
│   ├── NodeStatus.java
│   ├── Span.java
│   ├── SpanManager.java
│   ├── TraceAgentHandler.java
│   ├── TraceAgentSpan.java
│   ├── TraceBaseHandler.java
│   ├── Tracer.java
│   ├── TracerHandlerName.java
│   ├── TracerWorkflowUtils.java
│   ├── TraceWorkflowHandler.java
│   └── TraceWorkflowSpan.java
└── utils/
    └── SessionUtils.java
```

## 6. 后续待办

1. **GraphInterrupt 类型替换** — 当 graph 模块转换完成后，将 `RuntimeException("GraphInterrupt: ...")` 替换为真实的 `GraphInterrupt` 异常类
2. **FileStore 实现** — 基于文件系统的存储实现
3. **AgentCard 类型化** — 当 agent card 模块转换后，将 `Object card` 替换为具体类型
4. **WorkflowConfig 丰富** — `TracerWorkflowUtils.getWorkflowMetadata()` 中提取 workflow version/name
5. **NodeSession.nodeConfig()** — 需要 workflow config spec 类型完善后补全
6. **单元测试** — 基于 Python 测试用例编写 JUnit 5 对应测试
