# com.openjiuwen.core.memory.manage.index.BaseMemoryManager

## class BaseMemoryManager

```java
public abstract class BaseMemoryManager
```

Abstract base class for memory manager implementations.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `NONCE_HEX_LENGTH` | `int` | nonce hex length. |
| `TAG_HEX_LENGTH` | `int` | tag hex length. |

## Methods

| Signature | Description |
| --- | --- |
| `public abstract void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | Add memories in batch. |
| `public abstract void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | Update memory by its id. |
| `public abstract boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | Delete memory by its id. |
| `public abstract boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | Delete memory by user id and scope id. |
| `public abstract Map<String, Object> get(String userId, String scopeId, String memId)` | Get memory by its id. |
| `public abstract List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | Query memory, return top k results. |
| `public static String encryptMemoryIfNeeded(byte[] key, String plaintext)` | Encrypt plaintext if a valid crypto key is provided. |
| `public static String decryptMemoryIfNeeded(byte[] key, String ciphertext)` | Decrypt ciphertext if a valid crypto key is provided. |
