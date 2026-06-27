# com.openjiuwen.core.memory.migration.migrator.SqlMigrator

## 类 SqlMigrator

```java
public class SqlMigrator
```

`SqlMigrator` 通过 JDBC 对 SQL 表执行迁移操作，并把最终版本写回 `memory_meta`。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | SQL 迁移期间使用的日志记录器。 |
| `sqlDb` | `SqlDbStore` | 提供底层引擎对象与表操作能力的 SQL 存储封装。 |
| `memoryMetaManager` | `MemoryMetaManager` | 负责读取和更新 `memory_meta` 表中的版本信息。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SqlMigrator(SqlDbStore sqlDb)` | 使用指定 `SqlDbStore` 创建迁移器，并同步创建 `MemoryMetaManager`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean tryMigrate(String entityKey, List<BaseOperation> operations)` | 根据 `memory_meta` 过滤待执行操作，在事务中执行迁移并更新版本；失败时回滚并返回 `false`。 |

## 行为说明

- 当前支持 `AddColumnOperation`、`RenameColumnOperation`、`UpdateColumnTypeOperation`。
- 当底层引擎是 `DataSource` 时会自行获取并关闭连接；如果是现成 `Connection`，则直接复用。
- 针对 SQLite，更新列类型时会创建临时表、复制数据并重命名回原表，以规避 SQLite 对 `ALTER COLUMN TYPE` 的限制。
