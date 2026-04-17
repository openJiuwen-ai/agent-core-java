# com.openjiuwen.core.common.logging.events.RunnerEvent

## 类 RunnerEvent

```java
public class RunnerEvent extends BaseLogEvent
```

`RunnerEvent` 承载 Runner 生命周期和资源管理相关事件的输入输出、资源和卡片信息。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `runnerId` | `String` | `runner_id` | Runner 标识。 |
| `inputs` | `Object` | `inputs` | 输入数据或入参快照。 |
| `outputs` | `Object` | `outputs` | 输出数据或执行结果。 |
| `chunk` | `Object` | `chunk` | 当前分片或流式片段。 |
| `envs` | `Object` | `envs` | 执行环境变量或上下文对象。 |
| `resourceId` | `String` | `resource_id` | 资源标识。 |
| `resourceType` | `String` | `resource_type` | 资源类型。 |
| `tag` | `Object` | `tag` | 资源标签或分组标记。 |
| `card` | `BaseCard` | `card` | 与资源事件关联的 `BaseCard` 对象。 |

## 构造与序列化

- 默认构造函数只调用 `super()`，不会覆写 `moduleType`，因此默认值仍为 `ModuleType.SYSTEM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `RUNNER_START`、`RUNNER_STOP` 和 `RESOURCE_MGR_*` 系列枚举值映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
