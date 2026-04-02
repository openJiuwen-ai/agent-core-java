# common

`com.openjiuwen.core.memory.common` provides shared locking, crypto, prefix, and parsing helpers used across the memory engine.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`DistributedLock`](./common/DistributedLock.md) | class | Synchronous distributed lock using KV store exclusive_set. |
| [`KvPrefixRegistry`](./common/KvPrefixRegistry.md) | class | Registry for managing KV store key prefixes used by memory modules. |
| [`MemoryCrypto`](./common/MemoryCrypto.md) | class | AES-256-GCM encryption/decryption utilities for memory content. |
| [`MemoryUtils`](./common/MemoryUtils.md) | class | Utility methods for memory module. |

## Notes

- The current page also links the 4 direct public type page(s) defined in this package.
