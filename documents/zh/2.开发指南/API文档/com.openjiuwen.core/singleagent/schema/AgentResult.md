# com.openjiuwen.core.singleagent.schema.AgentResult

## 类 AgentResult

```java
public class AgentResult
```

表示单智能体任务执行结果的数据模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `taskId` | `String` | `-` | 任务 ID。 |
| `sessionId` | `String` | `-` | 会话 ID。 |
| `status` | `TaskStatus` | `-` | 当前任务状态。 |
| `artifacts` | `List<Artifact>` | `new ArrayList<>()` | 结果关联的制品列表。 |
| `metadata` | `Map<String, Object>` | `new HashMap<>()` | 附加元数据。 |

## 说明

- 相关测试：`DataClassCoverageTest`、`SchemaTest`。
- builder 默认会为 `artifacts` 与 `metadata` 创建空集合。
