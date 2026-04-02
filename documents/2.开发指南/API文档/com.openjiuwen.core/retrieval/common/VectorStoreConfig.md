# com.openjiuwen.core.retrieval.common.VectorStoreConfig

## 类 VectorStoreConfig

```java
public class VectorStoreConfig
```

向量库连接与集合配置，定义提供方、数据库名、集合名与距离度量。

## 说明

- 默认 `databaseName = ""`、`distanceMetric = "cosine"`。
- `storeProvider` 仅允许 `milvus`、`chroma`、`pgvector`。
- `collectionName` 不能为空白，`distanceMetric` 仅允许 `cosine`、`euclidean`、`dot`。
