# com.openjiuwen.core.memory.LongTermMemory

## 类 LongTermMemory

```java
public class LongTermMemory
```

该类是长期记忆引擎的对外入口，负责管理配置、消息写入、变量维护与记忆检索。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `MAPPER` | `ObjectMapper` | JSON 映射器。 |
| `TIMESTAMP_FMT` | `DateTimeFormatter` | 时间戳格式化器。 |
| `DEFAULT_VALUE` | `String` | 默认占位值。 |
| `SCOPE_CONFIG_KEY` | `String` | 作用域配置在 KV 中使用的键前缀。 |
| `instance` | `LongTermMemory` | 单例实例。 |
| `sysMemConfig` | `MemoryEngineConfig` | 系统级记忆配置。 |
| `scopeConfig` | `ConcurrentHashMap<String, MemoryScopeConfig>` | 按作用域缓存的配置。 |
| `kvStore` | `BaseKVStore` | KV 存储。 |
| `vectorStore` | `VectorStore` | 向量存储。 |
| `dbStore` | `BaseDbStore<?>` | 底层 SQL 存储适配器。 |
| `scopeUserMappingManager` | `ScopeUserMappingManager` | 作用域与用户映射管理器。 |
| `messageManager` | `MessageManager` | 消息管理器。 |
| `userProfileManager` | `FragmentMemoryManager` | 分片记忆管理器。 |
| `variableManager` | `VariableManager` | 变量记忆管理器。 |
| `writeManager` | `WriteManager` | 统一写入协调器。 |
| `summaryManager` | `SummaryManager` | 摘要记忆管理器。 |
| `searchManager` | `SearchManager` | 记忆检索管理器。 |
| `generator` | `Generator` | 记忆生成器。 |
| `baseLlm` | `Map.Entry<String, Model>` | 默认模型条目。 |
| `baseEmbed` | `Embedding` | 默认嵌入模型。 |
| `scopeEmbedding` | `ConcurrentHashMap<String, Embedding>` | 按作用域缓存的嵌入模型。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static LongTermMemory getInstance()` | 返回单例实例。 |
| `public static void resetInstance()` | 重置单例实例，主要用于测试。 |
| `public void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embeddingModel)` | 执行 `registerStore`。 |
| `public void setConfig(MemoryEngineConfig config)` | 执行 `setConfig` 配置更新。 |
| `public boolean setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig)` | 执行 `setScopeConfig` 配置更新。 |
| `public MemoryScopeConfig getScopeConfig(String scopeId)` | 返回 `getScopeConfig` 的执行结果。 |
| `public boolean deleteScopeConfig(String scopeId)` | 执行 `deleteScopeConfig` 删除流程。 |
| `public boolean deleteMemByScope(String scopeId)` | 执行 `deleteMemByScope` 删除流程。 |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId, OffsetDateTime timestamp, boolean genMem, int genMemWithHistoryMsgNum)` | 执行 `addMessages` 写入流程。 |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId)` | 执行 `addMessages` 写入流程。 |
| `public List<BaseMessage> getRecentMessages(String userId, String scopeId, String sessionId, int num)` | 返回 `getRecentMessages` 的执行结果。 |
| `public MessageManager.MessageRecord getMessageById(String msgId)` | 返回 `getMessageById` 的执行结果。 |
| `public void deleteMessagesByUserAndScope(String userId, String scopeId)` | 执行 `deleteMessagesByUserAndScope` 删除流程。 |
| `public void deleteMemById(String memId, String userId, String scopeId)` | 执行 `deleteMemById` 删除流程。 |
| `public void deleteMemByUserId(String userId, String scopeId)` | 执行 `deleteMemByUserId` 删除流程。 |
| `public void updateMemById(String memId, String memory, String userId, String scopeId)` | 执行 `updateMemById` 更新流程。 |
| `public Map<String, String> getVariables(Object names, String userId, String scopeId)` | 返回 `getVariables` 的执行结果。 |
| `public void updateVariables(Map<String, String> variables, String userId, String scopeId)` | 执行 `updateVariables` 更新流程。 |
| `public boolean deleteVariables(List<String> names, String userId, String scopeId)` | 执行 `deleteVariables` 删除流程。 |
| `public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold)` | 执行 `searchUserMem` 查询流程。 |
| `public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold)` | 执行 `searchUserHistorySummary` 查询流程。 |
| `public int userMemTotalNum(String userId, String scopeId)` | 执行 `userMemTotalNum`。 |
| `public List<MemInfo> getUserMemByPage(String userId, String scopeId, int pageSize, int pageIdx, MemoryType memoryType)` | 返回 `getUserMemByPage` 的执行结果。 |

## 使用说明

- 相关测试：`LongTermMemoryTest.java`
