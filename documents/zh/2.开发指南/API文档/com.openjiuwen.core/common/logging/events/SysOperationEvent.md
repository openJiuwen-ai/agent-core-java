# com.openjiuwen.core.common.logging.events.SysOperationEvent

## 类 SysOperationEvent

```java
public class SysOperationEvent extends BaseLogEvent
```

`SysOperationEvent` 用于记录系统操作的模式、说明、方法参数、返回结果和执行耗时。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `operationName` | `String` | `operation_name` | 系统操作名称。 |
| `operationMode` | `String` | `operation_mode` | 执行模式。 |
| `operationDesc` | `String` | `operation_desc` | 操作说明。 |
| `methodName` | `String` | `method_name` | 调用的方法名。 |
| `methodParams` | `Map<String, Object>` | `method_params` | 方法参数快照。 |
| `methodResult` | `Map<String, Object>` | `method_result` | 方法返回结果。 |
| `methodExecTimeMs` | `Double` | `method_exec_time_ms` | 方法执行耗时，单位毫秒。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.SYS_OPERATION`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `SYS_OP_START`、`SYS_OP_END`、`SYS_OP_ERROR`、`SYS_OP_STREAM` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
