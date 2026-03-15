# graph 模块缺漏复核清单

## 复核说明

- Python 基线：`agent-core-python/openjiuwen/core/graph/**`
- Java 对照：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/graph/**`
- 第二轮复核时间：`2026-03-14`
- 本文只保留“真实缺口”或“行为仍不一致”的项；纯命名差异、`snake_case -> camelCase`、`async -> sync/Iterator/BlockingQueue` 这类语言适配不计入缺漏。

## 第一轮 8 项在第二轮中的状态

| 第一轮项 | 第二轮状态 | 说明 |
| --- | --- | --- |
| `compile/context` 透传 | 已补齐 | Java 现已提供 `Graph.compile(BaseSession, Map<String, Object>)` / `PregelGraph.compile(BaseSession, Map<String, Object>)`，并把 `kwargs["context"]` 传入 `Vertex.init(session, kwargs)`。 |
| 条件路由 async / state 兼容 | 仍缺 | Java 条件路由仍只支持同步 `Function<Object, Object>`，没有 Python 的 callable/async/state 兼容层。 |
| `FIRST_EXCEPTION` 语义 | 已补齐 | `TaskExecutorPool.waitAll()` 已改为“首个失败触发 + 取消剩余任务”的处理逻辑。 |
| LLM 流式输出回写 | 已补齐 | `Vertex.postStream()` 现在已经对 `LLMExecutable.getStreamOutput()` 做回写。 |
| Mermaid `png/svg` 导出 | 已补齐 | `Drawable.toMermaidPng()` / `toMermaidSvg()` 已存在。 |
| 条件边目标自动推断 | 仍缺 | Java 还不能像 Python `_get_targets()` 那样从 `Literal[...]` 返回类型自动推导目标。 |
| `PickleSerializer` | 仍缺 | Java 只有 `JavaNativeSerializer` / `Serializer.create("java")`，公开 API 面仍未对齐 Python 的 `"pickle"` 路径。 |
| async `after_step` callback | 仍缺 | Java `PregelBuilder.build()` / `Pregel` 仍只接受同步 `Consumer<PregelLoop>`。 |

## 第二轮仍缺的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 | 代码定位 |
| --- | --- | --- | --- | --- | --- |
| `P0` | 条件路由 callable / async / `state` 兼容性 | `Router` 允许 sync/async callable；`ConditionalRouter.dispatch()` 会探测签名，必要时注入 `state=None`，并支持 `await selector(...)`；`Graph.add_conditional_edges()` 接受任意 callable | `Router` / `PregelBuilder.addBranch()` 只接受 `Function<Object, Object>`；`PregelGraph.Branch.getCondition()` 对非 `Function` router 会退化成返回 `null` 的 lambda；`ConditionalRouter.dispatch()` 固定执行 `selector.apply(null)` | Python 中的 class-based router、async selector、依赖 `state` 的 selector 都不能 1:1 迁移，且非 `Function` router 可能静默失效 | Python：`openjiuwen/core/graph/base.py`、`openjiuwen/core/graph/graph.py`、`openjiuwen/core/graph/pregel/router.py`；Java：`com/openjiuwen/core/graph/Router.java`、`PregelGraph.java`、`pregel/ConditionalRouter.java`、`pregel/PregelBuilder.java` |
| `P1` | `Drawable` 条件边目标自动推导 | `_get_targets()` 会读取 callable 的返回类型注解；若是 `Literal[...]`，可自动得到条件边目标列表 | `Drawable.getTargetsFromCallable()` 只有在对象实现 `Drawable.TargetProvider` 时才能拿到目标；普通 `Function`/callable 无法自动推导 | 条件边在可视化图里可能缺少目标展开，Mermaid 图的可读性弱于 Python | Python：`openjiuwen/core/graph/visualization/drawable.py`；Java：`com/openjiuwen/core/graph/visualization/Drawable.java` |
| `P1` | `PickleSerializer` 公开 API 命名与类型标签 | Python 暴露 `PickleSerializer`，`create_serializer("pickle")` 返回该实现，typed tag 也是 `"pickle"` | Java 暴露的是 `Serializer.JavaNativeSerializer`，工厂入口是 `Serializer.create("java")`，typed tag 是 `"java"` | 依赖 Python 命名、工厂参数或 typed tag 的迁移代码/兼容测试无法直接复用；文档层面也不能声称 API 完全对位 | Python：`openjiuwen/core/graph/store/serde.py`；Java：`com/openjiuwen/core/graph/store/Serializer.java` |
| `P2` | async `after_step` callback | Python `PregelBuilder.build(..., after_step_callback=...)` / `Pregel(after_step=...)` 允许 sync 或 async callback；`PregelLoop.run_step()` 会在需要时 `await callback(self)` | Java `PregelBuilder.build()` / `Pregel` 只接受 `Consumer<PregelLoop>`；`PregelLoop` 仅做同步 `accept(this)` | Python 里的 async super-step hook 不能直接迁移，只能由调用方自己再包一层线程/阻塞桥接 | Python：`openjiuwen/core/graph/pregel/builder.py`、`openjiuwen/core/graph/pregel/engine.py`；Java：`com/openjiuwen/core/graph/pregel/PregelBuilder.java`、`Pregel.java`、`PregelLoop.java` |

## 第二轮确认已补齐，不再视为缺漏

- `compile/context` 透传已经补齐：`PregelGraph.compile(BaseSession, Map<String, Object>)` 会在初始化 `Vertex` 时传入 `kwargs`，`Vertex.init()` 也已经读取 `"context"`。
- `TaskExecutorPool.waitAll()` 的 `FIRST_EXCEPTION` 语义已经补齐：Java 版现在会在首个失败出现后结束等待并取消剩余任务，而不是无条件等所有任务跑完。
- `Vertex._post_stream()` 的 LLM 输出回写已经补齐：Java `Vertex.postStream()` 现在会检查 `LLMExecutable` 并把 `getStreamOutput()` 回写到 workflow state。
- `Drawable.to_mermaid_png()` / `to_mermaid_svg()` 已经补齐：Java `Drawable` 现已公开 `toMermaidPng()` / `toMermaidSvg()`。
