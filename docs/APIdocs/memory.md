# Memory 模块 API 文档

> 包路径：`com.openjiuwen.core.memory`

长期记忆、语义检索、迁移、提示词与更新流水线。基于 `memory` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `54` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.memory` | 3 |
| `com.openjiuwen.core.memory.common` | 5 |
| `com.openjiuwen.core.memory.config` | 3 |
| `com.openjiuwen.core.memory.manage.index` | 5 |
| `com.openjiuwen.core.memory.manage.mem_model` | 15 |
| `com.openjiuwen.core.memory.manage.search` | 2 |
| `com.openjiuwen.core.memory.manage.update` | 5 |
| `com.openjiuwen.core.memory.migration` | 2 |
| `com.openjiuwen.core.memory.migration.migrator` | 4 |
| `com.openjiuwen.core.memory.migration.operation` | 3 |
| `com.openjiuwen.core.memory.process.extract` | 6 |
| `com.openjiuwen.core.memory.prompt` | 1 |

## `com.openjiuwen.core.memory`

公开类型：`3`

### `LongTermMemory`

- 类型：`class`
- 声明：`public class LongTermMemory`
- 说明：Main memory engine implementing long-term memory management.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `DEFAULT_VALUE` | `String` | `public static final` | `"__default__"` | - |
| `SCOPE_CONFIG_KEY` | `String` | `public static final` | `"memory_scope_config"` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static LongTermMemory getInstance()` | `LongTermMemory` | - |
| `public static void resetInstance()` | `void` | Reset singleton for testing. |
| `public void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embeddingModel)` | `void` | - |
| `public void setConfig(MemoryEngineConfig config)` | `void` | - |
| `public boolean setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig)` | `boolean` | - |
| `public MemoryScopeConfig getScopeConfig(String scopeId)` | `MemoryScopeConfig` | - |
| `public boolean deleteScopeConfig(String scopeId)` | `boolean` | - |
| `public boolean deleteMemByScope(String scopeId)` | `boolean` | - |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId, OffsetDateTime timestamp, boolean genMem, int genMemWithHistoryMsgNum)` | `void` | - |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId)` | `void` | - |
| `public List<BaseMessage> getRecentMessages(String userId, String scopeId, String sessionId, int num)` | `List<BaseMessage>` | - |
| `public MessageManager.MessageRecord getMessageById(String msgId)` | `MessageManager.MessageRecord` | - |
| `public void deleteMessagesByUserAndScope(String userId, String scopeId)` | `void` | - |
| `public void deleteMemById(String memId, String userId, String scopeId)` | `void` | - |
| `public void deleteMemByUserId(String userId, String scopeId)` | `void` | - |
| `public void updateMemById(String memId, String memory, String userId, String scopeId)` | `void` | - |
| `public Map<String, String> getVariables(Object names, String userId, String scopeId)` | `Map<String, String>` | - |
| `public void updateVariables(Map<String, String> variables, String userId, String scopeId)` | `void` | - |
| `public boolean deleteVariables(List<String> names, String userId, String scopeId)` | `boolean` | - |
| `public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold)` | `List<MemResult>` | - |
| `public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold)` | `List<MemResult>` | - |
| `public int userMemTotalNum(String userId, String scopeId)` | `int` | - |
| `public List<MemInfo> getUserMemByPage(String userId, String scopeId, int pageSize, int pageIdx, MemoryType memoryType)` | `List<MemInfo>` | - |

### `MemInfo`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemInfo`
- 说明：Memory information containing id, content, and type.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memId` | `String` | `private` | `""` | - |
| `content` | `String` | `private` | `""` | - |
| `type` | `MemoryType` | `private` | `MemoryType.FRAGMENT_MEMORY` | - |

### `MemResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemResult`
- 说明：Memory search result with relevance score.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memInfo` | `MemInfo` | `private` | `-` | - |
| `score` | `double` | `private` | `0.0` | - |

## `com.openjiuwen.core.memory.common`

公开类型：`5`

### `DistributedLock`

