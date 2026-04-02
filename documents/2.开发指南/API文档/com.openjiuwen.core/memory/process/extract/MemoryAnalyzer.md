# com.openjiuwen.core.memory.process.extract.MemoryAnalyzer

## class MemoryAnalyzer

```java
public class MemoryAnalyzer
```

Analyzes conversation messages to determine key information, extract variables, and generate summary.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `MAPPER` | `ObjectMapper` | mapper. |

## Constructors

| Signature | Description |
| --- | --- |
| `private MemoryAnalyzer()` | Create a new `MemoryAnalyzer` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static MemoryAnalyzerResult analyze( List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken, int retries)` | Execute `analyze`. |
| `public static MemoryAnalyzerResult analyze( List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken)` | Execute `analyze`. |
