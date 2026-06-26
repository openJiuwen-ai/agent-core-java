# store

`com.openjiuwen.core.session.store` 提供会话持久化所需的抽象存储接口，以及内存版和文件版存储实现。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`FileStore`](./store/FileStore.md) | 预留中的文件存储实现。 |
| [`MemoryStore`](./store/MemoryStore.md) | 基于 `HashMap` 的内存存储实现。 |
| [`Store`](./store/Store.md) | 键值存储抽象基类。 |