- 类型：`class`
- 声明：`public class DistributedLock implements AutoCloseable`
- 说明：Synchronous distributed lock using KV store exclusive_set.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DistributedLock(BaseKVStore store, String lockName)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void acquire()` | `void` | - |
| `public void release()` | `void` | - |
| `public void close()` | `void` | - |

### `KvPrefixRegistry`

- 类型：`class`
- 声明：`public final class KvPrefixRegistry`
- 说明：Registry for managing KV store key prefixes used by memory modules.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static KvPrefixRegistry getInstance()` | `KvPrefixRegistry` | - |
| `public synchronized void registerCurrent(String prefix)` | `void` | Register a current (active) key prefix used by a memory module. |
| `public synchronized void registerLegacy(String prefix)` | `void` | Register a legacy (deprecated) key prefix for migration detection. |
| `public synchronized Set<String> getAllPrefixes()` | `Set<String>` | Get all registered prefixes (both current and legacy). |
| `public synchronized void unregister(String prefix)` | `void` | Unregister a prefix from both current and all prefixes. |

### `MemoryCrypto`

- 类型：`class`
- 声明：`public final class MemoryCrypto`
- 说明：AES-256-GCM encryption/decryption utilities for memory content.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `NONCE_LENGTH` | `int` | `public static final` | `12` | - |
| `TAG_LENGTH` | `int` | `public static final` | `16` | - |
| `AES_KEY_LENGTH` | `int` | `public static final` | `32` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String[] encrypt(byte[] key, String plaintext)` | `String[]` | Encrypt plaintext using AES-256-GCM. |
| `public static String decrypt(byte[] key, String ciphertext, String nonce, String tag)` | `String` | Decrypt ciphertext using AES-256-GCM. |

### `MemoryUtils`

- 类型：`class`
- 声明：`public final class MemoryUtils`
- 说明：Utility methods for memory module.
- 嵌套公开类型：`MemoryUtils.HitParseResult`

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String generateIdxName(String userId, String scopeId, String memType)` | `String` | Generate vector index name from user id, scope id and memory type. |
| `public static String parseMemTypeFromIdxName(String idxName)` | `String` | Parse memory type from vector index name. |
| `public static HitParseResult parseMemoryHitInfos(List<Map.Entry<String, Double>> hits)` | `HitParseResult` | Parse memory hit infos from search results. |

### `MemoryUtils.HitParseResult`

- 类型：`class`
- 声明：`public static class HitParseResult`
- 说明：Result of parsing memory hit infos.
- 宿主类型：`MemoryUtils`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ids` | `List<String>` | `private final` | `-` | - |
| `scores` | `Map<String, Double>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public HitParseResult(List<String> ids, Map<String, Double> scores)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> getIds()` | `List<String>` | - |
| `public List<String> ids()` | `List<String>` | - |
| `public Map<String, Double> getScores()` | `Map<String, Double>` | - |
| `public Map<String, Double> scores()` | `Map<String, Double>` | - |

## `com.openjiuwen.core.memory.config`

公开类型：`3`

### `AgentMemoryConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class AgentMemoryConfig`
- 说明：Agent memory configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memVariables` | `List<Param>` | `private` | `new ArrayList<>()` | - |
| `enableLongTermMem` | `boolean` | `private` | `true` | - |
| `enableFragmentMemory` | `boolean` | `private` | `true` | - |
| `enableSummaryMemory` | `boolean` | `private` | `true` | - |

