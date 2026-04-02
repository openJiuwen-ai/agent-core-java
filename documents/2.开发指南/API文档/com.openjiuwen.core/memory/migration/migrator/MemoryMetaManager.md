# com.openjiuwen.core.memory.migration.migrator.MemoryMetaManager

## 类 MemoryMetaManager

```java
public class MemoryMetaManager
```

`MemoryMetaManager` 用于维护 SQL 存储中的 `memory_meta` 表，记录各表的迁移版本。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `META_TABLE` | `String` | 元数据表名，固定为 `memory_meta`。 |
| `sqlDb` | `SqlDbStore` | 提供存在性判断、写入、删除与条件查询能力的 SQL 存储封装。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MemoryMetaManager(SqlDbStore sqlDb)` | 使用指定的 `SqlDbStore` 创建管理器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void add(String tableName, String schemaVersion)` | 在参数非空且当前记录不存在时，为指定表插入版本记录。 |
| `public boolean deleteByTableName(String tableName)` | 删除指定表在 `memory_meta` 中的所有记录。 |
| `public List<Map<String, Object>> getByTableName(String tableName)` | 查询指定表的版本记录；未命中时返回 `null`。 |
