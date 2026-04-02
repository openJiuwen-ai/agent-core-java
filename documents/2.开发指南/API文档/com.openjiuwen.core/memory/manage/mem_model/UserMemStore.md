# com.openjiuwen.core.memory.manage.mem_model.UserMemStore

## 类 UserMemStore

```java
public class UserMemStore
```

`UserMemStore` 是 `com.openjiuwen.core.memory.manage.mem_model` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `MAPPER` | `ObjectMapper` | JSON 映射器。 |
| `BYTE_NUM_PER_ID` | `int` | 字段 `BYTE_NUM_PER_ID`。 |
| `IDS_STR` | `String` | 字段 `IDS_STR`。 |
| `USER_PROFILE_TOPIC_STR` | `String` | 字段 `USER_PROFILE_TOPIC_STR`。 |
| `KEY_PREFIX_STR` | `String` | 字段 `KEY_PREFIX_STR`。 |
| `MEM_TYPE_FIELD_KEY` | `String` | 字段 `MEM_TYPE_FIELD_KEY`。 |
| `TOPIC_FIELD_KEY` | `String` | 字段 `TOPIC_FIELD_KEY`。 |
| `SEPARATOR` | `String` | 字段 `SEPARATOR`。 |
| `kvStore` | `BaseKVStore` | KV 存储。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public UserMemStore(BaseKVStore kvStore)` | 创建 `UserMemStore` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean write(String userId, String scopeId, String memId, Map<String, Object> data)` | 执行 `write` 写入流程。 |
| `public boolean update(String userId, String scopeId, String memId, Map<String, Object> data)` | 执行 `update` 更新流程。 |
| `public void delete(String userId, String scopeId, String memId)` | 执行 `delete` 删除流程。 |
| `public void batchDelete(String userId, String scopeId, List<String> memIds)` | 执行 `batchDelete`。 |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | 返回 `get` 的执行结果。 |
| `public List<Map<String, Object>> batchGet(String userId, String scopeId, List<String> memIds)` | 执行 `batchGet`。 |
| `public List<Map<String, Object>> getAll(String userId, String scopeId, String memType)` | 返回 `getAll` 的执行结果。 |
| `public List<Map<String, Object>> getByTopic(String userId, String scopeId, String topic)` | 返回 `getByTopic` 的执行结果。 |
| `public List<Map<String, Object>> getInRange(String userId, String scopeId, int startIdx, int endIdx, String memType)` | 返回 `getInRange` 的执行结果。 |
