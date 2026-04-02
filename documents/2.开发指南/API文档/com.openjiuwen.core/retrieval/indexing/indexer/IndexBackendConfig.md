# com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig

## interface IndexBackendConfig

```java
public interface IndexBackendConfig
```

Shared config surface that must match between vector store and index manager.

## Methods

| Signature | Description |
| --- | --- |
| `String getDatabaseName()` | Return the database name. |
| `String getDistanceMetric()` | Return the distance metric. |
| `String getIndexType()` | Return the index type. |
| `String getTextField()` | Return the text field. |
| `String getVectorField()` | Return the vector field. |
| `String getSparseVectorField()` | Return the sparse vector field. |
| `String getMetadataField()` | Return the metadata field. |
| `String getDocIdField()` | Return the doc id field. |
