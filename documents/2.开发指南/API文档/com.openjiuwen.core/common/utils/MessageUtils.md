# com.openjiuwen.core.common.utils.MessageUtils

## class MessageUtils

```java
public final class MessageUtils
```

`MessageUtils` adds and reads chat-history messages from `ContextEngine` / `ModelContext` instances while handling duplicate user input and sensitive logging rules.

## Constructors

| Signature | Description |
| --- | --- |
| `private MessageUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static boolean shouldAddUserMessage(String query, ContextEngine contextEngine, Session session)` | Return `false` only when the latest message in the default context is already a user message whose content equals `query`. |
| `public static void addUserMessage(Object query, ContextEngine contextEngine, Session session)` | Convert `query` to text, append a `UserMessage` to the `default_context_id` context when deduplication allows it, and log either masked or raw content based on `UserConfig.isSensitive()`. |
| `public static void addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine, Session session)` | Append a non-null assistant message to the `default_context_id` context when that context exists. |
| `public static void addToolMessage(ToolMessage toolMessage, ContextEngine contextEngine, Session session)` | Append a non-null tool message to the `default_context_id` context when that context exists. |
| `public static void addWorkflowMessage(BaseMessage message, String workflowId, ContextEngine contextEngine, Session session)` | Append a message to the workflow-specific context identified by `workflowId` when that context exists. |
| `public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int maxRounds)` | Return the full default-context history when it fits within `2 * maxRounds` messages, otherwise return only the trailing slice for that many dialogue rounds. |

## Notes

- Missing contexts are treated as no-op or empty-history cases rather than failures.
- The helper uses the literal context id `default_context_id` for the main agent conversation path.
