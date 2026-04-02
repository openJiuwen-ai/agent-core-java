# com.openjiuwen.core.common.logging.events.RetrievalEvent

## 类 RetrievalEvent

```java
public class RetrievalEvent extends BaseLogEvent
```

`RetrievalEvent` 用于记录检索请求、候选上限、返回文档、得分和知识库上下文。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `retrievalType` | `String` | `retrieval_type` | 检索类型。 |
| `query` | `String` | `query` | 原始查询或请求文本。 |
| `topK` | `Integer` | `top_k` | 返回候选上限。 |
| `retrievedDocs` | `List<Map<String, Object>>` | `retrieved_docs` | 检索得到的文档列表。 |
| `retrievalScore` | `Double` | `retrieval_score` | 检索得分。 |
| `latencyMs` | `Double` | `latency_ms` | 请求耗时，单位毫秒。 |
| `knowledgeBaseId` | `String` | `knowledge_base_id` | 知识库标识。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.RETRIEVAL`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `RETRIEVAL_START`、`RETRIEVAL_END`、`RETRIEVAL_ERROR` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `query` 做脱敏。
