# com.openjiuwen.core.common.logging.events.PerformanceEvent

## 类 PerformanceEvent

```java
public class PerformanceEvent extends BaseLogEvent
```

`PerformanceEvent` 用于记录性能指标值、单位、资源类型和所属操作。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `metricName` | `String` | `metric_name` | 性能指标名称。 |
| `metricValue` | `Double` | `metric_value` | 指标值。 |
| `metricUnit` | `String` | `metric_unit` | 指标单位。 |
| `resourceType` | `String` | `resource_type` | 资源类型。 |
| `operation` | `String` | `operation` | 当前操作名称。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.SYSTEM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `PERFORMANCE_METRIC` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
