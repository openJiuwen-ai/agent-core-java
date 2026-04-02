# com.openjiuwen.core.retrieval.indexing.processor.extractor.LLMTripleExtractor

## class LLMTripleExtractor

```java
public class LLMTripleExtractor extends Extractor
```

LLM-backed triple extractor aligned with the Python implementation.

## Constructors

| Signature | Description |
| --- | --- |
| `public LLMTripleExtractor(BaseModelClient llmClient, String modelName)` | Create a new `LLMTripleExtractor` instance. |
| `public LLMTripleExtractor(BaseModelClient llmClient, String modelName, float temperature, int maxConcurrent)` | Create a new `LLMTripleExtractor` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)` | Extract triples from the provided text chunks. |

## Notes

- Related tests: `LLMTripleExtractorTest.java`.
