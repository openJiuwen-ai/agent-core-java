# com.openjiuwen.core.memory.prompt.PromptApplier

## class PromptApplier

```java
public class PromptApplier
```

Singleton prompt applier that loads .md prompt templates from classpath resources and applies variable substitution.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `PROMPT_RESOURCE_DIR` | `String` | prompt resource dir. |
| `instance` | `PromptApplier` | instance. |
| `promptCache` | `ConcurrentHashMap<String, PromptTemplate>` | prompt cache. |

## Constructors

| Signature | Description |
| --- | --- |
| `private PromptApplier()` | Create a new `PromptApplier` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static PromptApplier getInstance()` | Execute `getInstance`. |
| `public String apply(String filePrefix, Map<String, Object> variables)` | Execute `apply`. |
| `public void clearCache(String filePrefix)` | Execute `clearCache`. |
| `public void clearCache()` | Execute `clearCache`. |
| `public PromptTemplate getTemplate(String filePrefix)` | Execute `getTemplate`. |

## Notes

- Related tests: `PromptApplierTest.java`
