# com.openjiuwen.core.memory.manage.index.BaseMemoryManager

## 类 BaseMemoryManager

```java
public abstract class BaseMemoryManager
```

`BaseMemoryManager` 是 `com.openjiuwen.core.memory.manage.index` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `NONCE_HEX_LENGTH` | `int` | 字段 `NONCE_HEX_LENGTH`。 |
| `TAG_HEX_LENGTH` | `int` | 字段 `TAG_HEX_LENGTH`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public abstract void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | 执行 `addMemories` 写入流程。 |
| `public abstract void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | 执行 `update` 更新流程。 |
| `public abstract boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | 执行 `delete` 删除流程。 |
| `public abstract boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | 执行 `deleteByUserId` 删除流程。 |
| `public abstract Map<String, Object> get(String userId, String scopeId, String memId)` | 返回 `get` 的执行结果。 |
| `public abstract List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | 执行 `search` 查询流程。 |
| `public static String encryptMemoryIfNeeded(byte[] key, String plaintext)` | 执行 `encryptMemoryIfNeeded`。 |
| `public static String decryptMemoryIfNeeded(byte[] key, String ciphertext)` | 执行 `decryptMemoryIfNeeded`。 |
