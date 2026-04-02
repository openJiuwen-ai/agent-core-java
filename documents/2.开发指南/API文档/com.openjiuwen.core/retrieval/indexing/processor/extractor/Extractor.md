# com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor

## class Extractor

```java
public abstract class Extractor implements Processor<List<TextChunk>, List<Triple>>
```

Triple extractor abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)` | Execute `extract`. |
| `public List<Triple> process(List<TextChunk> input, Map<String, Object> options)` | Execute `process`. |

## Notes

- Related tests: `KnowledgeBaseTest.java`, `LLMTripleExtractorTest.java`, `SimpleTripleExtractorTest.java`.
