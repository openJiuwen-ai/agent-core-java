# com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig

## 接口 IndexBackendConfig

```java
public interface IndexBackendConfig
```

`IndexBackendConfig` 统一定义索引后端需要暴露的命名和字段约定，使 `Indexer` 与 `VectorStore` 在 collection schema 上保持一致。

## 抽象方法

- `String getDatabaseName()`：返回数据库名或逻辑库名。
- `String getDistanceMetric()`：返回距离度量方式。
- `String getIndexType()`：返回索引类型，例如 `vector`、`hybrid`、`bm25`。
- `String getTextField()`：正文列名。
- `String getVectorField()`：稠密向量列名。
- `String getSparseVectorField()`：稀疏向量列名。
- `String getMetadataField()`：metadata 列名。
- `String getDocIdField()`：文档 id 列名。
