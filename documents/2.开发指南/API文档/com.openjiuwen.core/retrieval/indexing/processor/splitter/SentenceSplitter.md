# com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter

## class SentenceSplitter

```java
public class SentenceSplitter extends Splitter
```

Sentence-aware splitter with lightweight language detection and tokenizer-aware windows.

## Constructors

| Signature | Description |
| --- | --- |
| `public SentenceSplitter(int chunkSize, int chunkOverlap)` | Create a new `SentenceSplitter` instance. |
| `public SentenceSplitter(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language)` | Create a new `SentenceSplitter` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<String> splitText(String text)` | Split the input text into smaller segments. |

## Notes

- Related tests: `SentenceSplitterTest.java`.
