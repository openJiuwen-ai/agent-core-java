# com.openjiuwen.core.memory.manage.mem_model.SqlDbStore

## 类 SqlDbStore

```java
public class SqlDbStore
```

该类封装记忆模块使用的 SQL CRUD 操作与表结构反射。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `dbStore` | `BaseDbStore<?>` | 底层 SQL 存储适配器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SqlDbStore(BaseDbStore<?> dbStore)` | 创建 `SqlDbStore` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public BaseDbStore<?> getDbStore()` | 返回 `getDbStore` 的执行结果。 |
| `public Object getEngine()` | 返回 `getEngine` 的执行结果。 |
| `public boolean write(String table, Map<String, Object> data)` | 执行 `write` 写入流程。 |
| `public Map<String, Object> get(String table, String recordId, List<String> columns)` | 返回 `get` 的执行结果。 |
| `public List<Map<String, Object>> getWithSort(String table, Map<String, Object> filters, String sortBy, String order, int limit)` | 返回 `getWithSort` 的执行结果。 |
| `public boolean exist(String table, Map<String, Object> conditions)` | 执行 `exist`。 |
| `public List<Map<String, Object>> batchGet(String table, List<Map<String, Object>> conditionsList)` | 执行 `batchGet`。 |
| `public List<Map<String, Object>> conditionGet(String table, Map<String, List<Object>> conditions, List<String> columns)` | 执行 `conditionGet`。 |
| `public boolean update(String table, Map<String, Object> conditions, Map<String, Object> data)` | 执行 `update` 更新流程。 |
| `public boolean delete(String table, Map<String, Object> conditions)` | 执行 `delete` 删除流程。 |
| `public boolean deleteTable(String tableName)` | 执行 `deleteTable` 删除流程。 |
| `public TableInfo getTable(String tableName)` | 返回 `getTable` 的执行结果。 |

## 嵌套公开类型

| 类型 | 签名 | 说明 |
| --- | --- | --- |
| `TableInfo` | `public static final class TableInfo` | `TableInfo` 是本页声明的嵌套公开类型。 |
| `ColumnInfo` | `public static final class ColumnInfo` | `ColumnInfo` 是本页声明的嵌套公开类型。 |

## 使用说明

- 相关测试：`SqlDbStoreTest.java`
