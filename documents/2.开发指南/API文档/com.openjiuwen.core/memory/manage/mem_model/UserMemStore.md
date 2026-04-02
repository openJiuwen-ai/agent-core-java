# com.openjiuwen.core.memory.manage.mem_model.UserMemStore

## class UserMemStore

```java
public class UserMemStore
```

KV-based memory data storage with ID index management.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `MAPPER` | `ObjectMapper` | mapper. |
| `BYTE_NUM_PER_ID` | `int` | byte num per id. |
| `IDS_STR` | `String` | ids str. |
| `USER_PROFILE_TOPIC_STR` | `String` | user profile topic str. |
| `KEY_PREFIX_STR` | `String` | key prefix str. |
| `MEM_TYPE_FIELD_KEY` | `String` | mem type field key. |
| `TOPIC_FIELD_KEY` | `String` | topic field key. |
| `SEPARATOR` | `String` | separator. |
| `kvStore` | `BaseKVStore` | kv store. |

## Constructors

| Signature | Description |
| --- | --- |
| `public UserMemStore(BaseKVStore kvStore)` | Create a new `UserMemStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean write(String userId, String scopeId, String memId, Map<String, Object> data)` | Execute `write`. |
| `public boolean update(String userId, String scopeId, String memId, Map<String, Object> data)` | Execute `update`. |
| `public void delete(String userId, String scopeId, String memId)` | Execute `delete`. |
| `public void batchDelete(String userId, String scopeId, List<String> memIds)` | Execute `batchDelete`. |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | Execute `get`. |
| `public List<Map<String, Object>> batchGet(String userId, String scopeId, List<String> memIds)` | Execute `batchGet`. |
| `public List<Map<String, Object>> getAll(String userId, String scopeId, String memType)` | Execute `getAll`. |
| `public List<Map<String, Object>> getByTopic(String userId, String scopeId, String topic)` | Execute `getByTopic`. |
| `public List<Map<String, Object>> getInRange(String userId, String scopeId, int startIdx, int endIdx, String memType)` | Execute `getInRange`. |
