# com.openjiuwen.core.memory.common.DistributedLock

## 类 DistributedLock

```java
public class DistributedLock implements AutoCloseable
```

`DistributedLock` 是 `com.openjiuwen.core.memory.common` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `store` | `BaseKVStore` | 字段 `store`。 |
| `lockKey` | `String` | 字段 `lockKey`。 |
| `ttl` | `int` | 字段 `ttl`。 |
| `retryDelayMs` | `long` | 字段 `retryDelayMs`。 |
| `lockValue` | `String` | 字段 `lockValue`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DistributedLock(BaseKVStore store, String lockName)` | 创建 `DistributedLock` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void acquire()` | 执行 `acquire`。 |
| `public void release()` | 执行 `release`。 |
| `public void close()` | 执行 `close`。 |
