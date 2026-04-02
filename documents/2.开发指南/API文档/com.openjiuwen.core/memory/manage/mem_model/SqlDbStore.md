# com.openjiuwen.core.memory.manage.mem_model.SqlDbStore

## class SqlDbStore

```java
public class SqlDbStore
```

JDBC-based SQL CRUD wrapper for memory tables.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `dbStore` | `BaseDbStore<?>` | db store. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SqlDbStore(BaseDbStore<?> dbStore)` | Create a new `SqlDbStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public BaseDbStore<?> getDbStore()` | Execute `getDbStore`. |
| `public Object getEngine()` | Execute `getEngine`. |
| `public boolean write(String table, Map<String, Object> data)` | Insert a row into the specified table. |
| `public Map<String, Object> get(String table, String recordId, List<String> columns)` | Get a single record by id. |
| `public List<Map<String, Object>> getWithSort(String table, Map<String, Object> filters, String sortBy, String order, int limit)` | Get rows with filters, sorting, and limit. |
| `public boolean exist(String table, Map<String, Object> conditions)` | Check if a record exists matching the given conditions. |
| `public List<Map<String, Object>> batchGet(String table, List<Map<String, Object>> conditionsList)` | Get rows matching any condition group in the provided list. |
| `public List<Map<String, Object>> conditionGet(String table, Map<String, List<Object>> conditions, List<String> columns)` | Get rows matching IN conditions on specified columns. |
| `public boolean update(String table, Map<String, Object> conditions, Map<String, Object> data)` | Update rows matching the given conditions. |
| `public boolean delete(String table, Map<String, Object> conditions)` | Delete rows matching the given conditions. |
| `public boolean deleteTable(String tableName)` | Drop a table if it exists. |
| `public TableInfo getTable(String tableName)` | Reflect table metadata for public callers that need schema access. |

## Nested Public Types

| Type | Signature | Description |
| --- | --- | --- |
| `TableInfo` | `public static final class TableInfo` | Nested public type declared on this page. |
| `ColumnInfo` | `public static final class ColumnInfo` | Nested public type declared on this page. |

## Notes

- Related tests: `SqlDbStoreTest.java`
