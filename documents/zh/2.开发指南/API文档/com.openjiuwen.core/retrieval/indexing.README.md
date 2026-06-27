# indexing

`com.openjiuwen.core.retrieval.indexing` 负责把原始文件、链接或内存中的内容转换为可检索索引。该子树覆盖索引后端选择、文档解析、文本切分、chunk 生成与三元组抽取。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`indexer`](./indexing/indexer.README.md) | 面向 `VectorStore` 或 Milvus 的索引构建、更新、删除与元信息查询实现。 |
| [`processor`](./indexing/processor.README.md) | 通用处理器抽象，以及解析、切分、chunking、抽取等处理链。 |

## 关键行为

- `IndexerFactoryTest` 验证 `IndexerFactory` 会为 `MilvusVectorStore` 返回 `MilvusIndexer`，其余 `VectorStore` 返回 `InMemoryIndexer`。
- `InMemoryIndexerTest` 验证内存索引器在批量嵌入时会透传 `callback`，并按 `Embedding.getMaxBatchSize()` 分批写入。
- `MilvusIndexerTest` 验证 Milvus 后端会在建索引前检查 collection、拒绝重复 `doc_id`、删除时按 `doc_id` 过滤，而不是按主键列表删除。
- `AutoParserTest`、`AutoFileParserTest`、`AutoLinkParserTest` 说明解析层支持本地文件和 HTTP/HTTPS 链接，并按后缀或 URL 规则路由到具体解析器。
