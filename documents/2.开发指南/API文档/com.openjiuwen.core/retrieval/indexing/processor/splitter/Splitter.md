# com.openjiuwen.core.retrieval.indexing.processor.splitter.Splitter

## 抽象类 Splitter

```java
public abstract class Splitter implements Processor<List<Document>, List<TextChunk>>
```

`Splitter` 是面向 `Document` 的切分抽象，与 `Chunker` 类似，但更强调句子和文本分割语义。

## 受保护构造说明

`Splitter` 只暴露受保护构造器 `Splitter(int chunkSize, int chunkOverlap)`，供具体切分器在初始化时复用。

会校验：

- `chunkSize > 0`
- `chunkOverlap >= 0`
- `chunkOverlap < chunkSize`

## 抽象方法

- `List<String> splitText(String text)`：切分单段文本。

## 公开方法

- `public List<TextChunk> getNodesFromDocuments(List<Document> documents)`：把切分结果包装成 `TextChunk`，并补入 `chunk_index`、`total_chunks`、`chunk_id` metadata。
- `public List<TextChunk> process(List<Document> input, Map<String, Object> options)`：默认委托给 `getNodesFromDocuments(...)`。
