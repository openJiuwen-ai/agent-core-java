# com.openjiuwen.core.memory.process.extract.Generator

## class Generator

```java
public class Generator
```

Generates all memory units (variables, summary, fragment) from conversation messages.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `dataIdGenerator` | `DataIdManager` | data id generator. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Generator(DataIdManager dataIdGenerator)` | Create a new `Generator` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, List<BaseMemoryUnit>> genAllMemory(Map<String, Object> kwargs)` | Execute `genAllMemory`. |
