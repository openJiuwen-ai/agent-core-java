# com.openjiuwen.core.session.tracer.TraceAgentSpan

## 类 TraceAgentSpan

```java
public class TraceAgentSpan extends Span
```

`TraceAgentSpan` 在基础 `Span` 上增加了 agent 调用类型、显示名称、耗时字符串和实例元数据。

## 主要属性

| 属性 | 说明 |
| --- | --- |
| `invokeType` | 调用类型字符串，如 `llm`、`plugin`、`workflow`。 |
| `name` | 被追踪对象的名称。 |
| `elapsedTime` | 形如 `120ms` 或 `1.20s` 的耗时字符串。 |
| `metaData` | 实例元数据，例如 `class_name`、`type` 等。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TraceAgentSpan()` | 创建空 agent span。 |
| `public TraceAgentSpan(String traceId, String invokeId, String parentInvokeId)` | 使用 trace / 调用标识创建 agent span。 |

## 主要方法

| 签名 | 说明 |
| --- | --- |
| `public TraceAgentSpan snapshot()` | 生成当前 agent span 的深拷贝快照。 |

## 说明

- 相关测试：`TracerTest`。
- 该类为上述属性提供标准 getter / setter。
- 内部字段更新同时兼容 `invoke_type` / `invokeType`、`elapsed_time` / `elapsedTime`、`meta_data` / `metaData`。
