# com.openjiuwen.core.memory.manage.index.FragmentMemoryManager

## 类 FragmentMemoryManager

```java
public class FragmentMemoryManager extends BaseMemoryManager
```

`FragmentMemoryManager` 是 `com.openjiuwen.core.memory.manage.index` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `UPDATE_CHECK_OLD_MEMORY_NUM` | `int` | 字段 `UPDATE_CHECK_OLD_MEMORY_NUM`。 |
| `UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD` | `double` | 字段 `UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD`。 |
| `memStore` | `UserMemStore` | 字段 `memStore`。 |
| `dataIdGenerator` | `DataIdManager` | 字段 `dataIdGenerator`。 |
| `cryptoKey` | `byte[]` | 字段 `cryptoKey`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public FragmentMemoryManager(UserMemStore memStore, DataIdManager dataIdGenerator, byte[] cryptoKey)` | 创建 `FragmentMemoryManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | 执行 `addMemories` 写入流程。 |
| `public void update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | 执行 `update` 更新流程。 |
| `public List<Map<String, Object>> search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | 执行 `search` 查询流程。 |
| `public Map<String, Object> get(String userId, String scopeId, String memId)` | 返回 `get` 的执行结果。 |
| `public boolean delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | 执行 `delete` 删除流程。 |
| `public boolean deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | 执行 `deleteByUserId` 删除流程。 |
| `public List<Map<String, Object>> listFragmentMemories(String userId, String scopeId, String profileType)` | 执行 `listFragmentMemories` 查询流程。 |
