# com.openjiuwen.core.session.tracer.Tracer

## 类 Tracer

```java
public class Tracer
```

`Tracer` 是 agent / workflow trace 的中心协调器，负责维护根 trace ID、agent span manager 与按父节点拆分的 workflow span manager。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Tracer()` | 创建新的 tracer，并初始化 agent 级 `SpanManager`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void init(StreamWriterManager streamWriterManager, CallbackManager callbackManager)` | 初始化 tracer，并注册默认的 `TraceAgentHandler` 与根 `TraceWorkflowHandler`。 |
| `public void registerWorkflowSpanManager(String parentNodeId)` | 为指定父节点创建独立的 workflow `SpanManager` 和对应 handler。 |
| `public TraceWorkflowSpan getWorkflowSpan(String invokeId, String parentNodeId)` | 按父节点 ID 与 `invokeId` 读取 workflow span；不存在时返回 `null`。 |
| `public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | 通过 `CallbackManager` 触发 tracer 事件；若 `kwargs.parent_node_id` 非空，会自动拼接 handler 后缀。 |
| `public void popWorkflowSpan(String invokeId, String parentNodeId)` | 从指定父节点的 workflow `SpanManager` 中移除 span。 |
| `public String getTraceId()` | 返回 tracer 的全局 trace ID。 |
| `public SpanManager getTracerAgentSpanManager()` | 返回 agent 级 `SpanManager`。 |
| `public Map<String, SpanManager> getTracerWorkflowSpanManagerDict()` | 返回 workflow `SpanManager` 字典。 |

## 说明

- 相关测试：`TracerDecoratorTest`、`TracerTest`。
- `init()` 会为根 workflow 注册键名为 `tracer_workflow` 的 handler；`registerWorkflowSpanManager()` 会注册键名为 `tracer_workflow.<parentNodeId>` 的 handler。
