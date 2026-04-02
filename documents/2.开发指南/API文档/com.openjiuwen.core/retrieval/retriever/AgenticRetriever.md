# com.openjiuwen.core.retrieval.retriever.AgenticRetriever

## class AgenticRetriever

```java
public class AgenticRetriever extends AbstractRetriever
```

Retriever that adds iterative query rewriting and triple reading on top of a base retriever.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `READ_PROMPT` | `static final String` | `""" Your task is to find facts that help answer an input question. ...` | Prompt used to turn retrieved passages into knowledge triples. |
| `REWRITE_PROMPT` | `static final String` | `""" Given a question and its associated retrieved knowledge triples, ...` | Prompt used to ask the LLM whether another retrieval turn is needed. |
| `retriever` | `final Retriever` | `-` | Wrapped base retriever used for actual retrieval work. |
| `llm` | `final BaseModelClient` | `-` | LLM client used for triple extraction and query rewriting. |
| `maxIter` | `final int` | `-` | Maximum number of retrieval / rewrite turns. |
| `graphRetriever` | `final boolean` | `-` | Whether the wrapped retriever is a `GraphRetriever`. |
| `defaultMode` | `final String` | `-` | Default retrieval mode inferred from the wrapped retriever index type. |

## Constructors

| Signature | Description |
| --- | --- |
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient)` | Create an agentic retriever with `maxIter = 2`. |
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter)` | Create an agentic retriever with an explicit maximum number of rewrite turns. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean isGraphRetriever()` | Return whether the wrapped retriever is graph-aware and will use graph expansion logic. |
| `public String getDefaultMode()` | Return the fallback mode derived from the wrapped retriever index type. |
| `public String getIndexType()` | Delegate the index-type lookup to the wrapped retriever. |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | Run iterative retrieval. Graph-backed retrievers expand linked triples; non-graph retrievers perform repeated retrieve-read-rewrite turns and fuse the history with RRF. |
| `public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | Apply `retrieve(...)` to each query in order. |
| `public void close()` | Close the wrapped retriever and ignore close-time exceptions. |

## Notes

- The implementation stops early when the LLM reports that the current triples are sufficient or fails to return a usable rewrite.
- Related tests: `RetrievalCoreTest.java`.
