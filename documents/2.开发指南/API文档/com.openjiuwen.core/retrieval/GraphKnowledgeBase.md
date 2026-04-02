# com.openjiuwen.core.retrieval.GraphKnowledgeBase

## class GraphKnowledgeBase

```java
public class GraphKnowledgeBase extends KnowledgeBase
```

Knowledge base with optional graph index.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `chunkRetriever` | `final Retriever` | chunk retriever. |
| `tripleRetriever` | `final Retriever` | triple retriever. |
| `graphRetriever` | `GraphRetriever` | graph retriever. |

## Constructors

| Signature | Description |
| --- | --- |
| `public GraphKnowledgeBase(KnowledgeBaseConfig config)` | Create a new `GraphKnowledgeBase` instance. |
| `public GraphKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever chunkRetriever, Retriever tripleRetriever)` | Create a new `GraphKnowledgeBase` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> addDocuments(List<Document> documents)` | Add documents. |
| `GraphRetriever graph = graphRetriever != null ? graphRetriever : new GraphRetriever( chunkRetriever, tripleRetriever, vectorStore, embedModel, chunkIndexName(), tripleIndexName())` | Execute `GraphRetriever`. |
| `stats.put("chunk_index_info", activeIndexManager.getIndexInfo(chunkIndexName()))` | Execute `put`. |
| `public static List<MultiKBRetrievalResult> retrieveMultiGraphKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK)` | Perform retrieval on multiple graph knowledge bases, includes source information. |

## Notes

- Related tests: `KnowledgeBaseTest.java`.
