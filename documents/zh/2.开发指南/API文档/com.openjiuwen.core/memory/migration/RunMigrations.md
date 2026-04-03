# com.openjiuwen.core.memory.migration.RunMigrations

## 类 RunMigrations

```java
public final class RunMigrations
```

`RunMigrations` 是记忆迁移的统一执行入口，负责从 `MigrationPlan` 读取注册表并调度对应迁移器。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 迁移执行期间使用的记忆日志记录器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static boolean runSqlMigrations(SqlDbStore sqlDbStore)` | 执行 SQL 注册表中的全部迁移；若任一实体失败则返回 `false`。 |
| `public static boolean runVectorMigrations(SemanticStore semanticStore)` | 执行向量存储迁移，并在失败时记录错误日志后返回 `false`。 |
| `public static boolean runKvMigrations(BaseKVStore kvStore)` | 执行 KV 迁移；如果没有已注册实体则直接返回 `true`。 |

## 使用说明

- 三个方法都会先检查对应注册表是否为空；为空时视为无需迁移。
- 每次执行会按实体键顺序逐个调用 `SqlMigrator`、`VectorMigrator` 或 `KvMigrator`。
- 该类没有公开构造方法，适合作为启动阶段的静态工具入口。
