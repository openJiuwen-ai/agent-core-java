# com.openjiuwen.core.common.utils.MessageUtils

## class MessageUtils

```java
public final class MessageUtils
```

`MessageUtils` 用于向会话上下文写入消息，并按轮次读取聊天历史。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static boolean shouldAddUserMessage(String query, ContextEngine contextEngine, Session session)` | 判断是否应追加用户消息；当默认上下文不存在、无历史消息，或最后一条消息不是同内容用户消息时返回 `true`。 |
| `public static void addUserMessage(Object query, ContextEngine contextEngine, Session session)` | 将用户输入转换为字符串后写入默认上下文；若 `UserConfig.isSensitive()` 为真，只记录“已添加用户消息”的摘要日志。 |
| `public static void addAiMessage(AssistantMessage aiMessage, ContextEngine contextEngine, Session session)` | 将助手消息追加到默认上下文。 |
| `public static void addToolMessage(ToolMessage toolMessage, ContextEngine contextEngine, Session session)` | 将工具消息追加到默认上下文。 |
| `public static void addWorkflowMessage(BaseMessage message, String workflowId, ContextEngine contextEngine, Session session)` | 将消息追加到指定 `workflowId` 的上下文。 |
| `public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int maxRounds)` | 读取默认上下文消息，并最多返回最近 `2 * maxRounds` 条消息。 |

## 说明

- 默认上下文 ID 在源码中固定为 `default_context_id`。
- 去重逻辑只比较最后一条消息，且要求其 `role` 为 `user` 且内容与本次输入完全一致。
