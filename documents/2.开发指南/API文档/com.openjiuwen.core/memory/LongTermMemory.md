# com.openjiuwen.core.memory.LongTermMemory

## class LongTermMemory

```java
public class LongTermMemory
```

Main memory engine implementing long-term memory management.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `MAPPER` | `ObjectMapper` | mapper. |
| `TIMESTAMP_FMT` | `DateTimeFormatter` | timestamp fmt. |
| `DEFAULT_VALUE` | `String` | default value. |
| `SCOPE_CONFIG_KEY` | `String` | scope config key. |
| `instance` | `LongTermMemory` | instance. |
| `sysMemConfig` | `MemoryEngineConfig` | sys mem config. |
| `scopeConfig` | `ConcurrentHashMap<String, MemoryScopeConfig>` | scope config. |
| `kvStore` | `BaseKVStore` | kv store. |
| `vectorStore` | `VectorStore` | vector store. |
| `dbStore` | `BaseDbStore<?>` | db store. |
| `scopeUserMappingManager` | `ScopeUserMappingManager` | scope user mapping manager. |
| `messageManager` | `MessageManager` | message manager. |
| `userProfileManager` | `FragmentMemoryManager` | user profile manager. |
| `variableManager` | `VariableManager` | variable manager. |
| `writeManager` | `WriteManager` | write manager. |
| `summaryManager` | `SummaryManager` | summary manager. |
| `searchManager` | `SearchManager` | search manager. |
| `generator` | `Generator` | generator. |
| `baseLlm` | `Map.Entry<String, Model>` | base llm. |
| `baseEmbed` | `Embedding` | base embed. |
| `scopeEmbedding` | `ConcurrentHashMap<String, Embedding>` | scope embedding. |

## Constructors

| Signature | Description |
| --- | --- |
| `private LongTermMemory()` | Create a new `LongTermMemory` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static LongTermMemory getInstance()` | Execute `getInstance`. |
| `public static void resetInstance()` | Reset singleton for testing. |
| `public void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embeddingModel)` | Execute `registerStore`. |
| `public void setConfig(MemoryEngineConfig config)` | Execute `setConfig`. |
| `public boolean setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig)` | Execute `setScopeConfig`. |
| `public MemoryScopeConfig getScopeConfig(String scopeId)` | Execute `getScopeConfig`. |
| `public boolean deleteScopeConfig(String scopeId)` | Execute `deleteScopeConfig`. |
| `public boolean deleteMemByScope(String scopeId)` | Execute `deleteMemByScope`. |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId, OffsetDateTime timestamp, boolean genMem, int genMemWithHistoryMsgNum)` | Execute `addMessages`. |
| `public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId)` | Execute `addMessages`. |
| `public List<BaseMessage> getRecentMessages(String userId, String scopeId, String sessionId, int num)` | Execute `getRecentMessages`. |
| `public MessageManager.MessageRecord getMessageById(String msgId)` | Execute `getMessageById`. |
| `public void deleteMessagesByUserAndScope(String userId, String scopeId)` | Execute `deleteMessagesByUserAndScope`. |
| `public void deleteMemById(String memId, String userId, String scopeId)` | Execute `deleteMemById`. |
| `public void deleteMemByUserId(String userId, String scopeId)` | Execute `deleteMemByUserId`. |
| `public void updateMemById(String memId, String memory, String userId, String scopeId)` | Execute `updateMemById`. |
| `public Map<String, String> getVariables(Object names, String userId, String scopeId)` | Execute `getVariables`. |
| `public void updateVariables(Map<String, String> variables, String userId, String scopeId)` | Execute `updateVariables`. |
| `public boolean deleteVariables(List<String> names, String userId, String scopeId)` | Execute `deleteVariables`. |
| `public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold)` | Execute `searchUserMem`. |
| `public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold)` | Execute `searchUserHistorySummary`. |
| `public int userMemTotalNum(String userId, String scopeId)` | Execute `userMemTotalNum`. |
| `public List<MemInfo> getUserMemByPage(String userId, String scopeId, int pageSize, int pageIdx, MemoryType memoryType)` | Execute `getUserMemByPage`. |

## Notes

- Related tests: `LongTermMemoryTest.java`
