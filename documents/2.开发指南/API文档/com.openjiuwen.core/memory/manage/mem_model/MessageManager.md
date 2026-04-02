# com.openjiuwen.core.memory.manage.mem_model.MessageManager

## class MessageManager

```java
public class MessageManager
```

DB-based message management.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `sqlDb` | `SqlDbStore` | sql db. |
| `dataId` | `DataIdManager` | data id. |
| `cryptoKey` | `byte[]` | crypto key. |
| `MESSAGE_TABLE` | `String` | message table. |

## Constructors

| Signature | Description |
| --- | --- |
| `public MessageManager(SqlDbStore sqlDb, DataIdManager dataId, byte[] cryptoKey)` | Create a new `MessageManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String add(MessageAddRequest req)` | Execute `add`. |
| `public List<MessageRecord> get(String userId, String scopeId, String sessionId, int messageLen)` | Execute `get`. |
| `public MessageRecord getById(String msgId)` | Execute `getById`. |
| `public boolean deleteByUserAndScope(String userId, String scopeId)` | Execute `deleteByUserAndScope`. |

## Nested Public Types

| Type | Signature | Description |
| --- | --- | --- |
| `MessageRecord` | `public record MessageRecord(BaseMessage message, OffsetDateTime timestamp) {}` | Result of getting a message: the BaseMessage and its timestamp. |
