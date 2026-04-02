# com.openjiuwen.core.memory.process.extract.LongTermMemoryExtractor

## class LongTermMemoryExtractor

```java
public class LongTermMemoryExtractor
```

Extracts long-term memory (fragment memories) from conversation using LLM.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `MAPPER` | `ObjectMapper` | mapper. |

## Constructors

| Signature | Description |
| --- | --- |
| `private LongTermMemoryExtractor()` | Create a new `LongTermMemoryExtractor` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static Map<String, List<String>> extractLongTermMemory( ExtractMemoryParams params, String timestamp, int retries)` | Execute `extractLongTermMemory`. |
| `public static Map<String, List<String>> extractLongTermMemory( ExtractMemoryParams params, String timestamp)` | Execute `extractLongTermMemory`. |
