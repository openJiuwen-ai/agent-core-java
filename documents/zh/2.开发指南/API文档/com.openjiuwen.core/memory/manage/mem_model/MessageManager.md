# com.openjiuwen.core.memory.manage.mem_model.MessageManager

## 类 MessageManager

```java
public class MessageManager
```

`MessageManager` 是 `com.openjiuwen.core.memory.manage.mem_model` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sqlDb` | `SqlDbStore` | 字段 `sqlDb`。 |
| `dataId` | `DataIdManager` | 字段 `dataId`。 |
| `cryptoKey` | `byte[]` | 字段 `cryptoKey`。 |
| `MESSAGE_TABLE` | `String` | 字段 `MESSAGE_TABLE`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MessageManager(SqlDbStore sqlDb, DataIdManager dataId, byte[] cryptoKey)` | 创建 `MessageManager` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String add(MessageAddRequest req)` | 执行 `add` 写入流程。 |
| `public List<MessageRecord> get(String userId, String scopeId, String sessionId, int messageLen)` | 返回 `get` 的执行结果。 |
| `public MessageRecord getById(String msgId)` | 返回 `getById` 的执行结果。 |
| `public boolean deleteByUserAndScope(String userId, String scopeId)` | 执行 `deleteByUserAndScope` 删除流程。 |

## 嵌套公开类型

| 类型 | 签名 | 说明 |
| --- | --- | --- |
| `MessageRecord` | `public record MessageRecord(BaseMessage message, OffsetDateTime timestamp) {}` | `MessageRecord` 是本页声明的嵌套公开类型。 |
