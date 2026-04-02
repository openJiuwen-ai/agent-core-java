# com.openjiuwen.core.memory.manage.update.MemUpdateChecker

## class MemUpdateChecker

```java
public class MemUpdateChecker
```

Memory update checker for detecting redundancy and conflicts between memories.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `promptApplier` | `PromptApplier` | prompt applier. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MemUpdateChecker()` | Create a new `MemUpdateChecker` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel)` | Check for redundancy and conflicts between new and old memories. |
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel, int retries)` | Execute `check`. |

## Notes

- Related tests: `MemUpdateCheckerTest.java`
