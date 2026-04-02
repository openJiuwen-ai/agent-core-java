# com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig

## class KnowledgeBaseConfig

```java
public class KnowledgeBaseConfig
```

Knowledge base configuration.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `kbId` | `String` | `-` | kb id. |
| `indexType` | `String` | `"hybrid"` | index type. |
| `useGraph` | `boolean` | `false` | use graph. |
| `chunkSize` | `int` | `512` | chunk size. |
| `chunkOverlap` | `int` | `50` | chunk overlap. |

## Constructors

| Signature | Description |
| --- | --- |
| `public KnowledgeBaseConfig()` | Create a new `KnowledgeBaseConfig` instance. |
| `public KnowledgeBaseConfig(String kbId)` | Create a new `KnowledgeBaseConfig` instance. |
| `public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap)` | Create a new `KnowledgeBaseConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void validate()` | Execute `validate`. |
| `public String getKbId()` | Return the kb id. |
| `public void setKbId(String kbId)` | Update the kb id. |
| `public String getIndexType()` | Return the index type. |
| `public void setIndexType(String indexType)` | Update the index type. |
| `public boolean isUseGraph()` | Return whether use graph. |
| `public void setUseGraph(boolean useGraph)` | Update the use graph. |
| `public int getChunkSize()` | Return the chunk size. |
| `public void setChunkSize(int chunkSize)` | Update the chunk size. |
| `public int getChunkOverlap()` | Return the chunk overlap. |
| `public void setChunkOverlap(int chunkOverlap)` | Update the chunk overlap. |

## Notes

- Related tests: `ConfigTest.java`, `KnowledgeBaseTest.java`, `MilvusKnowledgeBaseTest.java`, `PGVectorKnowledgeBaseTest.java`, `RetrievalCoreTest.java`.
