# com.openjiuwen.core.foundation.store.kv.DbBasedKVStore

## class DbBasedKVStore

```java
public class DbBasedKVStore extends BaseKVStore
```

基于 `BaseDbStore` 的 JDBC 键值存储实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DbBasedKVStore(BaseDbStore<?> dbStore)` | 使用默认表名 `kv_store` 初始化。 |
| `public DbBasedKVStore(BaseDbStore<?> dbStore, String tableName)` | 使用指定表名初始化，并自动建表。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void set(String key, Object value)` | 用 upsert 方式写入键值。 |
| `public boolean exclusiveSet(String key, Object value, Integer expiry)` | 仅在键不存在时写入。 |
| `public Object get(String key)` | 读取字符串值；不存在时返回 `null`。 |
| `public boolean exists(String key)` | 判断键是否存在。 |
| `public void delete(String key)` | 删除单个键。 |
| `public Map<String, Object> getByPrefix(String prefix)` | 返回指定前缀下的键值。 |
| `public void deleteByPrefix(String prefix, Integer batchSize)` | 删除指定前缀下的键。 |
| `public List<Object> mget(List<String> keys)` | 批量读取多个键。 |
| `public int batchDelete(List<String> keys, Integer batchSize)` | 批量删除并返回删除数量。 |
| `public KVStorePipeline pipeline()` | 返回仅支持 `set`、`get`、`exists` 的简单管道。 |

## 使用说明

- 构造时会自动执行 `CREATE TABLE IF NOT EXISTS` 初始化表结构。
- 当前实现把值统一保存为字符串。
- `exclusiveSet` 的 `expiry` 参数当前未落库实现。
