# com.openjiuwen.core.memory.manage.search.SearchManager

## class SearchManager

```java
public class SearchManager
```

Orchestrates memory search across different memory type managers.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `USER_MEM_MANAGER_LIST` | `Set<String>` | user mem manager list. |
| `ALL_MEM_MANAGER_LIST` | `Set<String>` | all mem manager list. |
| `managers` | `Map<String, BaseMemoryManager>` | managers. |
| `memStore` | `UserMemStore` | mem store. |
| `cryptoKey` | `byte[]` | crypto key. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SearchManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore, byte[] cryptoKey)` | Create a new `SearchManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Map<String, Object>> search(SearchParams params, SemanticStore semanticStore)` | Execute `search`. |
| `public List<Map<String, Object>> listUserMem(String userId, String scopeId, int nums, int pages, String memType)` | Execute `listUserMem`. |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId, String profileType)` | Execute `listUserProfile`. |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId)` | Execute `listUserProfile`. |
| `public String getUserVariable(String userId, String scopeId, String varName)` | Execute `getUserVariable`. |
| `public Map<String, String> getAllUserVariable(String userId, String scopeId)` | Execute `getAllUserVariable`. |

## Notes

- Related tests: `SearchManagerTest.java`
