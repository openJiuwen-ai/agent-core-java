# com.openjiuwen.core.memory.manage.mem_model.DbModel

## 类 DbModel

```java
public final class DbModel
```

该类定义记忆 SQL 表的表名、建表逻辑与迁移元数据初始化。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `USER_MESSAGE_TABLE` | `String` | 字段 `USER_MESSAGE_TABLE`。 |
| `SCOPE_USER_MAPPING_TABLE` | `String` | 字段 `SCOPE_USER_MAPPING_TABLE`。 |
| `MEMORY_META_TABLE` | `String` | 字段 `MEMORY_META_TABLE`。 |
| `MEMORY_TABLES_CONFIG` | `String[][]` | 字段 `MEMORY_TABLES_CONFIG`。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void createTables(BaseDbStore<?> dbStore)` | 执行 `createTables`。 |

## 使用说明

- 相关测试：`DbModelTest.java`
