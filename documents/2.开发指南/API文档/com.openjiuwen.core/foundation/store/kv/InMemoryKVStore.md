# com.openjiuwen.core.foundation.store.kv.InMemoryKVStore

## class InMemoryKVStore

```java
public class InMemoryKVStore extends BaseKVStore
```

In-memory key-value store with optional expiry support.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `values` | `final Map<String, Object>` | `new ConcurrentHashMap<>()` | Values. |
| `expiryAt` | `final Map<String, Long>` | `new ConcurrentHashMap<>()` | Expiry at. |

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
| `private void cleanupIfExpired(String key)` | Execute `cleanupIfExpired`. |
