# com.openjiuwen.core.common.logging.events.StoreEvent

## 类 StoreEvent

```java
public class StoreEvent extends BaseLogEvent
```

`StoreEvent` 用于记录存储层增删改查和装载操作的目标位置与数据规模。

## 新增字段

| 字段 | 类型 | 序列化键 | 说明 |
| --- | --- | --- | --- |
| `tableName` | `String` | `table_name` | 数据表、集合或逻辑存储名称。 |
| `dataNum` | `Integer` | `data_num` | 涉及的数据条数。 |

## 构造与序列化

- 默认构造函数调用 `super()` 后会把 `moduleType` 设为 `ModuleType.STORE`。
- 通用元数据字段（如 `eventId`、`eventType`、`traceId`、`status`）沿用父类的实现。
- `EventClassRegistry` 会把 `STORE_ADD`、`STORE_DELETE`、`STORE_UPDATE`、`STORE_RETRIEVE`、`STORE_LOAD` 映射到该类型。
- 该类型使用 Lombok 的 `@Data`、`@SuperBuilder` 与 `@EqualsAndHashCode(callSuper = true)` 生成访问器、builder 和相等性逻辑。
