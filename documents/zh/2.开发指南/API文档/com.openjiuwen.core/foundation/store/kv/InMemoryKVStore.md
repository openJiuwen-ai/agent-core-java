# com.openjiuwen.core.foundation.store.kv.InMemoryKVStore

## class InMemoryKVStore

```java
public class InMemoryKVStore extends BaseKVStore
```

基于内存 `Map` 的键值存储实现。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void set(String key, Object value)` | 写入键值，并清除该键已有的过期时间。 |
| `public boolean exclusiveSet(String key, Object value, Integer expiry)` | 仅在键不存在时写入，可选设置过期秒数。 |
| `public Object get(String key)` | 读取键值；若键已过期会先清理。 |
| `public boolean exists(String key)` | 判断键是否存在；过期键会被清理。 |
| `public void delete(String key)` | 删除单个键。 |
| `public Map<String, Object> getByPrefix(String prefix)` | 返回指定前缀下的所有键值。 |
| `public void deleteByPrefix(String prefix, Integer batchSize)` | 删除指定前缀下的所有键。 |
| `public List<Object> mget(List<String> keys)` | 批量读取多个键。 |
| `public int batchDelete(List<String> keys, Integer batchSize)` | 批量删除多个键并返回实际删除数量。 |
| `public KVStorePipeline pipeline()` | 返回仅支持 `set`、`get`、`exists` 的简单管道。 |

## 使用说明

- 过期清理采用读取时惰性触发，不包含后台定时线程。
- `deleteByPrefix` 与 `batchDelete` 的 `batchSize` 参数当前未参与实现逻辑。
