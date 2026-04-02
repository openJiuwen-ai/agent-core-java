# com.openjiuwen.core.retrieval.retriever.GraphRetriever

## class GraphRetriever

```java
public class GraphRetriever extends AbstractRetriever
```

Graph-aware retriever that expands retrieved chunks through linked triples.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `chunkRetriever` | `final Retriever` | chunk retriever. |
| `tripleRetriever` | `final Retriever` | triple retriever. |
| `vectorStore` | `final VectorStore` | vector store. |
| `embedModel` | `final Embedding` | embed model. |
| `chunkCollection` | `final String` | chunk collection. |
| `tripleCollection` | `final String` | triple collection. |
| `indexType` | `String` | index type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever)` | Create a new `GraphRetriever` instance. |
| `public GraphRetriever(VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | Create a new `GraphRetriever` instance. |
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever, VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | Create a new `GraphRetriever` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void setIndexType(String indexType)` | Update the index type. |
| `public String getIndexType()` | Return the index type. |
| `public boolean supportsMode(String mode)` | Execute `supportsMode`. |
| `public Retriever getRetrieverForMode(String mode, boolean isChunk)` | Return the retriever for mode. |
| `Retriever chunkModeRetriever = getRetrieverForMode(mode, true)` | Return the retriever for mode. |

## Notes

- Related tests: `KnowledgeBaseTest.java`, `RetrievalCoreTest.java`.
