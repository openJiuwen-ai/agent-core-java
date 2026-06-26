# com.openjiuwen.core.common.logging.events.GraphEvent

## 类 GraphEvent

```java
public class GraphEvent extends BaseLogEvent
```

`GraphEvent` 用于记录图执行、顶点调用、super step、图存储和图流式传输过程中的附加载荷。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `graphId` | `String` | `graph_id` | 图实例或图定义标识。 |
| `nodeId` | `String` | `node_id` | 节点标识。 |
| `nodeName` | `String` | `node_name` | 节点名称。 |
| `inputs` | `Object` | `inputs` | 输入数据或入参快照。 |
| `outputs` | `Object` | `outputs` | 输出数据或执行结果。 |
| `chunk` | `Object` | `chunk` | 当前分片或流式片段。 |

## 构造与序列化

- 默认构造函数只调用 `super()`，不会覆写 `moduleType`，因此默认值仍为 `ModuleType.SYSTEM`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 graph stream、graph vertex、graph super step、graph lifecycle 和 graph store 相关枚举值映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
