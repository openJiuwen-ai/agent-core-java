# common

`com.openjiuwen.core.retrieval.common` 提供 retrieval 领域的公共配置对象、结果模型、排序配置、异常与参数校验工具。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseCallback`](./common/BaseCallback.md) | 批处理回调基类，记录总量与批次调用次数。 |
| [`BaseRankConfig`](./common/BaseRankConfig.md) | 排序配置基类，统一 ranker 名称、方向与参数导出。 |
| [`Document`](./common/Document.md) | 原始文档模型，保存 `id`、`text` 与 `metadata`。 |
| [`EmbeddingConfig`](./common/EmbeddingConfig.md) | embedding 模型访问配置。 |
| [`IndexConfig`](./common/IndexConfig.md) | 索引名称与索引类型配置。 |
| [`KnowledgeBaseConfig`](./common/KnowledgeBaseConfig.md) | 知识库级配置，定义 `kbId`、索引模式与分块参数。 |
| [`LoggingCallback`](./common/LoggingCallback.md) | 基于 SLF4J 的进度日志回调。 |
| [`MultiKBRetrievalResult`](./common/MultiKBRetrievalResult.md) | 多知识库聚合检索结果。 |
| [`MultimodalDocument`](./common/MultimodalDocument.md) | 支持文本、图片、音频、视频字段的多模态文档模型。 |
| [`RRFRankConfig`](./common/RRFRankConfig.md) | RRF 融合配置。 |
| [`RerankerConfig`](./common/RerankerConfig.md) | 重排器远程调用配置。 |
| [`ResultRankRegistry`](./common/ResultRankRegistry.md) | 数据库原生 ranker 实现注册表。 |
| [`RetrievalConfig`](./common/RetrievalConfig.md) | 单次检索请求配置。 |
| [`RetrievalExceptions`](./common/RetrievalExceptions.md) | retrieval 相关异常构造工具。 |
| [`RetrievalResult`](./common/RetrievalResult.md) | 面向调用方的检索结果模型。 |
| [`RetrievalValidation`](./common/RetrievalValidation.md) | 公共参数校验工具。 |
| [`SearchResult`](./common/SearchResult.md) | 搜索层原始结果模型。 |
| [`StoreType`](./common/StoreType.md) | 支持的向量库提供方枚举。 |
| [`TextChunk`](./common/TextChunk.md) | 文档分块模型，可附带 embedding。 |
| [`TqdmCallback`](./common/TqdmCallback.md) | 轻量级进度回调。 |
| [`Triple`](./common/Triple.md) | 图检索使用的知识三元组。 |
| [`TripleBeam`](./common/TripleBeam.md) | 三元组 beam 搜索状态容器。 |
| [`TripleMemory`](./common/TripleMemory.md) | 三元组记忆与去重容器。 |
| [`VectorStoreConfig`](./common/VectorStoreConfig.md) | 向量库连接与集合配置。 |
| [`WeightedRankConfig`](./common/WeightedRankConfig.md) | 稠密/稀疏结果加权融合配置。 |

## 关键行为

- `KnowledgeBaseConfig`、`IndexConfig`、`VectorStoreConfig`、`RetrievalConfig` 等配置对象会在构造或 setter 中执行参数校验。
- `Document`、`TextChunk`、`SearchResult`、`RetrievalResult` 等模型会对文本、标识符与 `metadata` 做非空或复制保护。
- `MultimodalDocument` 会把文件内容转换为模型可消费的结构化内容，并对 `kind`、`data`、`filePath` 与 `dataId` 做校验。
- `RRFRankConfig` 与 `WeightedRankConfig` 会把 Java 配置对象转换为底层 ranker 需要的参数列表。

## 相关测试

- `ConfigTest`
- `KnowledgeBaseTest`
