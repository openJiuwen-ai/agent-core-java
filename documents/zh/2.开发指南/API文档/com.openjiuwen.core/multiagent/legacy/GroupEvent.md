# com.openjiuwen.core.multiagent.legacy.GroupEvent

## class GroupEvent

```java
@Deprecated
public class GroupEvent
```

`GroupEvent` 是 legacy 多 Agent 路由事件模型，承载查询内容、会话标识、接收者和自定义事件类型等信息。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `eventId` | `String` | 随机构造的 UUID | 事件唯一标识。 |
| `query` | `String` | `null` | 文本化查询内容。 |
| `queryPayload` | `Object` | `null` | 保留原始查询载荷，可为字符串或交互输入对象。 |
| `conversationId` | `String` | `null` | 会话 / 对话标识。 |
| `userId` | `String` | `null` | 调用用户标识。 |
| `receiverId` | `String` | `null` | 点对点路由目标 Agent ID。 |
| `customEventType` | `String` | `null` | 广播路由时使用的消息类型。 |
| `metadata` | `Map<String, Object>` | `new HashMap<>()` | 事件扩展元数据。 |

## 工厂方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `createUserEvent(String content, String conversationId)` | `GroupEvent` | 用文本内容与会话 ID 创建用户事件。 |
| `createUserEvent(String content, String conversationId, String userId)` | `GroupEvent` | 在上一个工厂基础上补充用户 ID。 |
| `fromMap(Map<String, Object> map)` | `GroupEvent` | 从兼容的 `Map` 输入中抽取 `content/query`、`conversation_id`、`user_id`、`receiver_id`、`custom_event_type` 等字段。 |

## 访问器

| 方法 | 返回 | 说明 |
|---|---|---|
| `getEventId()` / `setEventId(String eventId)` | `String` / `void` | 读写事件 ID。 |
| `getQuery()` / `setQuery(String query)` | `String` / `void` | 读写文本查询，同时同步更新 `queryPayload`。 |
| `getQueryPayload()` / `setQueryPayload(Object queryPayload)` | `Object` / `void` | 读写原始查询载荷，同时把 `query` 同步为 `toString()` 结果。 |
| `getConversationId()` / `setConversationId(String conversationId)` | `String` / `void` | 读写会话 ID。 |
| `getUserId()` / `setUserId(String userId)` | `String` / `void` | 读写用户 ID。 |
| `getReceiverId()` / `setReceiverId(String receiverId)` | `String` / `void` | 读写点对点接收者。 |
| `getCustomEventType()` / `setCustomEventType(String customEventType)` | `String` / `void` | 读写广播事件类型。 |
| `getMetadata()` / `setMetadata(Map<String, Object> metadata)` | `Map<String, Object>` / `void` | 读写扩展元数据。 |

## 说明

- 默认构造会自动生成 `eventId`，并初始化空的 `metadata`。
- `fromMap(...)` 会把传入 `map` 的全部键值拷贝进 `metadata`，用于保留 legacy 输入上下文。
