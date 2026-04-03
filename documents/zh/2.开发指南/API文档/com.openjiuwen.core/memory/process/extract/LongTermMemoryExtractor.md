# com.openjiuwen.core.memory.process.extract.LongTermMemoryExtractor

## 类 LongTermMemoryExtractor

```java
public class LongTermMemoryExtractor
```

`LongTermMemoryExtractor` 使用提示词与模型调用，把消息提取为长期片段记忆的分类结果。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 长期记忆提取流程使用的日志记录器。 |
| `MAPPER` | `ObjectMapper` | Jackson `ObjectMapper` 实例。当前源码中定义但未直接参与公开流程。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, List<String>> extractLongTermMemory(ExtractMemoryParams params, String timestamp, int retries)` | 组装历史与当前消息，调用模型并解析 JSON 结果；失败时按 `retries` 次数重试。 |
| `public static Map<String, List<String>> extractLongTermMemory(ExtractMemoryParams params, String timestamp)` | 使用默认重试次数 `3` 执行长期记忆提取。 |

## 行为说明

- 提示词通过 `PromptApplier.getInstance().apply("fragment_memory_prompt", variables)` 生成。
- 输入模型的消息固定为单条 `UserMessage`，内容是拼接后的提示词文本。
- 只有模型输出能被 `JsonOutputParser` 解析成 `Map` 时才返回结果；连续失败后返回空映射。
