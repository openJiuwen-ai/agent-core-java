# graph 模块缺漏复核清单

## 复核口径

- 以 Python `agent-core-python/openjiuwen/core/graph/**` 为基线，对 Java `src/main/java/com/openjiuwen/core/graph/**` 逐类逐方法复核。
- 这里只列“真实缺口”或“行为不一致点”。
- `snake_case -> camelCase`、`TypedDict/dataclass -> bean`、`async -> sync` 这类纯语言适配不计入缺漏。

## 复核结论

- Java 版 graph 主体已经可用，但仍有 8 个值得跟进的缺口。
- 其中优先级最高的是:
  1. `compile` 链路没有把 Python 的 `kwargs/context` 传到 `Vertex.init()`。
  2. 条件路由不支持 async selector。
  3. `TaskExecutorPool.waitAll()` 没有保持 Python 的 `FIRST_EXCEPTION` 取消语义。
  4. `Vertex` 没有把 `LLMExecutable.getStreamOutput()` 回写到 session state。

## 缺漏清单

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 | 代码定位 |
| --- | --- | --- | --- | --- | --- |
| `P0` | compile 上下文透传 | `Graph.compile(session, **kwargs)` / `PregelGraph.compile()` 会把 `**kwargs` 传给 `node.init(session, **kwargs)`；`Vertex.init()` 会消费 `context` | `Graph.compile(BaseSession)` 无额外参数；`PregelGraph.compile()` 固定调用 `entry.getValue().init(session, null)` | 依赖 compile-time `context` 的节点无法按 Python 方式初始化 | Python: `openjiuwen/core/graph/graph.py`, `openjiuwen/core/graph/vertex.py`; Java: `com/openjiuwen/core/graph/Graph.java`, `PregelGraph.java`, `Vertex.java` |
| `P0` | 条件路由 async / state 兼容 | `Router` 允许 sync/async callable；`ConditionalRouter.dispatch()` 会检查 selector 是否需要 `state`，并支持 `await selector(...)` | `Router` 只是 `Function<Object,Object>`；`ConditionalRouter.dispatch()` 直接 `selector.apply(null)` | Python 中的 async selector 或依赖签名探测的 selector 不能原样迁移 | Python: `openjiuwen/core/graph/base.py`, `pregel/router.py`; Java: `com/openjiuwen/core/graph/Router.java`, `pregel/ConditionalRouter.java`, `pregel/PregelBuilder.java` |
| `P0` | `FIRST_EXCEPTION` 语义缺失 | `TaskExecutorPool.wait_all()` 使用 `asyncio.wait(..., FIRST_EXCEPTION)`，一旦首个任务报错就取消剩余任务 | `TaskExecutorPool.waitAll()` 先 `CompletableFuture.allOf(...).join()`，等全部任务结束后才汇总异常 | 异常发生后其余节点仍可能继续执行，带来额外副作用，也拉长失败路径 | Python: `openjiuwen/core/graph/pregel/task.py`; Java: `com/openjiuwen/core/graph/pregel/TaskExecutorPool.java` |
| `P1` | LLM 流式输出回写 | `Vertex._post_stream()` 对 `LLMExecutable` 特判，调用 `get_stream_output()`，并把结果写回 `session.state().set_outputs(...)` | Java `LLMExecutable` 已有 `getStreamOutput()`，但 `Vertex.postStream()` 没有使用 | 流式 LLM 组件执行后，最终 outputs 可能留在组件内部状态，未同步回工作流 state | Python: `openjiuwen/core/graph/vertex.py`; Java: `com/openjiuwen/core/graph/Vertex.java`, `workflow/component/llm/LLMExecutable.java` |
| `P1` | Mermaid 图片导出 | `Drawable` 公开 `to_mermaid_png()` 与 `to_mermaid_svg()` | Java `Drawable` 只公开 `toMermaid(...)` 文本输出 | graph 可视化只能拿 Mermaid 文本，不能直接导出 PNG / SVG | Python: `openjiuwen/core/graph/visualization/drawable.py`; Java: `com/openjiuwen/core/graph/visualization/Drawable.java` |
| `P1` | 条件边目标自动推断弱化 | Python `_get_targets()` 能从 callable 的 `Literal[...]` 返回类型推导可视化 target | Java `getTargetsFromCallable()` 除非对象实现 `TargetProvider`，否则返回空列表 | 一些条件边在可视化图里无法自动展开到目标节点 | Python: `openjiuwen/core/graph/visualization/drawable.py`; Java: `com/openjiuwen/core/graph/visualization/Drawable.java` |
| `P2` | `PickleSerializer` 缺失 | Python 有 `PickleSerializer`，`create_serializer("pickle")` 返回该实现 | Java 只有 `Serializer.JsonSerializer`，`Serializer.create()` 只接受 `"json"` | 存储序列化策略与 Python 不完全兼容，无法对位 `pickle` 路径 | Python: `openjiuwen/core/graph/store/serde.py`; Java: `com/openjiuwen/core/graph/store/Serializer.java` |
| `P2` | async after-step callback 缺失 | `PregelBuilder.build(..., after_step_callback=...)` / `Pregel` 接受 sync 或 async callback | Java `PregelBuilder.build()` / `Pregel` 只接受 `Consumer<PregelLoop>` | 无法直接迁移 Python 的 async step hook | Python: `openjiuwen/core/graph/pregel/builder.py`, `pregel/engine.py`; Java: `com/openjiuwen/core/graph/pregel/PregelBuilder.java`, `Pregel.java` |

## 非缺漏, 但需要说明的结构差异

- `AsyncAtomicNode` 在 Java 中被折叠进 `AtomicNode`，这是同步化适配，不算缺漏。
- `Branch` 在 Java 中变成 `PregelGraph.Branch`，属于宿主类嵌套化，不算缺漏。
- `InnerPregelConfig` 在 Java 中被合并进 `PregelConfig`，且 `createInnerConfig()` 仍可对位 Python helper。
- `GraphState` 有两份语义:
  - Python `graph_state.py::GraphState` 对位 Java `GraphNodeState`
  - Python `store/base.py::GraphState` 对位 Java `GraphStoreState`
- `CompiledGraph.stream()` / `interrupt()` 目前两边都还是占位实现，不属于“Java 独有缺漏”。

## 建议优先级

1. 先修 `compile/context`、`ConditionalRouter`、`TaskExecutorPool.waitAll()`、`Vertex` 的 LLM 流式输出回写。
2. 再补 `Drawable` 图片导出与条件边目标推断。
3. 最后补 `PickleSerializer` 与 async after-step callback。
