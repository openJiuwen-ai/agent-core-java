# com.openjiuwen.core.retrieval.retriever.AbstractStoreBackedRetriever

## class AbstractStoreBackedRetriever

```java
public abstract class AbstractStoreBackedRetriever extends AbstractRetriever
```

Base class for retrievers backed by a vector store.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `vectorStore` | `final VectorStore` | vector store. |
| `embedModel` | `final Embedding` | embed model. |

## Constructors

| Signature | Description |
| --- | --- |
| `protected AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel)` | Create a new `AbstractStoreBackedRetriever` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public VectorStore getVectorStore()` | Return the vector store. |
| `public Embedding getEmbedModel()` | Return the embed model. |
| `public String getIndexType()` | Return the index type. |
