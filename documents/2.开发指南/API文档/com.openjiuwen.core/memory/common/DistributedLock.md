# com.openjiuwen.core.memory.common.DistributedLock

## class DistributedLock

```java
public class DistributedLock implements AutoCloseable
```

Synchronous distributed lock using KV store exclusive_set.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `store` | `BaseKVStore` | store. |
| `lockKey` | `String` | lock key. |
| `ttl` | `int` | ttl. |
| `retryDelayMs` | `long` | retry delay ms. |
| `lockValue` | `String` | lock value. |

## Constructors

| Signature | Description |
| --- | --- |
| `public DistributedLock(BaseKVStore store, String lockName)` | Create a new `DistributedLock` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void acquire()` | Execute `acquire`. |
| `public void release()` | Execute `release`. |
| `public void close()` | Execute `close`. |
