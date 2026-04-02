# com.openjiuwen.core.retrieval.common.IndexConfig

## class IndexConfig

```java
public class IndexConfig
```

Index configuration.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `indexName` | `String` | `-` | index name. |
| `indexType` | `String` | `"hybrid"` | index type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public IndexConfig()` | Create a new `IndexConfig` instance. |
| `public IndexConfig(String indexName)` | Create a new `IndexConfig` instance. |
| `public IndexConfig(String indexName, String indexType)` | Create a new `IndexConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void validate()` | Execute `validate`. |
| `public String getIndexName()` | Return the index name. |
| `public void setIndexName(String indexName)` | Update the index name. |
| `public String getIndexType()` | Return the index type. |
| `public void setIndexType(String indexType)` | Update the index type. |

## Notes

- Related tests: `InMemoryIndexerTest.java`, `MilvusIndexerTest.java`, `MilvusKnowledgeBaseTest.java`, `RetrievalCoreTest.java`.
