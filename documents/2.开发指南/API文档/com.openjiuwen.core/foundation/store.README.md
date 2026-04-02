# store

`com.openjiuwen.core.foundation.store` 汇总 foundation 层的存储抽象与适配实现，覆盖嵌入、图存储、向量存储、键值存储、数据库与本地对象存储。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`base_embedding`](store/base_embedding.README.md) | 嵌入模型抽象与配置对象。 |
| [`db`](store/db.README.md) | 基于 JDBC 的默认数据库引擎持有器。 |
| [`graph`](store/graph.README.md) | 图存储接口、内存实现、工厂与配置对象。 |
| [`kv`](store/kv.README.md) | 内存与数据库后端的键值存储实现。 |
| [`object`](store/object.README.md) | 本地文件系统对象存储客户端。 |
| [`query`](store/query.README.md) | Milvus 与 Chroma 查询方言定义及注册入口。 |
| [`vector`](store/vector.README.md) | foundation 到 retrieval 的向量存储适配器与迁移工具。 |
| [`vector_fields`](store/vector_fields.README.md) | 面向不同后端的向量索引配置对象与默认 schema 辅助类。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`StoreFactory`](store/StoreFactory.md) | 按短名称创建内建向量存储适配器。 |

## 说明

- `StoreFactory` 当前只内建 `in_memory`、`chroma`、`milvus`、`pgvector` 等固定类型映射，未知类型返回 `null`。
- 向量存储子包的公开类型主要是不同后端的适配器，统一桥接到 retrieval 层实现。
