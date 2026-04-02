# kv

`com.openjiuwen.core.foundation.store.kv` 提供键值存储的内存实现与数据库实现，两者都对齐 `BaseKVStore` 的基础接口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`InMemoryKVStore`](kv/InMemoryKVStore.md) | 基于内存 `Map` 的键值存储，支持惰性过期清理。 |
| [`DbBasedKVStore`](kv/DbBasedKVStore.md) | 基于 `BaseDbStore` 的 JDBC 键值存储。 |
