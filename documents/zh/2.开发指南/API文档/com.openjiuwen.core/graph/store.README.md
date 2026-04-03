# store

`com.openjiuwen.core.graph.store` 提供图状态持久化抽象、状态快照数据模型、序列化器工厂，以及默认的内存存储实现。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`GraphStore`](./store/GraphStore.md) | 为底层 `Store` 增加日志包装的装饰器。 |
| [`GraphStoreState`](./store/GraphStoreState.md) | 保存 Pregel 图恢复所需 channel 值、待处理消息和节点版本的状态快照。 |
| [`InMemoryStore`](./store/InMemoryStore.md) | 使用 `ConcurrentHashMap` 保存状态快照的内存版 `Store`。 |
| [`PendingNode`](./store/PendingNode.md) | 记录待恢复节点名称、状态和异常列表。 |
| [`Serializer`](./store/Serializer.md) | 图状态持久化使用的抽象序列化器与工厂入口。 |
| [`Store`](./store/Store.md) | 图状态持久化接口，定义读取、保存与删除操作。 |

## 说明

- `GraphStoreTest` 覆盖了 `InMemoryStore` 的 CRUD、命名空间前缀删除、深拷贝隔离，以及 `GraphStoreState` / `PendingNode` / `GraphStore` 的基础行为。