### `MemoryEngineConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemoryEngineConfig`
- 说明：Memory engine configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `defaultModelCfg` | `ModelRequestConfig` | `private` | `-` | - |
| `defaultModelClientCfg` | `ModelClientConfig` | `private` | `-` | - |
| `inputMsgMaxLen` | `int` | `private` | `8192` | - |
| `cryptoKey` | `byte[]` | `private` | `new byte[0]` | - |
| `singleTurnHistorySummaryMaxToken` | `int` | `private` | `128` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void validateCryptoKey()` | `void` | Validate crypto key: must be empty or exactly 32 bytes. |

### `MemoryScopeConfig`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemoryScopeConfig`
- 说明：Scope-specific memory configuration.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelCfg` | `ModelRequestConfig` | `private` | `-` | - |
| `modelClientCfg` | `ModelClientConfig` | `private` | `-` | - |
| `embeddingCfg` | `EmbeddingConfig` | `private` | `-` | - |

## `com.openjiuwen.core.memory.manage.index`

公开类型：`5`

### `BaseMemoryManager`

- 类型：`class`
- 声明：`public abstract class BaseMemoryManager`
- 说明：Abstract base class for memory manager implementations.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `MEMORY_LOGGER` | `LoggerProtocol` | `protected static final` | `Loggers.MEMORY` | - |
| `NONCE_HEX_LENGTH` | `int` | `protected static final` | `MemoryCrypto.NONCE_LENGTH * 2` | - |
| `TAG_HEX_LENGTH` | `int` | `protected static final` | `MemoryCrypto.TAG_LENGTH * 2` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | `void` | Add memories in batch. |
| `public abstract void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | `void` | Update memory by its id. |
| `public abstract boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | `boolean` | Delete memory by its id. |
| `public abstract boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | `boolean` | Delete memory by user id and scope id. |
| `public abstract Map<String, Object> get(String userId, String scopeId, String memId)` | `Map<String, Object>` | Get memory by its id. |
| `public abstract List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | `List<Map<String, Object>>` | Query memory, return top k results. |
| `public static String encryptMemoryIfNeeded(byte[] key, String plaintext)` | `String` | Encrypt plaintext if a valid crypto key is provided. |
| `public static String decryptMemoryIfNeeded(byte[] key, String ciphertext)` | `String` | Decrypt ciphertext if a valid crypto key is provided. |

### `FragmentMemoryManager`

- 类型：`class`
- 声明：`public class FragmentMemoryManager extends BaseMemoryManager`
- 说明：Manages fragment (user profile) memory CRUD with encryption and vector storage.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `UPDATE_CHECK_OLD_MEMORY_NUM` | `int` | `public static final` | `5` | - |
| `UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD` | `double` | `public static final` | `0.75` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public FragmentMemoryManager(UserMemStore memStore, DataIdManager dataIdGenerator, byte[] cryptoKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | `void` | - |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | `void` | - |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | `List<Map<String, Object>>` | - |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | `Map<String, Object>` | - |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | `boolean` | - |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | `boolean` | - |
| `public List<Map<String, Object>> listFragmentMemories(String userId, String scopeId, String profileType)` | `List<Map<String, Object>>` | - |

### `SummaryManager`

- 类型：`class`
- 声明：`public class SummaryManager extends BaseMemoryManager`
- 说明：Manages summary memory CRUD with encryption and vector storage.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SummaryManager(UserMemStore memStore, byte[] cryptoKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | `void` | - |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | `void` | - |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | `boolean` | - |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | `boolean` | - |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | `Map<String, Object>` | - |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | `List<Map<String, Object>>` | - |

### `VariableManager`

- 类型：`class`
- 声明：`public class VariableManager extends BaseMemoryManager`
- 说明：Manages variable memory using KV store.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public VariableManager(BaseKVStore kvStore, byte[] cryptoKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | `void` | - |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | `void` | - |
| `public void updateUserVariable(String userId, String scopeId, String varName, String varMem)` | `void` | - |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | `boolean` | - |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | `boolean` | - |
| `public void deleteUserVariable(String userId, String scopeId, String varName)` | `void` | - |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | `Map<String, Object>` | - |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | `List<Map<String, Object>>` | - |
| `public Map<String, String> queryVariable(String userId, String scopeId, String name, String sessionId)` | `Map<String, String>` | Query variable by user_id, scope_id, variable_name. |

### `WriteManager`

- 类型：`class`
- 声明：`public class WriteManager`
- 说明：Orchestrates memory write operations across all memory type managers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WriteManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addMemories(String userId, String scopeId, Map<String, ? extends List<? extends BaseMemoryUnit>> memories, Map.Entry<String, Model> llm, SemanticStore semanticStore)` | `void` | Add memories of different types in batch. |
| `public void updateMemById(String userId, String scopeId, String memId, String memory, SemanticStore semanticStore)` | `void` | Update a memory by ID (determines type from store). |
| `public void deleteMemById(String userId, String scopeId, String memId, SemanticStore semanticStore)` | `void` | Delete a memory by ID (determines type from store). |
| `public void deleteMemByUserId(String userId, String scopeId, SemanticStore semanticStore)` | `void` | Delete all memories for a user across all types. |

