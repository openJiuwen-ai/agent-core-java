# index

`com.openjiuwen.core.memory.manage.index` 提供长期记忆的核心管理器实现，分别处理分片记忆、摘要记忆、变量记忆，以及跨类型写入协调。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`BaseMemoryManager`](./index/BaseMemoryManager.md) | 各类记忆管理器实现共享的抽象基类，封装加解密与共享工具。 |
| [`FragmentMemoryManager`](./index/FragmentMemoryManager.md) | 管理用户画像类分片记忆的增删改查与向量写入。 |
| [`SummaryManager`](./index/SummaryManager.md) | 管理历史摘要记忆。 |
| [`VariableManager`](./index/VariableManager.md) | 基于 KV 存储管理变量型记忆。 |
| [`WriteManager`](./index/WriteManager.md) | 统一协调各类记忆的新增、更新与删除。 |

## 使用说明

- `LongTermMemory.setConfig(...)` 会把 `FragmentMemoryManager`、`VariableManager`、`SummaryManager` 注册到 `WriteManager` 与 `SearchManager`。
