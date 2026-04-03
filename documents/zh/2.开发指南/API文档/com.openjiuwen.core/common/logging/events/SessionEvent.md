# com.openjiuwen.core.common.logging.events.SessionEvent

## 类 SessionEvent

```java
public class SessionEvent extends BaseLogEvent
```

`SessionEvent` 用于记录 Session 生命周期、checkpoint 以及 checkpointer store 相关附加字段。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `sessionType` | `String` | `session_type` | 会话类型。 |
| `userId` | `String` | `user_id` | 用户标识。 |
| `agentId` | `String` | `agent_id` | 关联的 Agent 标识。 |
| `workflowId` | `String` | `workflow_id` | 关联的 Workflow 标识。 |
| `sessionConfig` | `Map<String, Object>` | `session_config` | 会话配置快照。 |
| `messageCount` | `Integer` | `message_count` | 消息数量。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.SESSION`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `SESSION_*`、`CHECKPOINT_*`、`CHECKPOINTER_STORE_*` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
