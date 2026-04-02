# common

`com.openjiuwen.core.memory.common` 提供记忆模块共享的基础工具，包括分布式锁、KV 前缀管理、AES 加解密以及检索结果解析辅助逻辑。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`DistributedLock`](./common/DistributedLock.md) | 基于 `BaseKVStore.exclusive_set(...)` 的同步分布式锁。 |
| [`KvPrefixRegistry`](./common/KvPrefixRegistry.md) | 统一管理记忆模块使用的 KV 前缀。 |
| [`MemoryCrypto`](./common/MemoryCrypto.md) | 提供 AES-256-GCM 记忆内容加解密能力。 |
| [`MemoryUtils`](./common/MemoryUtils.md) | 提供检索命中解析与静态工具方法。 |

## 相关测试

- `MemoryCryptoTest`
