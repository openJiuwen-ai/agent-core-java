# com.openjiuwen.core.retrieval.indexing.processor.chunker.CharSplitterText

## class CharSplitterText

```java
public class CharSplitterText extends TextSplitter
```

Simple text splitter based on character length, no dependency on tokenizer.

## Constructors

| Signature | Description |
| --- | --- |
| `public CharSplitterText()` | Create a new `CharSplitterText` instance. |
| `public CharSplitterText(Integer chunkSize, Integer chunkOverlap)` | Create a new `CharSplitterText` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public List<TextChunk> split(Document doc)` | Execute `split`. |
