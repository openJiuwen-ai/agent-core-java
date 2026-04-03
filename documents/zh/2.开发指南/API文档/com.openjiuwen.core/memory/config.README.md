# config

`com.openjiuwen.core.memory.config` 定义长期记忆的配置模型，覆盖引擎默认模型、消息长度、加密密钥，以及按 agent 或 scope 细分的策略选项。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`AgentMemoryConfig`](./config/AgentMemoryConfig.md) | 单次代理运行的记忆生成与摘要配置。 |
| [`MemoryEngineConfig`](./config/MemoryEngineConfig.md) | 全局记忆引擎配置，声明默认模型、加密密钥和摘要上限。 |
| [`MemoryScopeConfig`](./config/MemoryScopeConfig.md) | 作用域级配置，支持覆盖模型与嵌入参数。 |

## 使用说明

- `MemoryEngineConfig.validateCryptoKey()` 要求 `cryptoKey` 为空数组或长度恰好为 `32` 字节。
- `LongTermMemory.setScopeConfig(...)` 会把 `MemoryScopeConfig` 序列化后写入 KV，并对 API Key 做加密处理。
