# com.openjiuwen.core.memory.manage.index.FragmentMemoryManager

## class FragmentMemoryManager

```java
public class FragmentMemoryManager extends BaseMemoryManager
```

Manages fragment (user profile) memory CRUD with encryption and vector storage.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `UPDATE_CHECK_OLD_MEMORY_NUM` | `int` | update check old memory num. |
| `UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD` | `double` | update check old memory relevance threshold. |
| `memStore` | `UserMemStore` | mem store. |
| `dataIdGenerator` | `DataIdManager` | data id generator. |
| `cryptoKey` | `byte[]` | crypto key. |

## Constructors

| Signature | Description |
| --- | --- |
| `public FragmentMemoryManager(UserMemStore memStore, DataIdManager dataIdGenerator, byte[] cryptoKey)` | Create a new `FragmentMemoryManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | Execute `addMemories`. |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | Execute `update`. |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | Execute `search`. |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | Execute `get`. |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | Execute `delete`. |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | Execute `deleteByUserId`. |
| `public List<Map<String, Object>> listFragmentMemories(String userId, String scopeId, String profileType)` | Execute `listFragmentMemories`. |
