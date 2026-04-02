# com.openjiuwen.core.memory.manage.index.VariableManager

## 类 VariableManager

```java
public class VariableManager extends BaseMemoryManager
```

`VariableManager` 是 `com.openjiuwen.core.memory.manage.index` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `SEPARATOR` | `String` | 字段 `SEPARATOR`。 |
| `USER_VAR_PREFIX` | `String` | 字段 `USER_VAR_PREFIX`。 |
| `SESSION_VAR_PREFIX` | `String` | 字段 `SESSION_VAR_PREFIX`。 |
| `kvStore` | `BaseKVStore` | KV 存储。 |
| `cryptoKey` | `byte[]` | 字段 `cryptoKey`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public VariableManager(BaseKVStore kvStore, byte[] cryptoKey)` | 创建 `VariableManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | 执行 `addMemories` 写入流程。 |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | 执行 `update` 更新流程。 |
| `public void updateUserVariable(String userId, String scopeId, String varName, String varMem)` | 执行 `updateUserVariable` 更新流程。 |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | 执行 `delete` 删除流程。 |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | 执行 `deleteByUserId` 删除流程。 |
| `public void deleteUserVariable(String userId, String scopeId, String varName)` | 执行 `deleteUserVariable` 删除流程。 |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | 返回 `get` 的执行结果。 |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | 执行 `search` 查询流程。 |
| `public Map<String, String> queryVariable(String userId, String scopeId, String name, String sessionId)` | 执行 `queryVariable`。 |
