# com.openjiuwen.core.retrieval.indexing.processor.splitter.Splitter

## class Splitter

```java
public abstract class Splitter implements Processor<List<Document>, List<TextChunk>>
```

Text splitter abstraction.

## Methods

| Signature | Description |
| --- | --- |
| `public abstract List<String> splitText(String text)` | Split the input text into smaller segments. |
| `public List<TextChunk> getNodesFromDocuments(List<Document> documents)` | Execute `getNodesFromDocuments`. |
| `public List<TextChunk> process(List<Document> input, Map<String, Object> options)` | Process the input values and return transformed results. |

## Notes

- Related tests: `SentenceSplitterTest.java`.
