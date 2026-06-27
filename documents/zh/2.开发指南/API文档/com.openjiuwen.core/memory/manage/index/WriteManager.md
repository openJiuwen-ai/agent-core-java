# com.openjiuwen.core.memory.manage.index.WriteManager

## 类 WriteManager

```java
public class WriteManager
```

`WriteManager` 是 `com.openjiuwen.core.memory.manage.index` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `managers` | `Map<String, BaseMemoryManager>` | 字段 `managers`。 |
| `memStore` | `UserMemStore` | 字段 `memStore`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WriteManager(Map<String, BaseMemoryManager> managers, UserMemStore memStore)` | 创建 `WriteManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void addMemories(String userId, String scopeId, Map<String, ? extends List<? extends BaseMemoryUnit>> memories, Map.Entry<String, Model> llm, SemanticStore semanticStore)` | 执行 `addMemories` 写入流程。 |
| `public void updateMemById(String userId, String scopeId, String memId, String memory, SemanticStore semanticStore)` | 执行 `updateMemById` 更新流程。 |
| `public void deleteMemById(String userId, String scopeId, String memId, SemanticStore semanticStore)` | 执行 `deleteMemById` 删除流程。 |
| `public void deleteMemByUserId(String userId, String scopeId, SemanticStore semanticStore)` | 执行 `deleteMemByUserId` 删除流程。 |
