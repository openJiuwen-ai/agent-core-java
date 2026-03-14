# graph 模块 Python / Java API 映射

## 对照范围

- Python 源码: `agent-core-python/openjiuwen/core/graph/**`
- Java 源码: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/graph/**`
- Python 统计口径: 模块内非 `_` 顶层类，以及这些类的主要公开/准公开方法；`TypedDict`、`dataclass` 采用“字段名即 API”方式统计。
- Java 统计口径: `public` / `protected` 类型与方法；纯 DTO/Bean 的 getter、setter 不逐个机械展开，而以“字段 -> getter/setter”汇总。
- 命名规则: 默认按 `snake_case -> camelCase` 对照；async / await 在 Java 侧通常落成同步 `Iterator`、阻塞队列或虚拟线程实现。

## 复核结论

- Python `graph` 模块共复核 54 个类；Java `com.openjiuwen.core.graph` 及其子包共复核 50 个源码类。
- Java 版主干能力已经覆盖 `core graph`、`pregel`、`store`、`stream_actor`、`visualization` 五个分组。
- 差异主要分成三类:
  1. 命名与语言适配: `snake_case -> camelCase`、`TypedDict/dataclass -> bean`、`async -> sync/virtual thread`。
  2. 结构调整: `Branch`、`InnerPregelConfig`、`JsonSerializer` 等在 Java 中变成宿主类的嵌套类或被合并。
  3. 真实缺口: compile 上下文透传、异步条件路由、`FIRST_EXCEPTION` 语义、LLM 流式输出回写、`png/svg` 导出、`PickleSerializer` 等。详见 `../FIXED/graph_fixed.md`。

## 状态说明

- `完全映射`: Python API 与 Java API 语义基本一致。
- `适配映射`: Java 有对位实现，但命名、宿主类位置或同步模型不同。
- `部分映射`: Java 有主体实现，但仍存在可见能力差异或部分公开 API 未对齐。
- `Python-only`: Python 有公开类型，Java 没有同位公开类型。
- `Java-only`: Java 为桥接、类型系统或可用性额外增加的公开类型/方法。

## 包级总览

| Python 模块 | Java 包 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.graph` | `com.openjiuwen.core.graph` | 部分映射 | 主图构建、执行包装器已齐，`compile(**kwargs)` / `context` 透传未齐。 |
| `openjiuwen.core.graph.pregel` | `com.openjiuwen.core.graph.pregel` | 部分映射 | BSP 引擎主体齐全，异步 selector、async after-step 与 `FIRST_EXCEPTION` 语义仍有差异。 |
| `openjiuwen.core.graph.store` | `com.openjiuwen.core.graph.store` | 部分映射 | 存储主流程齐，`PickleSerializer` 缺失。 |
| `openjiuwen.core.graph.stream_actor` | `com.openjiuwen.core.graph.stream_actor` | 适配映射 | 流式 actor 主体齐，async 接口改为同步/阻塞式实现。 |
| `openjiuwen.core.graph.visualization` | `com.openjiuwen.core.graph.visualization` | 部分映射 | Mermaid 文本生成已齐，`png/svg` 导出与自动目标推断能力未完全对齐。 |

## 1. core graph

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AtomicNode` | `AtomicNode` | `atomic_invoke -> atomicInvoke`; `_atomic_invoke -> doAtomicInvoke` | 适配映射 | Python 同时存在同步 `AtomicNode` 与异步 `AsyncAtomicNode`；Java 合并为一个同步原子节点抽象。 |
| `AsyncAtomicNode` | `AtomicNode` | `atomic_invoke -> atomicInvoke`; `_atomic_invoke -> doAtomicInvoke` | 适配映射 | Java 无独立异步原子节点类型。 |
| `Executable` | `Executable` | `on_invoke -> onInvoke`; `on_stream -> onStream`; `on_collect -> onCollect`; `on_transform -> onTransform`; `skip_trace -> skipTrace`; `graph_invoker -> graphInvoker`; `post_commit -> postCommit`; `component_type -> componentType` | 完全映射 | 公开方法一一可对位。 |
| `ExecutableGraph` | `ExecutableGraph` | `invoke -> invoke`; `stream -> stream`; `collect -> collect`; `transform -> transform`; `interrupt -> interrupt`; `_invoke -> doInvoke` | 适配映射 | 两边都保留了 `stream/collect/transform` 默认占位实现。 |
| `Router` type alias | `Router` functional interface | Python callable / awaitable router -> Java `Function<Object, Object>` | 部分映射 | Java 不支持 async router；详见缺漏清单。 |
| `Graph` | `Graph` | `start_node -> startNode`; `end_node -> endNode`; `add_node -> addNode`; `add_edge -> addEdge`; `add_conditional_edges -> addConditionalEdges`; `compile -> compile`; `get_nodes -> getNodes` | 部分映射 | Java 额外提供 `addNode(nodeId, node)` 重载，但没有 `compile(session, **kwargs)`。 |
| `Branch` (`graph.py`) | `PregelGraph.Branch` | `condition -> getCondition()` | 适配映射 | Python 是顶层 dataclass，Java 是 `PregelGraph` 的静态嵌套类。 |
| `PregelGraph` | `PregelGraph` | `start_node -> startNode`; `end_node -> endNode`; `add_node(wait_for_all=...) -> addNode(..., boolean)`; `add_edge -> addEdge`; `add_conditional_edges -> addConditionalEdges`; `compile -> compile`; `reset -> reset` | 部分映射 | Python `get_nodes()` 返回 `dict[str, Vertex]`；Java `getNodes()` 返回 `Map<String, Executable<?, ?>>`，并新增 `getVertex()` 作为补偿入口。 |
| `CompiledGraph` | `CompiledGraph` | `_invoke -> doInvoke`; `stream -> stream`; `interrupt -> interrupt` | 适配映射 | `stream()` 与 `interrupt()` 两边当前都未形成完整能力；Java `stream()` 返回 `null`，Python 也仍是占位。 |
| `GraphState` (`graph_state.py`) | `GraphNodeState` | `source_node_id -> getSourceNodeId/setSourceNodeId` | 适配映射 | Java 把 `TypedDict` 落成实体类，并额外补了 `merge()`。 |
| `Vertex` | `Vertex` | `init -> init`; `__call__ -> call`; `_atomic_invoke -> doAtomicInvoke`; `stream_call -> streamCall`; `is_done -> isDone`; `should_handle_message -> shouldHandleMessage`; `reset -> reset` | 部分映射 | Python 的 `_pre/_post_*` 与 tracing helper 在 Java 中被内联为私有实现；此外 Java 版缺少 LLM 流式输出回写。 |

## 2. pregel

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `START/END/MAX_RECURSIVE_LIMIT/...` | `PregelConstants.*` | 常量同名映射 | 完全映射 | 常量集合与取值对齐。 |
| `Interrupt` | `Interrupt` | `value -> getValue`; `__str__ -> toString` | 完全映射 | - |
| `GraphInterrupt` | `GraphInterrupt` | `value -> getValue` | 完全映射 | Python 异常值对象在 Java 中保留。 |
| `IRouter` | `IRouter` | `dispatch -> dispatch` | 适配映射 | Python 为 async `dispatch`，Java 为同步返回 `List<Message>`。 |
| `Message` | `Message` | 构造参数 `sender/target/payload`; 字段 -> `getSender/getTarget/getPayload`; `__str__ -> toString` | 完全映射 | - |
| `TriggerMessage` | `TriggerMessage` | 同父类构造 | 完全映射 | - |
| `BarrierMessage` | `BarrierMessage` | 同父类构造 | 完全映射 | - |
| `PregelNode` | `PregelNode` | `name/func/routers -> getName/getFunc/getRouters` | 完全映射 | Java 用 getter 暴露 dataclass 字段。 |
| `Channel` | `Channel` | `key -> getKey`; `node_name -> getNodeName`; `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 完全映射 | - |
| `TriggerChannel` | `TriggerChannel` | `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 完全映射 | - |
| `BarrierChannel` | `BarrierChannel` | `key -> getKey`; `node_name -> getNodeName`; `is_ready -> isReady`; `accept -> accept`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 适配映射 | Python 的 `_make_router_key()` 在 Java 中被内部化。 |
| `ChannelManager` | `ChannelManager` | `buffer_message -> bufferMessage`; `is_empty -> isEmpty`; `flush -> flush`; `get_ready_nodes -> getReadyNodes`; `consume -> consume`; `snapshot -> snapshot`; `restore -> restore` | 完全映射 | Java 额外公开了 `getBuffer()`。 |
| `PregelConfig` | `PregelConfig` | `session_id/ns/recursion_limit -> getter/setter`; dict-style `get(key) -> get(key)` | 适配映射 | Java 把 `InnerPregelConfig` 合并进同一个类，并补了 `toMap()`。 |
| `InnerPregelConfig` | `PregelConfig` | `create_inner_config -> PregelConfig.createInnerConfig` | 适配映射 | Java 没有独立 `InnerPregelConfig` 类型。 |
| `DEFAULT_PREGEL_CONFIG` | `PregelConfig.DEFAULT` | 常量映射 | 完全映射 | - |
| `create_inner_config(config)` | `PregelConfig.createInnerConfig(config)` | 顶层函数 -> 静态工厂 | 完全映射 | - |
| `PregelBuilder` | `PregelBuilder` | `add_node -> addNode`; `add_edge -> addEdge`; `add_branch -> addBranch`; `build -> build` | 部分映射 | `selector` 仅支持同步 `Function<Object, Object>`。 |
| `StaticRouter` | `StaticRouter` | `dispatch -> dispatch` | 完全映射 | - |
| `ConditionalRouter` | `ConditionalRouter` | `dispatch -> dispatch` | 部分映射 | Python 支持 sync/async selector，且会检查 `state` 参数；Java 仅 `selector.apply(null)`。 |
| `BarrierRouter` | `BarrierRouter` | `dispatch -> dispatch` | 完全映射 | - |
| `NodeTask` | `NodeTask` | `run -> call` | 适配映射 | Java 用 `Callable<Object>` 实现。 |
| `TaskExecutorPool` | `TaskExecutorPool` | `submit -> submit`; `wait_all -> waitAll`; `cancel_all -> cancelAll`; `clear -> clear` | 部分映射 | Java 补了 `getSucceedMessages/getFailed`，但 `waitAll()` 未完全保留 Python 的 `FIRST_EXCEPTION` 取消语义。 |
| `PregelLoop` | `PregelLoop` | `init -> init`; `run_step -> runStep` | 适配映射 | Python 的 `_is_resume/_save_state_on_error` 在 Java 中保留为私有 helper，语义总体一致。 |
| `Pregel` | `Pregel` | `run -> run`; `nodes/channels/initial/store/after_step -> getter` | 部分映射 | Python `after_step` 可为 sync 或 async；Java 仅 `Consumer<PregelLoop>`。 |

## 3. store

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `PendingNode` | `PendingNode` | `node_name/status/exception -> getNodeName/getStatus/getExceptions` | 适配映射 | Java 用 getter 暴露字段。 |
| `GraphState` (`store.base`) | `GraphStoreState` | `ns/step/channel_values/pending_buffer/pending_node/node_version -> getter`; `create_state -> GraphStoreState.create` | 适配映射 | Java 重命名为 `GraphStoreState` 以避免与 `GraphNodeState` 冲突。 |
| `Store` | `Store` | `get/save/delete -> get/save/delete` | 完全映射 | async -> 同步接口。 |
| `GraphStore` | `GraphStore` | `get/save/delete -> get/save/delete` | 完全映射 | Java `get()` 返回 `Optional<GraphStoreState>`。 |
| `InMemoryStore` | `InMemoryStore` | `get/save/delete -> get/save/delete` | 完全映射 | Python `_delete_ns_by_prefix()` 在 Java 中保留为私有 `deleteNsByPrefix()`。 |
| `Serializer` | `Serializer` | `dumps_typed -> dumpsTyped`; `loads_typed -> loadsTyped`; `create_serializer -> Serializer.create` | 部分映射 | `pickle` 路径未对齐，Java 仅内建 JSON serializer。 |
| `JsonSerializer` | `Serializer.JsonSerializer` | `dumps_typed -> dumpsTyped`; `loads_typed -> loadsTyped` | 适配映射 | Java 用嵌套类承载。 |
| `PickleSerializer` | 无 | 无 | Python-only | Java 没有等价公开类型。 |

## 4. stream_actor

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `StreamConsumer` | `StreamConsumer` | `stream_call -> streamCall`; `should_handle_message -> shouldHandleMessage`; `is_done -> isDone` | 完全映射 | - |
| `StreamGraph` | `StreamGraph` | `add_stream_consumer -> addStreamConsumer`; `get_node -> getNode` | 完全映射 | - |
| `StreamPayload` | `StreamPayload` | `message/source_ability -> getMessage/getSourceAbility` | 完全映射 | - |
| `StreamActor` | `StreamActor` | `send -> send`; `generator -> generator`; `shutdown -> shutdown` | 适配映射 | Python `_error_callback()` 在 Java 中内联处理。 |
| `StreamProcessor` | `StreamProcessor` | `run -> run`; `receive -> receive`; `generator -> generator` | 适配映射 | Python 的 `_create_generator/is_value_from_source/_get_unique_source_key` 在 Java 中保留为私有或包级 helper。 |
| `StreamTransform` | `StreamTransform` | `get_by_defined_transformer -> getByDefinedTransformer`; `get_by_default_transformer -> getByDefaultTransformer` | 完全映射 | - |
| `ActorManager` | `ActorManager` | `sub_workflow_stream -> subWorkflowStream`; `stream_transform -> getStreamTransform`; `produce -> produce`; `end_message -> endMessage`; `consume -> consume`; `shutdown -> shutdown` | 适配映射 | Python `_get_actor()` 在 Java 中内联。 |

## 5. visualization

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Drawable` | `Drawable` | `add_node -> addNode`; `set_start_node -> setStartNode`; `set_end_node -> setEndNode`; `set_break_node -> setBreakNode`; `add_edge -> addEdge`; `to_mermaid -> toMermaid`; `get_graph -> getGraph` | 部分映射 | Java 额外提供 `addSimpleNode()` 与 `toMermaid()` 默认重载；但缺少 `to_mermaid_png()/to_mermaid_svg()`，且 callable 目标推断能力弱于 Python。 |
| `_get_targets(data)` | `Drawable.TargetProvider` + `Drawable.getTargetsFromCallable()` | 顶层 helper -> 嵌套接口 + 私有 helper | 部分映射 | Python 能从 `Literal[...]` 返回类型自动推导目标；Java 只有 `TargetProvider` 时才能显式提供。 |
| `DrawableEdge` | `DrawableEdge` | `source/target/data/conditional/streaming -> getter/setter` | 完全映射 | - |
| `DrawableBranchRouter` | `DrawableBranchRouter` | `targets/datas -> getTargets/getDatas` | 完全映射 | - |
| `DrawableGraph` | `DrawableGraph` | `nodes/edges/start_nodes/end_nodes/break_nodes -> getter/setter` | 完全映射 | - |
| `DrawableNode` | `DrawableNode` | `id/name/metadata -> getter/setter` | 完全映射 | - |
| `DrawableSubgraphNode` | `DrawableSubgraphNode` | `subgraph -> getSubgraph/setSubgraph` | 完全映射 | - |
| `Stringifiable` | 无 | 无 | Python-only | 这是 Python 的 typing protocol；Java 直接依赖 `Object.toString()`。 |
| `_MermaidDiagram` | `MermaidDiagram` | Mermaid 文本生成器 | 适配映射 | Python 为模块内部类；Java 为包级实现类。 |

## 6. 结构性差异

### Python 有, Java 采用“合并/嵌套/替代”实现

| Python API | Java 落点 | 说明 |
| --- | --- | --- |
| `AsyncAtomicNode` | `AtomicNode` | Java 用同步原子节点统一承载。 |
| `Branch` | `PregelGraph.Branch` | 改为宿主类嵌套类型。 |
| `InnerPregelConfig` | `PregelConfig` | 被合并为同一个配置类。 |
| `JsonSerializer` | `Serializer.JsonSerializer` | 改为嵌套类。 |
| `SelectorProtocol` | `Router` / `ConditionalRouter` | Python typing protocol 在 Java 中退化为函数接口。 |

### Java 额外公开的桥接 API

| Java API | Python 对位 | 说明 |
| --- | --- | --- |
| `PregelGraph.getVertex()` | 无同名公开方法 | 用于补回 `getNodes()` 不再直接返回 `Vertex` 的差异。 |
| `ChannelManager.getBuffer()` | 无同名公开方法 | 暴露缓冲区，便于恢复和测试。 |
| `TaskExecutorPool.getSucceedMessages()` | Python 直接访问 `succeed_messages` | Java 通过 getter 暴露。 |
| `TaskExecutorPool.getFailed()` | Python 直接访问 `failed` | Java 通过 getter 暴露。 |
| `Drawable.addSimpleNode()` | 无同名公开方法 | 额外提供直接加简易节点的入口。 |
| `Serializer.TypedBytes` | 无独立公开类型 | Java 用 record 表达 typed bytes。 |

## 7. 仍需关注的未对齐点

1. `Graph.compile/PregelGraph.compile` 缺少 Python `**kwargs` 与 `context` 透传。
2. `ConditionalRouter` 与 `Router` 不支持 async selector，也没有 Python 那样的 `state` 参数探测逻辑。
3. `TaskExecutorPool.waitAll()` 未精确保留 Python `FIRST_EXCEPTION` 的“首错即取消”语义。
4. `Vertex` 少了 Python `LLMExecutable.get_stream_output()` 的流式输出回写。
5. `Drawable` 缺少 `to_mermaid_png()`、`to_mermaid_svg()`。
6. `Drawable` 对条件边目标节点的自动推断弱于 Python。
7. `PickleSerializer` 与 `Serializer.create("pickle")` 未落地。
8. `Pregel/PregelBuilder` 的 after-step callback 仅支持同步 `Consumer`。

详细缺漏、影响和定位见 `../FIXED/graph_fixed.md`。
