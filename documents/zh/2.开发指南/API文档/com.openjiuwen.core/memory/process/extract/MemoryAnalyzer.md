# com.openjiuwen.core.memory.process.extract.MemoryAnalyzer

## 类 MemoryAnalyzer

```java
public class MemoryAnalyzer
```

`MemoryAnalyzer` 负责分析对话内容，输出是否包含关键信息、变量提取结果以及摘要文本。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆分析流程使用的日志记录器。 |
| `MAPPER` | `ObjectMapper` | 用于序列化变量定义与输出模板的 Jackson 对象。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static MemoryAnalyzerResult analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken, int retries)` | 构造分析提示词并调用模型，解析 `has_key_information`、`variables`、`summary` 字段，必要时按 `retries` 次重试。 |
| `public static MemoryAnalyzerResult analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken)` | 使用默认重试次数 `3` 执行分析。 |

## 行为说明

- 当 `messages` 为空时直接记录警告并返回 `null`。
- `memoryConfig.getMemVariables()` 会被转换为变量定义模板与输出模板，再注入 `memory_analysis_prompt`。
- 如果长期记忆或摘要记忆未启用，返回结果中的 `summary` 会被清空。
