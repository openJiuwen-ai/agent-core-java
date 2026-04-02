# com.openjiuwen.core.memory.manage.mem_model.UserMessage

## record UserMessage

```java
public record UserMessage( String messageId, String userId, String scopeId, String content, String sessionId, String role, String timestamp)
```

Public row model matching the memory user_message table.

## Record Components

| Name | Type | Description |
| --- | --- | --- |
| `messageId` | `String` | message id. |
| `userId` | `String` | user id. |
| `scopeId` | `String` | scope id. |
| `content` | `String` | content. |
| `sessionId` | `String` | session id. |
| `role` | `String` | role. |
| `timestamp` | `String` | timestamp. |
