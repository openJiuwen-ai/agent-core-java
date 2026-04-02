# com.openjiuwen.core.memory.process.extract.ExtractMemoryParams

## class ExtractMemoryParams

```java
public class ExtractMemoryParams
```

Parameters for memory extraction.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `userId` | `String` | user id. |
| `scopeId` | `String` | scope id. |
| `messages` | `List<BaseMessage>` | messages. |
| `historyMessages` | `List<BaseMessage>` | history messages. |
| `baseChatModel` | `Map.Entry<String, Model>` | Tuple: (modelName, modelClient) |

## Notes

- Lombok annotations on this type generate boilerplate accessors/builders that are not listed individually.
