# graph 模块 Python / Java API 映射

## 对照范围

- Python：`agent-core-python/openjiuwen/core/graph/**`
- Java：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/graph/**`
- 统计口径：
  - Python 统计公开类、公开函数、公开方法；同时补充少量以下划线开头、但在 Java 中有直接对应生命周期方法的 helper。
  - Java 统计 `public` 类型与 `public` 方法；对直接承接 Python underscore helper 的 `private/protected` 方法，在表中一并注明。
- 对照分组：`core graph`、`pregel`、`store`、`stream_actor`、`visualization`

## 复核结论

- Java 版 `graph` 主干 API 已基本覆盖 Python 版；第一轮文档里提到的 `compile(..., context)` 透传、`TaskExecutorPool.waitAll()` 的 `FIRST_EXCEPTION` 语义、`Vertex._post_stream()` 的 LLM 输出回写、`Drawable.to_mermaid_png/svg()` 都已经在 Java 中补齐。
- 当前真实仍未完全对齐的点，主要只剩 4 类：
  - 条件路由仍只支持同步 `Function<Object, Object>`，还没有 Python 的“任意 callable + 可选 `state` + async selector”能力。
  - `Drawable` 还不能像 Python `_get_targets()` 一样从 `Literal[...]` 返回类型自动推导条件边目标。
  - `Serializer` 的公开命名仍未与 Python `PickleSerializer` / `create_serializer("pickle")` 对齐。
  - `Pregel.after_step` 仍只接受同步 `Consumer<PregelLoop>`，不能直接承载 Python 的 async callback。
- 主要结构性适配已经完成：
  - `AsyncAtomicNode` 合并进 Java `AtomicNode`
  - `Branch`、`JsonSerializer` 等落为宿主类的嵌套类型
  - `AsyncIterator` / `asyncio.Queue` 分别落为 `Iterator` / `BlockingQueue`
  - `TypedDict` / `dataclass` 多数落为 Java bean / record

## 包级映射

| Python 模块 | Java 对应位置 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.graph` | `com.openjiuwen.core.graph` | 部分映射 | 核心图构建、编译、执行入口都在；`get_nodes()` 的返回类型与 Python 不同。 |
| `openjiuwen.core.graph.pregel` | `com.openjiuwen.core.graph.pregel` | 部分映射 | BSP 主体已经齐；剩余差异集中在条件路由 callable/async 支持与 async hook。 |
| `openjiuwen.core.graph.store` | `com.openjiuwen.core.graph.store` | 部分映射 | 状态存取主链已齐；`PickleSerializer` 的公开命名与工厂参数未对齐。 |
| `openjiuwen.core.graph.stream_actor` | `com.openjiuwen.core.graph.stream_actor` | 适配映射 | Stream actor 主体已齐；Python 的异步生成器被 Java 的阻塞式 `Iterator`/队列实现替代。 |
| `openjiuwen.core.graph.visualization` | `com.openjiuwen.core.graph.visualization` | 部分映射 | Mermaid 文本和 png/svg 导出都已齐；条件边目标自动推断仍弱于 Python。 |

## 命名映射约定

- Python `snake_case` 在 Java 中通常转为 `camelCase`
- Python 模块级函数通常落为 Java 静态 helper 或宿主类私有 helper
- Python `async` API 在 Java 中通常转为同步方法、虚拟线程、`Iterator` 或 `BlockingQueue`
- Python `TypedDict/dataclass` 在 Java 中通常转为 bean、record 或带 getter/setter 的实体类

## 1. Core Graph

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `_validate_session_and_state(session)` | `AtomicNode.validateSessionAndState(session)` | 模块级 helper -> 私有静态 helper | 完全映射 | Java 复用了同样的“校验 session/state 后提交组件状态”的入口。 |
| `AtomicNode` | `AtomicNode` | `atomic_invoke -> atomicInvoke`; `_atomic_invoke -> doAtomicInvoke` | 适配映射 | Python 同时有 sync `AtomicNode` 与 async `AsyncAtomicNode`；Java 合并成单一抽象类。 |
| `AsyncAtomicNode` | `AtomicNode` | `atomic_invoke -> atomicInvoke`; `_atomic_invoke -> doAtomicInvoke` | 适配映射 | Java 通过同步方法 + 虚拟线程语义承接。 |
| `Executable` | `Executable` | `on_invoke -> onInvoke`; `on_stream -> onStream`; `on_collect -> onCollect`; `on_transform -> onTransform`; `skip_trace -> skipTrace`; `graph_invoker -> graphInvoker`; `post_commit -> postCommit`; `component_type -> componentType` | 完全映射 | 公开能力一一对位。 |
| `ExecutableGraph` | `ExecutableGraph` | `invoke -> invoke`; `stream -> stream`; `collect -> collect`; `transform -> transform`; `interrupt -> interrupt`; `_invoke -> doInvoke` | 适配映射 | Java 把异步图执行包装成同步 `Iterator`/返回值；占位方法两边都仍保留。 |
| `Router` type alias | `Router` | Python callable/awaitable -> Java `Function<Object, Object>` | 部分映射 | Java 只覆盖同步 `Function`，不支持 async router，也不接受所有 callable 对象。 |
| `Graph` | `Graph` | `start_node -> startNode`; `end_node -> endNode`; `add_node -> addNode`; `add_edge -> addEdge`; `add_conditional_edges -> addConditionalEdges`; `compile -> compile`; `get_nodes -> getNodes` | 部分映射 | `compile(session, **kwargs)` 已对齐；`getNodes()` 返回的是 `Executable`，不是 Python 的 `Vertex` wrapper。 |
| `Branch` (`graph.py`) | `PregelGraph.Branch` | `condition -> getCondition()` | 适配映射 | Python 顶层 dataclass 在 Java 中变成 `PregelGraph` 静态嵌套类。 |
| `_get_callable_name(func)` | `PregelGraph.getCallableName(func)` | 模块级 helper -> 私有静态 helper | 完全映射 | 用于分支命名。 |
| `PregelGraph` | `PregelGraph` | `start_node -> startNode`; `_validate_node_id -> validateNodeId`; `end_node -> endNode`; `add_node(wait_for_all=...) -> addNode(..., boolean)`; `get_nodes -> getNodes + getVertex`; `add_edge -> addEdge`; `add_conditional_edges -> addConditionalEdges`; `compile -> compile`; `_compile -> doCompile`; `reset -> reset` | 部分映射 | `compile(BaseSession, Map<String, Object>)` 已把 `kwargs["context"]` 传进 `Vertex.init()`；但 `getNodes()` 返回值仍与 Python 不同，所以额外补了 `getVertex()`。 |
| `CompiledGraph` | `CompiledGraph` | `_invoke -> doInvoke`; `stream -> stream`; `interrupt -> interrupt` | 适配映射 | 两边的 `stream()` / `interrupt()` 当前都还是占位。Java `doInvoke()` 直接返回 `Pregel.run()` 的结果 map。 |
| `GraphState` (`graph_state.py`) | `GraphNodeState` | `source_node_id -> getSourceNodeId/setSourceNodeId`; `merge -> merge`(Java only) | 适配映射 | Java 在 `source_node_id` 之外额外补了 `merge()` 便于节点状态合并。 |
| `Vertex` | `Vertex` | `init -> init`; `__call__ -> call`; `_atomic_invoke -> doAtomicInvoke`; `call(config) -> doCall(config)`; `_pre_invoke/_post_invoke -> preInvoke/postInvoke`; `_pre_stream/_post_stream -> preStream/postStream`; `_process_chunk -> processChunk`; `_clear_interactive -> clearInteractive`; `stream_call -> streamCall`; `_stream_abilities -> streamAbilities`; `should_handle_message -> shouldHandleMessage`; `is_done -> isDone`; tracing helpers `__trace_* -> trace*`; `reset -> reset` | 部分映射 | Java 把 Python 的 `call(config)` 与 Pregel 入口 `__call__(state, config)` 拆成 public `call(GraphNodeState, Object)` + private `doCall(Object)`；`_post_stream()` 的 LLM 输出回写已补齐。 |

## 2. Pregel

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `pregel.constants` | `PregelConstants` | `START/END/MAX_RECURSIVE_LIMIT/... -> PregelConstants.*` | 完全映射 | 关键常量都已对齐。 |
| `Interrupt` | `Interrupt` | `value -> getValue`; `__init__ -> ctor` | 适配映射 | Java 额外实现了 `toString()`。 |
| `GraphInterrupt` | `GraphInterrupt` | `value -> getValue`; `__init__ -> ctor` | 完全映射 | - |
| `IRouter` | `IRouter` | `dispatch -> dispatch` | 适配映射 | Python 是 async interface，Java 是同步接口。 |
| `Message` | `Message` | 构造参数 `sender/target/payload`; 字段 -> `getSender/getTarget/getPayload` | 完全映射 | Java 额外补了 `toString()`。 |
| `TriggerMessage` | `TriggerMessage` | 同父类构造 | 完全映射 | - |
| `BarrierMessage` | `BarrierMessage` | 同父类构造 | 完全映射 | - |
| `PregelNode` | `PregelNode` | `name/func/routers -> getName/getFunc/getRouters` | 完全映射 | dataclass -> getter。 |
| `Channel` | `Channel` | `key -> getKey`; `node_name -> getNodeName`; `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 完全映射 | - |
| `TriggerChannel` | `TriggerChannel` | `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 完全映射 | - |
| `BarrierChannel` | `BarrierChannel` | `key -> getKey`; `node_name -> getNodeName`; `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore`; `_make_router_key -> makeRouterKey` | 完全映射 | Python 的静态 helper 在 Java 中是私有静态方法。 |
| `ChannelManager` | `ChannelManager` | `buffer_message -> bufferMessage`; `is_empty -> isEmpty`; `flush -> flush`; `get_ready_nodes -> getReadyNodes`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore`; `buffer -> getBuffer` | 完全映射 | Java 额外公开了 `getBuffer()` 便于错误恢复和测试。 |
| `PregelConfig` | `PregelConfig` | `session_id/ns/recursion_limit -> getter/setter`; `get(key) -> get(key)`; `to_map -> toMap` | 适配映射 | Python `TypedDict` 被 Java bean 承接。 |
| `InnerPregelConfig` | `PregelConfig` | `parent_ns -> getParentNs/setParentNs`; `create_inner_config -> createInnerConfig` | 适配映射 | Java 把外部/内部配置合并到同一个类。 |
| `DEFAULT_PREGEL_CONFIG` | `PregelConfig.DEFAULT` | 常量映射 | 完全映射 | - |
| `create_inner_config(config)` | `PregelConfig.createInnerConfig(config)` | 模块级函数 -> 静态工厂 | 完全映射 | - |
| `PregelBuilder` | `PregelBuilder` | `add_node -> addNode`; `add_edge -> addEdge`; `add_branch -> addBranch`; `build -> build` | 部分映射 | builder 主体已齐；但 `addBranch()` 只能接受同步 `Function<Object, Object>`。 |
| `StaticRouter` | `StaticRouter` | `dispatch -> dispatch` | 完全映射 | - |
| `SelectorProtocol` | `Function<Object, Object>` | Python protocol -> Java 函数式接口 | 部分映射 | Java 只有同步 `Function` 形态，没有 protocol/async 兼容层。 |
| `ConditionalRouter` | `ConditionalRouter` | `dispatch -> dispatch` | 部分映射 | Python 会探测 `state` 参数并支持 coroutine；Java 固定 `selector.apply(null)`。 |
| `BarrierRouter` | `BarrierRouter` | `dispatch -> dispatch` | 完全映射 | - |
| `NodeTask` | `NodeTask` | `run -> call`; `kwargs` 组装逻辑 -> `buildArgs/acceptsParameter/findCallMethod/invokeFunc` | 适配映射 | Java 通过反射构造 `config/state` 入参，承接 Python 的 `inspect.signature(...)`。 |
| `TaskExecutorPool` | `TaskExecutorPool` | `submit -> submit`; `wait_all -> waitAll`; `cancel_all -> cancelAll`; `clear -> clear`; `_commit_failure -> commitFailure`; `succeed_messages/failed -> getSucceedMessages/getFailed` | 完全映射 | `waitAll()` 已改为“首错触发 + 取消剩余任务”的语义。 |
| `PregelLoop` | `PregelLoop` | `init -> init`; `run_step -> runStep`; `_run_step -> doRunStep`; `_save_state_on_error -> saveStateOnError`; `_is_resume -> isResume`; `step/config/active_nodes -> getter` | 部分映射 | 主体流程对齐；after-step callback 仍只能同步执行。 |
| `Pregel` | `Pregel` | `run -> run`; `nodes/channels/initial/store/after_step -> getter` | 部分映射 | `run()` 的返回语义已与 Python 对齐；`after_step` 仍只有同步 `Consumer` 版本。 |

## 3. Store

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `PendingNode` | `PendingNode` | `node_name/status/exception -> getNodeName/getStatus/getExceptions` | 适配映射 | Java getter 使用复数 `getExceptions()`。 |
| `GraphState` (`store/base.py`) | `GraphStoreState` | `ns/step/channel_values/pending_buffer/pending_node/node_version -> getter` | 适配映射 | Java 改名为 `GraphStoreState`，避免与 `GraphNodeState` 冲突。 |
| `create_state(...)` | `GraphStoreState.create(...)` | 模块级工厂 -> 静态工厂 | 完全映射 | - |
| `Store` | `Store` | `get/save/delete -> get/save/delete` | 适配映射 | Java 用同步接口和 `Optional<GraphStoreState>` 承接。 |
| `GraphStore` | `GraphStore` | `get/save/delete -> get/save/delete` | 完全映射 | 装饰器语义对齐。 |
| `InMemoryStore` | `InMemoryStore` | `get/save/delete -> get/save/delete`; `_delete_ns_by_prefix -> deleteNsByPrefix` | 完全映射 | Java 额外补了 `deepCopy(...)` 私有 helper。 |
| `Serializer` | `Serializer` | `dumps_typed -> dumpsTyped`; `loads_typed -> loadsTyped` | 部分映射 | 抽象协议已齐；typed bytes 在 Java 中用 `TypedBytes` record 承载。 |
| `JsonSerializer` | `Serializer.JsonSerializer` | `dumps_typed -> dumpsTyped`; `loads_typed -> loadsTyped` | 适配映射 | Python 是独立类，Java 是嵌套类。 |
| `PickleSerializer` | `Serializer.JavaNativeSerializer` | `dumps_typed -> dumpsTyped`; `loads_typed -> loadsTyped` | 部分映射 | 序列化机制相近，但公开名字、类型标签和工厂参数未与 Python 的 `"pickle"` 对齐。 |
| `create_serializer(type_name)` | `Serializer.create(typeName)` | 模块级工厂 -> 静态工厂 | 部分映射 | Python 公开的是 `"pickle"` 路径；Java 公开 `"json"` 与 `"java"`。 |

## 4. Stream Actor

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `StreamConsumer` | `StreamConsumer` | `stream_call -> streamCall`; `should_handle_message -> shouldHandleMessage`; `is_done -> isDone` | 适配映射 | Python `async def` 在 Java 中改为同步接口。 |
| `StreamGraph` | `StreamGraph` | `add_stream_consumer -> addStreamConsumer`; `get_node -> getNode` | 完全映射 | - |
| `StreamPayload` | `StreamPayload` | `message/source_ability -> getMessage/getSourceAbility` | 完全映射 | dataclass -> bean。 |
| `StreamActor` | `StreamActor` | `send -> send`; `generator -> generator`; `_error_callback -> errorCallback`; `shutdown -> shutdown` | 适配映射 | Python 用 task + future；Java 用虚拟线程 + `CompletableFuture`。 |
| `StreamProcessor` | `StreamProcessor` | `run -> run`; `receive -> receive`; `generator -> generator`; `_create_generator -> createIterator`; `is_value_from_source -> isValueFromSource`; `_get_unique_source_key -> getUniqueSourceKey`; `_is_end_message -> isEndMessage`; `_get_producer_id -> getProducerId` | 适配映射 | Java 以 `Iterator.hasNext()/next()` 代替 Python async generator，因此额外暴露了 `hasNext/next`。 |
| `StreamTransform` | `StreamTransform` | `get_by_defined_transformer -> getByDefinedTransformer`; `get_by_default_transformer -> getByDefaultTransformer` | 完全映射 | - |
| `ActorManager` | `ActorManager` | `sub_workflow_stream -> subWorkflowStream`; `stream_transform -> getStreamTransform`; `produce -> produce`; `end_message -> endMessage`; `consume -> consume`; `shutdown -> shutdown`; `_build_reverse_graph -> buildReverseGraph` | 适配映射 | Python 的 `_get_actor()` 在 Java 中被内联到 `streams` map 访问。 |

## 5. Visualization

| Python API | Java 对应 | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `_get_targets(data)` | `Drawable.getTargetsFromCallable(data)` + `Drawable.TargetProvider` | 模块级 helper -> 私有 helper + 可选接口 | 部分映射 | Java 只能在对象实现 `TargetProvider` 时显式给出目标，尚不支持 Python 的 `Literal[...]` 类型推导。 |
| `Drawable` | `Drawable` | `add_node -> addNode`; `set_start_node -> setStartNode`; `set_end_node -> setEndNode`; `set_break_node -> setBreakNode`; `add_edge -> addEdge`; `to_mermaid -> toMermaid`; `to_mermaid_png -> toMermaidPng`; `to_mermaid_svg -> toMermaidSvg`; `get_graph -> getGraph` | 部分映射 | png/svg 导出已补齐；Java 还额外提供 `addSimpleNode()`。 |
| `DrawableEdge` | `DrawableEdge` | 字段 -> `getSource/getTarget/getData/setData/isConditional/setConditional/isStreaming/setStreaming` | 完全映射 | - |
| `DrawableBranchRouter` | `DrawableBranchRouter` | `targets/datas -> getTargets/getDatas` | 完全映射 | - |
| `DrawableGraph` | `DrawableGraph` | `nodes/edges/start_nodes/end_nodes/break_nodes -> getter`; `break_nodes = ... -> setBreakNodes` | 完全映射 | - |
| `DrawableNode` | `DrawableNode` | `id/name/metadata -> getId/getName/setName/getMetadata/setMetadata` | 完全映射 | - |
| `DrawableSubgraphNode` | `DrawableSubgraphNode` | `subgraph -> getSubgraph/setSubgraph` | 完全映射 | - |
| `Stringifiable` | 无独立同名类型 | `__str__ -> Object.toString()` | 适配映射 | Python protocol 在 Java 中没有独立公开接口。 |
| `_MermaidDiagram` | `MermaidDiagram` | `to_mermaid -> toMermaid`; `to_mermaid_png/svg -> MermaidRenderer.renderPng/renderSvg` | 适配映射 | Python 是模块私有实现，Java 拆成 `MermaidDiagram` + `MermaidRenderer`。 |
| `MermaidRenderer` | Java-only | `renderPng/renderSvg` | Java-only | Python 版通过 `mermaid-py` 直接渲染，没有单独 renderer 类。 |

## 6. 结构性适配与额外桥接

### Python 类型在 Java 中的承接方式

| Python API | Java 落点 | 说明 |
| --- | --- | --- |
| `AsyncAtomicNode` | `AtomicNode` | Java 统一用一个同步抽象类承接原子调用语义。 |
| `Branch` | `PregelGraph.Branch` | 顶层 dataclass 被收拢为宿主类嵌套类型。 |
| `InnerPregelConfig` | `PregelConfig` | 外部/内部配置合并为同一类，用 `parentNs` 区分。 |
| `JsonSerializer` / `PickleSerializer` | `Serializer.JsonSerializer` / `Serializer.JavaNativeSerializer` | Java 用嵌套类承接序列化实现。 |
| `_MermaidDiagram` | `MermaidDiagram` | Python 私有 helper 被拆成 Java 包内实现类。 |

### Java 额外公开的桥接 API

| Java API | Python 对位 | 说明 |
| --- | --- | --- |
| `PregelGraph.getVertex(String)` | 无同名公开方法 | 用来弥补 `getNodes()` 不再直接返回 `Vertex` 的差异。 |
| `Drawable.addSimpleNode(String)` | 无同名公开方法 | 额外提供纯节点占位入口。 |
| `Serializer.TypedBytes` | Python `(type, bytes)` tuple | Java 用 record 表达 typed bytes。 |
| `Drawable.TargetProvider` | 无同名公开类型 | 用显式接口替代 Python 的返回类型推导。 |
| `StreamProcessor.hasNext()/next()` | Python async generator 协议 | Java 迭代器模式需要显式暴露。 |
| `MermaidRenderer` | 无独立同名类 | Java 单独拆出渲染器类。 |

## 7. 第二轮复核后仍未完全对齐的点

1. 条件路由兼容性仍不足：`Router` / `ConditionalRouter` / `PregelGraph.Branch` 仍只覆盖同步 `Function<Object, Object>`，没有 Python 对任意 callable、`state` 参数探测和 async selector 的兼容层。
2. `Drawable` 条件边目标自动推导仍弱于 Python：Java 只能依赖 `TargetProvider`，无法直接复用 Python `_get_targets()` 对 `Literal[...]` 返回类型的推断。
3. `PickleSerializer` 的 API 表面仍未对齐：Java 虽然已有 `JavaNativeSerializer`，但没有 `PickleSerializer` 公开名，也不支持 `create("pickle")`。
4. `Pregel.after_step` 仍只有同步 callback：Python `PregelBuilder.build(..., after_step_callback=...)` / `Pregel` 允许 sync 或 async callback，Java 目前只接受 `Consumer<PregelLoop>`。

详细缺漏与优先级见：`../FIXED/graph_fixed.md`
