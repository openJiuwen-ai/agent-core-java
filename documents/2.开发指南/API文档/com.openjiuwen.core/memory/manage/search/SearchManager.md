# com.openjiuwen.core.memory.manage.search.SearchManager

## 类 SearchManager

```java
public class SearchManager
```

`SearchManager` 是 `com.openjiuwen.core.memory.manage.search` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `USER_MEM_MANAGER_LIST` | `Set<String>` | 字段 `USER_MEM_MANAGER_LIST`。 |
| `ALL_MEM_MANAGER_LIST` | `Set<String>` | 字段 `ALL_MEM_MANAGER_LIST`。 |
| `managers` | `Map<String, BaseMemoryManager>` | 字段 `managers`。 |
| `memStore` | `UserMemStore` | 字段 `memStore`。 |
| `cryptoKey` | `byte[]` | 字段 `cryptoKey`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SearchManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore, byte[] cryptoKey)` | 创建 `SearchManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public List<Map<String, Object>> search(SearchParams params, SemanticStore semanticStore)` | 执行 `search` 查询流程。 |
| `public List<Map<String, Object>> listUserMem(String userId, String scopeId, int nums, int pages, String memType)` | 执行 `listUserMem` 查询流程。 |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId, String profileType)` | 执行 `listUserProfile` 查询流程。 |
| `public List<Map<String, Object>> listUserProfile(String userId, String scopeId)` | 执行 `listUserProfile` 查询流程。 |
| `public String getUserVariable(String userId, String scopeId, String varName)` | 返回 `getUserVariable` 的执行结果。 |
| `public Map<String, String> getAllUserVariable(String userId, String scopeId)` | 返回 `getAllUserVariable` 的执行结果。 |

## 使用说明

- 相关测试：`SearchManagerTest.java`
