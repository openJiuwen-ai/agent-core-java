# com.openjiuwen.core.memory.manage.index.WriteManager

## class WriteManager

```java
public class WriteManager
```

Orchestrates memory write operations across all memory type managers.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `managers` | `Map<String, BaseMemoryManager>` | managers. |
| `memStore` | `UserMemStore` | mem store. |

## Constructors

| Signature | Description |
| --- | --- |
| `public WriteManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore)` | Create a new `WriteManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addMemories(String userId, String scopeId, Map<String, ? extends List<? extends BaseMemoryUnit>> memories, Map.Entry<String, Model> llm, SemanticStore semanticStore)` | Add memories of different types in batch. |
| `public void updateMemById(String userId, String scopeId, String memId, String memory, SemanticStore semanticStore)` | Update a memory by ID (determines type from store). |
| `public void deleteMemById(String userId, String scopeId, String memId, SemanticStore semanticStore)` | Delete a memory by ID (determines type from store). |
| `public void deleteMemByUserId(String userId, String scopeId, SemanticStore semanticStore)` | Delete all memories for a user across all types. |