## `com.openjiuwen.core.memory.manage.mem_model`

公开类型：`15`

### `BaseMemoryUnit`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor public class BaseMemoryUnit`
- 说明：Base class for a single memory data item.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `memType` | `MemoryType` | `private` | `-` | - |
| `memId` | `String` | `private` | `-` | - |

### `DataIdManager`

- 类型：`class`
- 声明：`public class DataIdManager`
- 说明：Generates unique memory IDs using timestamp + random + user hash.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String generateNextId(String userId)` | `String` | Generate a unique hex ID based on current time, random bytes, and user ID hash. |

### `DbModel`

- 类型：`class`
- 声明：`public final class DbModel`
- 说明：Database model: table definitions and creation logic.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `USER_MESSAGE_TABLE` | `String` | `public static final` | `"user_message"` | - |
| `SCOPE_USER_MAPPING_TABLE` | `String` | `public static final` | `"scope_user_mapping"` | - |
| `MEMORY_META_TABLE` | `String` | `public static final` | `"memory_meta"` | - |
| `MEMORY_TABLES_CONFIG` | `String[][]` | `public static final` | `{{USER_MESSAGE_TABLE, "user_messages"}, {SCOPE_USER_MAPPING_TABLE, "scope_user_mapping"}}` | Table configs for migration tracking. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void createTables(BaseDbStore<?> dbStore)` | `void` | Create memory tables if they don't exist. |

### `FragmentMemoryUnit`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class FragmentMemoryUnit extends BaseMemoryUnit`
- 说明：Fragment memory unit.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `fragmentType` | `String` | `private` | `-` | - |
| `content` | `String` | `private` | `-` | - |
| `messageMemId` | `String` | `private` | `-` | - |
| `timestamp` | `String` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public MemoryType getMemType()` | `MemoryType` | - |

### `MemoryType`

- 类型：`enum`
- 声明：`public enum MemoryType`
- 说明：Types of memory data.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `FRAGMENT_MEMORY` | `new MemoryType("fragment")` | - |
| `VARIABLE` | `new MemoryType("variable")` | - |
| `SUMMARY` | `new MemoryType("summary")` | - |
| `UNKNOWN` | `new MemoryType("unknown")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static MemoryType fromValue(String value)` | `MemoryType` | - |

### `MessageAddRequest`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MessageAddRequest`
- 说明：Request object for adding a message.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `userId` | `String` | `private` | `-` | - |
| `scopeId` | `String` | `private` | `-` | - |
| `content` | `String` | `private` | `-` | - |
| `role` | `String` | `private` | `-` | - |
| `sessionId` | `String` | `private` | `-` | - |
| `timestamp` | `OffsetDateTime` | `private` | `OffsetDateTime.now(ZoneOffset.UTC)` | - |

### `MessageManager`

