# com.openjiuwen.core.memory.process.extract.ExtractMemoryParams

## 类 ExtractMemoryParams

```java
public class ExtractMemoryParams
```

`ExtractMemoryParams` 封装记忆提取阶段使用的用户、消息历史和基础模型参数。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | `String` | 当前用户标识。 |
| `scopeId` | `String` | 当前记忆作用域标识。 |
| `messages` | `List<BaseMessage>` | 本轮需要分析的消息列表。 |
| `historyMessages` | `List<BaseMessage>` | 可选的历史消息列表。 |
| `baseChatModel` | `Map.Entry<String, Model>` | 基础聊天模型，键为模型名，值为 `Model` 实例。 |

## 使用说明

- `Generator` 会通过 `builder()` 构建该对象，并传给 `LongTermMemoryExtractor.extractLongTermMemory(...)`。
- 该类使用 Lombok 的 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器与构建器。
