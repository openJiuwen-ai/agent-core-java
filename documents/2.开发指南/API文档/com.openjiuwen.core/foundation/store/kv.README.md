# kv

`com.openjiuwen.core.foundation.store.kv` contains the key-value store implementations used by short-lived or database-backed persistence.

## Core Types

| Type | Description |
| --- | --- |
| [`DbBasedKVStore`](kv/DbBasedKVStore.md) | JDBC-backed KV store using a simple two-column table. |
| [`InMemoryKVStore`](kv/InMemoryKVStore.md) | In-memory key-value store with optional expiry support. |
