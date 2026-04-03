# com.openjiuwen.core.common.logging.events.SystemEvent

## 类 SystemEvent

```java
public class SystemEvent extends BaseLogEvent
```

`SystemEvent` 用于记录系统启动、关闭或错误时的版本、配置和资源使用信息。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `systemVersion` | `String` | `system_version` | 系统版本。 |
| `systemConfig` | `Map<String, Object>` | `system_config` | 系统配置快照。 |
| `resourceUsage` | `Map<String, Object>` | `resource_usage` | 资源使用统计。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.SYSTEM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `SYSTEM_START`、`SYSTEM_SHUTDOWN`、`SYSTEM_ERROR` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
