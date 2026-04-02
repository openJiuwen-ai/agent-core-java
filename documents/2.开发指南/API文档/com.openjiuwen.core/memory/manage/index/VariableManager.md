# com.openjiuwen.core.memory.manage.index.VariableManager

## class VariableManager

```java
public class VariableManager extends BaseMemoryManager
```

Manages variable memory using KV store.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `SEPARATOR` | `String` | separator. |
| `USER_VAR_PREFIX` | `String` | user var prefix. |
| `SESSION_VAR_PREFIX` | `String` | session var prefix. |
| `kvStore` | `BaseKVStore` | kv store. |
| `cryptoKey` | `byte[]` | crypto key. |

## Constructors

| Signature | Description |
| --- | --- |
| `public VariableManager(BaseKVStore kvStore, byte[] cryptoKey)` | Create a new `VariableManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | Execute `addMemories`. |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | Execute `update`. |
| `public void updateUserVariable(String userId, String scopeId, String varName, String varMem)` | Execute `updateUserVariable`. |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | Execute `delete`. |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | Execute `deleteByUserId`. |
| `public void deleteUserVariable(String userId, String scopeId, String varName)` | Execute `deleteUserVariable`. |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | Execute `get`. |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | Execute `search`. |
| `public Map<String, String> queryVariable(String userId, String scopeId, String name, String sessionId)` | Query variable by user_id, scope_id, variable_name. |