- 类型：`class`
- 声明：`public class MessageManager`
- 说明：DB-based message management.
- 嵌套公开类型：`MessageManager.MessageRecord`

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MessageManager(SqlDbStore sqlDb, DataIdManager dataId, byte[] cryptoKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String add(MessageAddRequest req)` | `String` | - |
| `public List<MessageRecord> get(String userId, String scopeId, String sessionId, int messageLen)` | `List<MessageRecord>` | - |
| `public MessageRecord getById(String msgId)` | `MessageRecord` | - |
| `public boolean deleteByUserAndScope(String userId, String scopeId)` | `boolean` | - |

### `MessageManager.MessageRecord`

- 类型：`record`
- 声明：`public record MessageRecord(BaseMessage message, OffsetDateTime timestamp)`
- 说明：Result of getting a message: the BaseMessage and its timestamp.
- 宿主类型：`MessageManager`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `message` | `BaseMessage` | `private final` | `-` | - |
| `timestamp` | `OffsetDateTime` | `private final` | `-` | - |

### `ScopeUserMappingManager`

- 类型：`class`
- 声明：`public class ScopeUserMappingManager`
- 说明：Manages scope-user mapping records in the SQL database.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ScopeUserMappingManager(SqlDbStore sqlDb)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void add(String userId, String scopeId)` | `void` | - |
| `public boolean deleteByScopeId(String scopeId)` | `boolean` | - |
| `public List<Map<String, Object>> getByScopeId(String scopeId)` | `List<Map<String, Object>>` | - |

### `SemanticStore`

- 类型：`class`
- 声明：`public class SemanticStore`
- 说明：Semantic store wrapping VectorStore for memory module.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SemanticStore(VectorStore vectorStore)` | - |
| `public SemanticStore(VectorStore vectorStore, Embedding embedding)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void initializeEmbeddingModel(Embedding embeddingModel)` | `void` | - |
| `public boolean collectionExist(String collectionName)` | `boolean` | Check if a collection exists. |
| `public void createCollection(String collectionName, int dimension, Map<String, Object> schema)` | `void` | Create a collection. |
| `public boolean addDocs(List<Map.Entry<String, String>> docs, String tableName)` | `boolean` | Add documents as (id, text) pairs. |
| `public List<Map.Entry<String, Double>> search(String query, String tableName, int topK)` | `List<Map.Entry<String, Double>>` | Search by text query. |
| `public void deleteDocs(List<String> ids, String tableName)` | `void` | Delete documents by IDs from a collection. |
| `public void deleteTable(String tableName)` | `void` | Delete an entire collection/table. |
| `public List<String> listCollectionNames()` | `List<String>` | List collection names. |
| `public boolean updateSchema(String collectionName, List<?> operations)` | `boolean` | Update schema. |
| `public Map<String, Object> getCollectionMetadata(String collectionName)` | `Map<String, Object>` | Get collection metadata. |
| `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | `void` | Update collection metadata. |

### `SqlDbStore`

- 类型：`class`
- 声明：`public class SqlDbStore`
- 说明：JDBC-based SQL CRUD wrapper for memory tables.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SqlDbStore(BaseDbStore<?> dbStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseDbStore<?> getDbStore()` | `BaseDbStore<?>` | - |
| `public Object getEngine()` | `Object` | - |
| `public boolean write(String table, Map<String, Object> data)` | `boolean` | Insert a row into the specified table. |
| `public Map<String, Object> get(String table, String recordId, List<String> columns)` | `Map<String, Object>` | Get a single record by id. |
| `public List<Map<String, Object>> getWithSort(String table, Map<String, Object> filters, String sortBy, String order, int limit)` | `List<Map<String, Object>>` | Get rows with filters, sorting, and limit. |
| `public boolean exist(String table, Map<String, Object> conditions)` | `boolean` | Check if a record exists matching the given conditions. |
| `public List<Map<String, Object>> conditionGet(String table, Map<String, List<Object>> conditions, List<String> columns)` | `List<Map<String, Object>>` | Get rows matching IN conditions on specified columns. |
| `public boolean update(String table, Map<String, Object> conditions, Map<String, Object> data)` | `boolean` | Update rows matching the given conditions. |
| `public boolean delete(String table, Map<String, Object> conditions)` | `boolean` | Delete rows matching the given conditions. |

### `SummaryUnit`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class SummaryUnit extends BaseMemoryUnit`
- 说明：Summary memory unit.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `summary` | `String` | `private` | `-` | - |
| `messageMemId` | `String` | `private` | `-` | - |
| `timestamp` | `String` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public MemoryType getMemType()` | `MemoryType` | - |

### `SupportMemoryType`

- 类型：`enum`
- 声明：`public enum SupportMemoryType`
- 说明：Supported memory types for vector operations.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `USER_PROFILE` | `new SupportMemoryType("user_profile")` | - |
| `SUMMARY` | `new SupportMemoryType("summary")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |

### `UserMemStore`

- 类型：`class`
- 声明：`public class UserMemStore`
- 说明：KV-based memory data storage with ID index management.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `BYTE_NUM_PER_ID` | `int` | `public static final` | `24` | - |
| `IDS_STR` | `String` | `public static final` | `"ids"` | - |
| `USER_PROFILE_TOPIC_STR` | `String` | `public static final` | `"UPT"` | - |
| `KEY_PREFIX_STR` | `String` | `public static final` | `"UMD"` | - |
| `MEM_TYPE_FIELD_KEY` | `String` | `public static final` | `"mem_type"` | - |
| `TOPIC_FIELD_KEY` | `String` | `public static final` | `"profile_type"` | - |
| `SEPARATOR` | `String` | `public static final` | `"/"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public UserMemStore(BaseKVStore kvStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean write(String userId, String scopeId, String memId, Map<String, Object> data)` | `boolean` | - |
| `public boolean update(String userId, String scopeId, String memId, Map<String, Object> data)` | `boolean` | - |
| `public void delete(String userId, String scopeId, String memId)` | `void` | - |
| `public void batchDelete(String userId, String scopeId, List<String> memIds)` | `void` | - |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | `Map<String, Object>` | - |
| `public List<Map<String, Object>> batchGet(String userId, String scopeId, List<String> memIds)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> getAll(String userId, String scopeId, String memType)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> getByTopic(String userId, String scopeId, String topic)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> getInRange(String userId, String scopeId, int startIdx, int endIdx, String memType)` | `List<Map<String, Object>>` | - |

### `VariableUnit`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class VariableUnit extends BaseMemoryUnit`
- 说明：Variable memory unit.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `variableName` | `String` | `private` | `-` | - |
| `variableMem` | `String` | `private` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public MemoryType getMemType()` | `MemoryType` | - |
| `public String getMemId()` | `String` | - |

## `com.openjiuwen.core.memory.manage.search`

公开类型：`2`

### `SearchManager`

- 类型：`class`
- 声明：`public class SearchManager`
- 说明：Orchestrates memory search across different memory type managers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SearchManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore, byte[] cryptoKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Map<String, Object>> search(SearchParams params, SemanticStore semanticStore)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> listUserMem(String userId, String scopeId, int nums, int pages, String memType)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId, String profileType)` | `List<Map<String, Object>>` | - |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId)` | `List<Map<String, Object>>` | - |
| `public String getUserVariable(String userId, String scopeId, String varName)` | `String` | - |
| `public Map<String, String> getAllUserVariable(String userId, String scopeId)` | `Map<String, String>` | - |

### `SearchParams`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class SearchParams`
- 说明：Parameters for memory search operations.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `userId` | `String` | `private` | `-` | - |
| `scopeId` | `String` | `private` | `-` | - |
| `query` | `String` | `private` | `-` | - |
| `topK` | `int` | `private` | `5` | - |
| `threshold` | `double` | `private` | `0.3` | - |
| `searchType` | `String` | `private` | `-` | - |

## `com.openjiuwen.core.memory.manage.update`

公开类型：`5`

### `CheckResult`

- 类型：`enum`
- 声明：`public enum CheckResult`
- 说明：Result of memory check operation.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `REDUNDANT` | `new CheckResult("redundant")` | - |
| `CONFLICTING` | `new CheckResult("conflicting")` | - |
| `NONE` | `new CheckResult("none")` | - |

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `value` | `String` | `private final` | `-` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static CheckResult fromValue(String value)` | `CheckResult` | - |

### `MemCheckItem`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemCheckItem`
- 说明：Represents a single memory check result item.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `infoId` | `String` | `private` | `-` | - |
| `infoText` | `String` | `private` | `-` | - |
| `result` | `CheckResult` | `private` | `-` | - |
| `relatedInfos` | `Map<String, String>` | `private` | `new LinkedHashMap<>()` | - |

### `MemUpdateChecker`

- 类型：`class`
- 声明：`public class MemUpdateChecker`
- 说明：Memory update checker for detecting redundancy and conflicts between memories.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MemUpdateChecker()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel)` | `List<MemoryActionItem>` | Check for redundancy and conflicts between new and old memories. |
| `public List<MemoryActionItem> check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel, int retries)` | `List<MemoryActionItem>` | - |

### `MemoryActionItem`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemoryActionItem`
- 说明：Represents a memory with its action status.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `content` | `String` | `private` | `-` | - |
| `status` | `MemoryStatus` | `private` | `-` | - |

