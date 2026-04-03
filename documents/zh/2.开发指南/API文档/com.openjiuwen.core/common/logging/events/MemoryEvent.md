# com.openjiuwen.core.common.logging.events.MemoryEvent

## 类 MemoryEvent

```java
public class MemoryEvent extends BaseLogEvent
```

`MemoryEvent` 用于记录记忆存取、更新、处理和检索结果统计。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `memoryType` | `String` | `memory_type` | 记忆类型。 |
| `operation` | `String` | `operation` | 当前操作名称。 |
| `memoryId` | `List<String>` | `memory_id` | 涉及的记忆 ID 列表。 |
| `query` | `String` | `query` | 原始查询或请求文本。 |
| `memoryCount` | `Integer` | `memory_count` | 记忆条目数量。 |
| `retrievedMemories` | `List<Map<String, Object>>` | `retrieved_memories` | 检索得到的记忆结果列表。 |
| `storageSizeBytes` | `Integer` | `storage_size_bytes` | 存储大小，单位字节。 |
| `userId` | `String` | `user_id` | 用户标识。 |
| `scopeId` | `String` | `scope_id` | 记忆范围标识。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.MEMORY`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `MEMORY_PROCESS`、`MEMORY_STORE`、`MEMORY_RETRIEVE`、`MEMORY_DELETE`、`MEMORY_UPDATE` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `query`、`retrieved_memories` 做脱敏。
