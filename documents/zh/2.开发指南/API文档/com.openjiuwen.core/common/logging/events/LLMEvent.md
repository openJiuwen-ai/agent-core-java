# com.openjiuwen.core.common.logging.events.LLMEvent

## 类 LLMEvent

```java
public class LLMEvent extends BaseLogEvent
```

`LLMEvent` 承载模型调用请求、流式输出、工具调用、用量统计和重试配置等附加信息。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `modelName` | `String` | `model_name` | 模型名称。 |
| `modelProvider` | `String` | `model_provider` | 模型提供方标识。 |
| `query` | `String` | `query` | 原始查询或请求文本。 |
| `messages` | `List<Map<String, Object>>` | `messages` | 参与模型调用的消息列表。 |
| `tools` | `List<Map<String, Object>>` | `tools` | 调用时可用的工具描述列表。 |
| `temperature` | `Double` | `temperature` | 采样温度。 |
| `maxTokens` | `Integer` | `max_tokens` | 最大输出 token 数。 |
| `topP` | `Double` | `top_p` | top-p 采样参数。 |
| `responseContent` | `String` | `response_content` | 模型返回的主要文本内容。 |
| `toolCalls` | `List<Map<String, Object>>` | `tool_calls` | 模型返回的工具调用列表。 |
| `usage` | `Map<String, Object>` | `usage` | token 等用量统计。 |
| `latencyMs` | `Double` | `latency_ms` | 请求耗时，单位毫秒。 |
| `isStream` | `boolean` | `is_stream` | 是否以流式方式处理。 |
| `chunkIndex` | `Integer` | `chunk_index` | 当前流式片段序号。 |
| `extraParams` | `Map<String, Object>` | `extra_params` | 透传给模型调用的额外参数。 |
| `timeout` | `Double` | `timeout` | 超时时间，单位秒。 |
| `stop` | `String` | `stop` | 停止词或停止条件。 |
| `maxRetries` | `Integer` | `max_retries` | 最大重试次数。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.LLM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `LLM_CALL_START`、`LLM_CALL_END`、`LLM_CALL_ERROR`、`LLM_STREAM_CHUNK` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `query`、`messages`、`response_content`、`tool_calls` 做脱敏。
- `StructuredLogEventTest` 覆盖了 `modelName`、`query`、`responseContent`、`moduleType` 以及默认脱敏行为。
- `addFieldsToMap()` 会无条件输出 `is_stream` 键，其余字段仍遵循“非空才写入”的规则。