### `MemoryStatus`

- 类型：`enum`
- 声明：`public enum MemoryStatus`
- 说明：Status of memory action.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `ADD` | `new MemoryStatus("add")` | - |
| `DELETE` | `new MemoryStatus("delete")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | - |
| `public static MemoryStatus fromValue(String value)` | `MemoryStatus` | - |

## `com.openjiuwen.core.memory.migration`

公开类型：`2`

### `MigrationPlan`

- 类型：`class`
- 声明：`public final class MigrationPlan`
- 说明：Global migration registries for SQL, vector, and KV operations.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static OperationRegistry getSqlRegistry()` | `OperationRegistry` | - |
| `public static OperationRegistry getVectorRegistry()` | `OperationRegistry` | - |
| `public static OperationRegistry getKvRegistry()` | `OperationRegistry` | - |

### `RunMigrations`

- 类型：`class`
- 声明：`public final class RunMigrations`
- 说明：Entry point for running all memory migrations (SQL, Vector, KV).

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static boolean runSqlMigrations(SqlDbStore sqlDbStore)` | `boolean` | - |
| `public static boolean runVectorMigrations(SemanticStore semanticStore)` | `boolean` | - |
| `public static boolean runKvMigrations(BaseKVStore kvStore)` | `boolean` | - |

## `com.openjiuwen.core.memory.migration.migrator`

公开类型：`4`

### `KvMigrator`

- 类型：`class`
- 声明：`public class KvMigrator`
- 说明：KV data migrator with backup and rollback support.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `KV_SCHEMA_VERSION` | `String` | `public static final` | `"MEMORY_MIGRATION_KV_SCHEMA_VERSION"` | - |
| `KV_ENTITY_KEY` | `String` | `public static final` | `"kv_global"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public KvMigrator(BaseKVStore kvStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | - |

### `MemoryMetaManager`

- 类型：`class`
- 声明：`public class MemoryMetaManager`
- 说明：Manages memory_meta table for tracking migration schema versions.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MemoryMetaManager(SqlDbStore sqlDb)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void add(String tableName, String schemaVersion)` | `void` | - |
| `public boolean deleteByTableName(String tableName)` | `boolean` | - |
| `public List<Map<String, Object>> getByTableName(String tableName)` | `List<Map<String, Object>>` | - |

### `SqlMigrator`

- 类型：`class`
- 声明：`public class SqlMigrator`
- 说明：SQL schema migrator using JDBC.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SqlMigrator(SqlDbStore sqlDb)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | - |

### `VectorMigrator`

- 类型：`class`
- 声明：`public class VectorMigrator`
- 说明：Vector store migrator.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public VectorMigrator(SemanticStore semanticStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | - |

## `com.openjiuwen.core.memory.migration.operation`

公开类型：`3`

### `BaseOperation`

- 类型：`class`
- 声明：`@Data public abstract class BaseOperation`
- 说明：Base class for all migration operations.
- 注解：`@Data`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `metadata` | `OperationMetadata` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseOperation(OperationMetadata metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getSchemaVersion()` | `int` | - |
| `public String getDescription()` | `String` | - |

### `OperationMetadata`

- 类型：`class`
- 声明：`@Data @AllArgsConstructor public class OperationMetadata`
- 说明：Simple operation metadata.
- 注解：`@Data`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `schemaVersion` | `int` | `private` | `-` | - |
| `description` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OperationMetadata(int schemaVersion)` | - |

### `OperationRegistry`

- 类型：`class`
- 声明：`public class OperationRegistry`
- 说明：Registry that manages chained upgrade operations by entity_key.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void register(String entityKey, BaseOperation op)` | `void` | - |
| `public List<BaseOperation> getOperations(String entityKey, int fromVersion, int toVersion)` | `List<BaseOperation>` | - |
| `public List<BaseOperation> getOperations(String entityKey)` | `List<BaseOperation>` | - |
| `public int getCurrentVersion(String entityKey)` | `int` | - |
| `public List<String> getAllEntities()` | `List<String>` | - |
| `public Map<String, List<BaseOperation>> getAllOperations()` | `Map<String, List<BaseOperation>>` | - |
| `public void clear()` | `void` | - |
| `public void setOperations(Map<String, List<BaseOperation>> ops)` | `void` | - |

## `com.openjiuwen.core.memory.process.extract`

公开类型：`6`

### `ExtractMemoryParams`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ExtractMemoryParams`
- 说明：Parameters for memory extraction.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `userId` | `String` | `private` | `-` | - |
| `scopeId` | `String` | `private` | `-` | - |
| `messages` | `List<BaseMessage>` | `private` | `-` | - |
| `historyMessages` | `List<BaseMessage>` | `private` | `-` | - |
| `baseChatModel` | `Map.Entry<String, Model>` | `private` | `-` | Tuple: (modelName, modelClient) |

### `Generator`

- 类型：`class`
- 声明：`public class Generator`
- 说明：Generates all memory units (variables, summary, fragment) from conversation messages.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Generator(DataIdManager dataIdGenerator)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, List<BaseMemoryUnit>> genAllMemory(Map<String, Object> kwargs)` | `Map<String, List<BaseMemoryUnit>>` | - |

### `LongTermMemoryExtractor`

- 类型：`class`
- 声明：`public class LongTermMemoryExtractor`
- 说明：Extracts long-term memory (fragment memories) from conversation using LLM.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, List<String>> extractLongTermMemory(ExtractMemoryParams params, String timestamp, int retries)` | `Map<String, List<String>>` | - |
| `public static Map<String, List<String>> extractLongTermMemory(ExtractMemoryParams params, String timestamp)` | `Map<String, List<String>>` | - |

### `MemoryAnalyzer`

- 类型：`class`
- 声明：`public class MemoryAnalyzer`
- 说明：Analyzes conversation messages to determine key information, extract variables, and generate summary.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static MemoryAnalyzerResult analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken, int retries)` | `MemoryAnalyzerResult` | - |
| `public static MemoryAnalyzerResult analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken)` | `MemoryAnalyzerResult` | - |

### `MemoryAnalyzerResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class MemoryAnalyzerResult`
- 说明：Result of memory analysis containing key information flag, variables, and summary.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `hasKeyInformation` | `boolean` | `private` | `false` | - |
| `variables` | `List<VariableResult>` | `private` | `new ArrayList<>()` | - |
| `summary` | `String` | `private` | `""` | - |

### `VariableResult`

- 类型：`class`
- 声明：`@Data @Builder @NoArgsConstructor @AllArgsConstructor public class VariableResult`
- 说明：Result of variable extraction from memory analysis.
- 注解：`@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `variableKey` | `String` | `private` | `""` | - |
| `variableValue` | `String` | `private` | `""` | - |

## `com.openjiuwen.core.memory.prompt`

公开类型：`1`

### `PromptApplier`

- 类型：`class`
- 声明：`public class PromptApplier`
- 说明：Singleton prompt applier that loads .md prompt templates from classpath resources and applies variable substitution.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static PromptApplier getInstance()` | `PromptApplier` | - |
| `public String apply(String filePrefix, Map<String, Object> variables)` | `String` | - |
| `public void clearCache(String filePrefix)` | `void` | - |
| `public void clearCache()` | `void` | - |
| `public PromptTemplate getTemplate(String filePrefix)` | `PromptTemplate` | - |

