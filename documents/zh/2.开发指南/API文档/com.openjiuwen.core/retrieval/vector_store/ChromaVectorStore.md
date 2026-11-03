# com.openjiuwen.retrieval.vector_store.ChromaVectorStore

`retrieval` 模块的 `ChromaVectorStore` 提供向量检索、文本过滤及集合管理。

## 构造与持久化

| 构造方式 | 存储行为 |
| --- | --- |
| `new ChromaVectorStore(config)` | 实例内存存储，适用于临时数据。 |
| `new ChromaVectorStore(config, "memory://example")` | 实例内存存储；不同实例不共享数据。 |
| `new ChromaVectorStore(config, directory)` | 在指定目录保存数据，相同目录和数据库的新实例可以读取已有集合。 |

第二个参数是存储目录，不是索引类型。索引方式由知识库和检索配置决定。
`VectorStoreFactory` 和 `KnowledgeRetrievalCompConfig` 使用连接参数
`chroma_path` 指定同一目录，可在入库后重新创建工作流进行检索。

磁盘实现使用带格式版本的 `jiuwen-chroma-<database-hash>.json` 文件。
写入通过文件锁与临时文件替换协调，读取损坏或不支持的格式时明确报错，
不会把已有数据静默替换为空集合。该文件是 Java 适配器的存储格式，
不能直接读取 Python Chroma 的 SQLite 数据库；跨语言数据迁移需重新导入文档。

本类与 `com.openjiuwen.core.foundation.store.vector.ChromaVectorStore`
是不同接口层的实现，调用时请确认导入的包名。
