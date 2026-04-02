# com.openjiuwen.core.memory.manage.index.SummaryManager

## class SummaryManager

```java
public class SummaryManager extends BaseMemoryManager
```

Manages summary memory CRUD with encryption and vector storage.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `memStore` | `UserMemStore` | mem store. |
| `cryptoKey` | `byte[]` | crypto key. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SummaryManager(UserMemStore memStore, byte[] cryptoKey)` | Create a new `SummaryManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | Execute `addMemories`. |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | Execute `update`. |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | Execute `delete`. |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | Execute `deleteByUserId`. |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | Execute `get`. |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | Execute `search`. |
