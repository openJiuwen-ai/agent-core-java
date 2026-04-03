# com.openjiuwen.core.session.tracer.SpanManager

## 类 SpanManager

```java
public class SpanManager
```

`SpanManager` 维护当前 trace 会话的 span 顺序、索引和父子关系。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SpanManager(String traceId)` | 创建没有父节点上下文的 `SpanManager`。 |
| `public SpanManager(String traceId, String parentNodeId)` | 创建绑定父节点 ID 的 `SpanManager`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Span getSpan(String invokeId)` | 按 `invokeId` 读取 span；若不在顺序表中则返回 `null`。 |
| `public void popSpan(String invokeId)` | 从顺序表与索引中移除指定 span。 |
| `public void refreshSpanRecord(String invokeId, Span span)` | 新增或更新指定 `invokeId` 的 span 记录。 |
| `public TraceAgentSpan createAgentSpan(Span parentSpan)` | 创建 agent span；若有父 span，会自动建立父子关系。 |
| `public TraceWorkflowSpan createWorkflowSpan(String invokeId, Span parentSpan)` | 用显式 `invokeId` 创建 workflow span，并按需要连接父 span。 |
| `public void updateSpan(Span span, Map<String, Object> data)` | 把数据更新到 span，并刷新记录。 |
| `public Span getLastSpan()` | 返回最近加入顺序表的 span。 |
| `public String getTraceId()` | 返回 trace ID。 |
| `public String getParentNodeId()` | 返回绑定的父节点 ID。 |

## 说明

- 相关测试：`TracerTest`。
- `createAgentSpan()` 使用随机 UUID 生成 `invokeId`；`createWorkflowSpan()` 则直接使用调用方传入的 `invokeId`。
