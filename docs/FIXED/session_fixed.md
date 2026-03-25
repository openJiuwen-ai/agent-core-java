# Session 模块第二轮缺漏补充清单

## 说明

- 本文只记录第二轮逐文件复核后，确认仍然缺失或未完全对齐的部分。
- 仅名称变化、async -> sync、接口/工具类桥接，不再算作缺漏。

## 第二轮确认仍缺的部分

| 类别 | 缺口 | 现状说明 | 建议 |
| --- | --- | --- | --- |
| 持久化 checkpointer | `checkpointer.persistence` 整组实现缺失 | Python 有 `BaseStorage`、`AgentStorage`、`WorkflowStorage`、`GraphStore`、`PersistenceCheckpointer`、`PersistenceCheckpointerProvider`、`_enable_sqlite_wal`；Java 只有 `InMemoryCheckpointer` 主线 | 优先补 `PersistenceCheckpointer`、`PersistenceCheckpointerProvider`，再补 storage 分层 |
| checkpointer 配置入口 | `CheckpointerConfig` 缺失 | Python `CheckpointerFactory.create()` 接 `CheckpointerConfig`；Java 只有 `create(String type, Map<String,Object> conf)` | 新增 Java 配置对象，保持工厂入口一致 |
| tracer 装饰器 API | `tracer.decorator` 函数族缺失 | `decorate_model_with_trace`、`decorate_tool_with_trace`、`decorate_workflow_with_trace`、`trace`、`async_trace`、`trace_stream`、`async_trace_stream` 都没有 Java 对位 | 先补公开包装入口，再考虑实现方式是代理还是显式 wrapper |
| tracer 事件链 | `TraceAgentHandler.on_llm_request` 缺失 | Python 能记录 LLM request 阶段增量数据；Java 只有 start/end/error | 给 `TraceAgentHandler` 增加 request 事件和 span 更新逻辑 |
| NodeSession trace 开关 | `skip_trace` 构造参数与 `skip_trace()` 缺失 | Python `NodeSession` 支持跳过 trace；Java `NodeSessionApi` 也因此无法先判断再 trace | 给 `NodeSession` 补字段、构造参数、accessor，并修改 `NodeSessionApi` |
| 子工作流关闭语义 | `SubWorkflowSession.close()` 缺失 | Python 关闭子工作流会 shutdown actor manager；Java 目前没有 override | 在 `SubWorkflowSession` 中补 close 逻辑 |
| 配置上下文覆盖 | `workflow_session_vars` 缺失 | Python 可用 contextvars 覆盖 env 配置；Java 仅读 `System.getenv()` | 视设计决定是否用 `ThreadLocal` 或显式上下文对象对位 |
| utils 包装 helper | `create_wrapper_class()` 缺失 | Python tracer decorator 直接依赖该 helper；Java 没有 | 若补 tracer decorator，这个 helper 需要一起补 |
| utils 路径 helper | `root_to_index()`、`_safe_extend_container()` 缺失 | Python 对 list 路径导航有独立 helper；Java 只在 `rootToPath(...)` 中内联处理 | 若要做严格 API 对齐，需要公开补齐 |
| utils 可见性 | `delete_by_key()`、`update_by_key()` 仅保留私有实现 | Java `SessionUtils` 内部有私有 `deleteByKey` / `updateByKey`，但不是公开 API | 如果文档目标是公开 API 完全一致，需要提升可见性或提供公开包装 |
| BaseSession 抽象面 | `actor_manager()` 缺失 | Python `BaseSession` 定义了该抽象方法；Java 只在少数具体类里有 `actorManager()` | 若上层代码希望按 `BaseSession` 多态调用，需要补到基类 |

## 第二轮确认不属于缺漏的项

- `BaseStreamMode`：Java 已被 `StreamMode` enum 吸收。
- `StreamSchemas`：Java 用 `StreamSchema` 接口承载联合类型语义。
- `OutputStreamWriter` / `TraceStreamWriter` / `CustomStreamWriter`：Java 用泛型 `StreamWriter` 统一承载。
- `trigger_event`：Java `@TriggerEvent` 已完成语义替换。
- `Transformer`：Java 用 `Function<Object, Object>` 承载。
- 外部 Session API 中大量 async 方法：Java 同步化实现属于执行模型差异，不单独计缺。

## 建议处理顺序

1. 先补 `checkpointer.persistence`、`CheckpointerConfig`、`tracer.decorator`、`on_llm_request`。
2. 再补 `NodeSession.skipTrace` 与 `SubWorkflowSession.close()` 这些会影响运行行为的一致性问题。
3. 最后处理 `workflow_session_vars` 与 utils 公开 helper，作为 API 完整性收尾。