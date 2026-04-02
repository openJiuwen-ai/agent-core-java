# com.openjiuwen.core.foundation.store.kv.DbBasedKVStore

## class DbBasedKVStore

```java
public class DbBasedKVStore extends BaseKVStore
```

JDBC-backed KV store using a simple two-column table.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `dbStore` | `final BaseDbStore<?>` | `-` | Db store. |
| `tableName` | `final String` | `-` | Table name. |

## Constructors

| Signature | Description |
| --- | --- |
| `public DbBasedKVStore(BaseDbStore<?> dbStore)` | Create a new `DbBasedKVStore` instance. |
| `public DbBasedKVStore(BaseDbStore<?> dbStore, String tableName)` | Create a new `DbBasedKVStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void set(String key, Object value)` | Execute `set`. |
| `public boolean exclusiveSet(String key, Object value, Integer expiry)` | Execute `exclusiveSet`. |
| `public Object get(String key)` | Execute `get`. |
| `public boolean exists(String key)` | Execute `exists`. |
| `public void delete(String key)` | Delete the requested resource. |
| `public Map<String, Object> getByPrefix(String prefix)` | Return the by prefix. |
| `public void deleteByPrefix(String prefix, Integer batchSize)` | Delete the requested resource. |
| `public List<Object> mget(List<String> keys)` | Execute `mget`. |
| `public int batchDelete(List<String> keys, Integer batchSize)` | Execute `batchDelete`. |
| `public KVStorePipeline pipeline()` | Execute `pipeline`. |
| `private Connection getConnection() throws SQLException` | Return the connection. |
| `private void initializeTable()` | Execute `initializeTable`. |
