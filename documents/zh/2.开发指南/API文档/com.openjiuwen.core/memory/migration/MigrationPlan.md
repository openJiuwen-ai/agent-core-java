# com.openjiuwen.core.memory.migration.MigrationPlan

## 类 MigrationPlan

```java
public final class MigrationPlan
```

`MigrationPlan` 维护记忆迁移使用的三套全局注册表，分别对应 SQL、向量库与 KV 存储。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `SQL_REGISTRY` | `OperationRegistry` | SQL 存储的全局迁移注册表。 |
| `VECTOR_REGISTRY` | `OperationRegistry` | 向量存储的全局迁移注册表。 |
| `KV_REGISTRY` | `OperationRegistry` | KV 存储的全局迁移注册表。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static OperationRegistry getSqlRegistry()` | 返回 SQL 迁移注册表。 |
| `public static OperationRegistry getVectorRegistry()` | 返回向量存储迁移注册表。 |
| `public static OperationRegistry getKvRegistry()` | 返回 KV 迁移注册表。 |

## 使用说明

- 该类只有静态访问入口，不提供公开构造方法。
- `MigrationPlanTest` 验证了三个注册表可被清空、恢复，并能按版本区间查询操作。
