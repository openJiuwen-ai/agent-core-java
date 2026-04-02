# graph

`com.openjiuwen.core.foundation.store.graph` 定义图存储接口、内存实现、后端工厂以及相关配置对象。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`GraphStore`](graph/GraphStore.md) | 图存储统一接口。 |
| [`InMemoryGraphStore`](graph/InMemoryGraphStore.md) | 内存版图存储实现。 |
| [`GraphStoreFactory`](graph/GraphStoreFactory.md) | 图存储后端注册与实例化工厂。 |
| [`GraphConfig`](graph/GraphConfig.md) | 图存储总配置对象。 |
| [`GraphStoreStorageConfig`](graph/GraphStoreStorageConfig.md) | 图对象字段容量配置。 |
| [`GraphStoreIndexConfig`](graph/GraphStoreIndexConfig.md) | 图索引与 BM25 相关配置。 |
| [`BM25Config`](graph/BM25Config.md) | BM25 参数配置。 |
| [`GraphUtils`](graph/GraphUtils.md) | 图存储批处理工具。 |

## 说明

- `GraphStoreFactory` 默认注册 `in_memory` 后端。
- `InMemoryGraphStore` 是轻量实现，搜索与过滤逻辑以基础占位能力为主，不应当作完整图数据库功能描述。
