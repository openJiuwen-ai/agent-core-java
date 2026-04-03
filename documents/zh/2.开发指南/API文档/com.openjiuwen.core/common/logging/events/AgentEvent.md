# com.openjiuwen.core.common.logging.events.AgentEvent

## 类 AgentEvent

```java
public class AgentEvent extends BaseLogEvent
```

`AgentEvent` 在 `BaseLogEvent` 的通用元数据之上，补充 Agent 执行、迭代和输入输出相关字段。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `agentType` | `String` | `agent_type` | Agent 的实现或策略类型。 |
| `agentConfig` | `Map<String, Object>` | `agent_config` | Agent 运行或初始化配置快照。 |
| `inputData` | `Map<String, Object>` | `input_data` | 输入数据或调用参数。 |
| `outputData` | `Map<String, Object>` | `output_data` | 输出结果或中间产物。 |
| `iterationCount` | `Integer` | `iteration_count` | 当前迭代轮次。 |
| `maxIterations` | `Integer` | `max_iterations` | 允许的最大迭代轮次。 |
| `executionTimeMs` | `Double` | `execution_time_ms` | 执行耗时，单位毫秒。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.AGENT`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `AGENT_START`、`AGENT_END`、`AGENT_INVOKE`、`AGENT_RESPONSE`、`AGENT_ERROR` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
- 默认 `EventSanitizer` 会对 `input_data`、`output_data` 做脱敏。
- `StructuredLogEventTest` 覆盖了该类型通过注册表创建、`agentType` 赋值以及 `moduleType=AGENT` 的行为。
