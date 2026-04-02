# tracer

`com.openjiuwen.core.session.tracer` 提供 trace span、handler、装饰器与 workflow/agent 级工具方法，用来把执行过程转换为可写流、可回调的追踪数据。

## Types

| 类型 | 说明 |
| --- | --- |
| [`InvokeType`](./tracer/InvokeType.md) | agent 级调用类型枚举。 |
| [`NodeStatus`](./tracer/NodeStatus.md) | workflow 节点追踪状态枚举。 |
| [`Span`](./tracer/Span.md) | 保存通用 trace 字段的基础 span。 |
| [`SpanManager`](./tracer/SpanManager.md) | 维护当前 trace 会话中的 span 顺序与父子关系。 |
| [`TraceAgentHandler`](./tracer/TraceAgentHandler.md) | 处理 chain、llm、prompt、plugin、retriever、evaluator、workflow 等 agent 级事件。 |
| [`TraceAgentSpan`](./tracer/TraceAgentSpan.md) | agent 调用使用的 span 扩展。 |
| [`TraceBaseHandler`](./tracer/TraceBaseHandler.md) | handler 共用的 trace 写流、耗时计算与状态推导基类。 |
| [`TraceWorkflowHandler`](./tracer/TraceWorkflowHandler.md) | 处理 workflow 组件生命周期事件的 handler。 |
| [`TraceWorkflowSpan`](./tracer/TraceWorkflowSpan.md) | workflow / 组件调用使用的 span 扩展。 |
| [`Tracer`](./tracer/Tracer.md) | 协调 agent 与 workflow span manager 的中心对象。 |
| [`TracerDecorator`](./tracer/TracerDecorator.md) | 为 model、tool、workflow 包装 trace 逻辑的装饰器工具类。 |
| [`TracerHandlerName`](./tracer/TracerHandlerName.md) | tracer 回调 handler 名称枚举。 |
| [`TracerWorkflowUtils`](./tracer/TracerWorkflowUtils.md) | workflow 侧上报 trace 事件的静态工具方法。 |

## 说明

- 相关测试：`TracerDecoratorTest`、`TracerTest`。
