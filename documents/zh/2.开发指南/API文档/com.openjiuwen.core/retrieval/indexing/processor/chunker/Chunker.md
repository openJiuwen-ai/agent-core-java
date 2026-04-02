# com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker

## 抽象类 Chunker

```java
public abstract class Chunker implements Processor<List<Document>, List<TextChunk>>
```

`Chunker` 是 `Document -> TextChunk` 的抽象基类，负责参数校验和把字符串切块结果包装成 `TextChunk`。

## 受保护构造说明

`Chunker` 只暴露受保护构造器 `Chunker(int chunkSize, int chunkOverlap)`，供子类初始化时复用。

- 校验 `chunkSize` 为正数。
- 校验 `chunkOverlap` 非负且小于 `chunkSize`。

## 抽象方法

- `List<String> chunkText(String text)`：把单个文本切成字符串列表。

## 公开方法

### `public List<TextChunk> chunkDocuments(List<Document> documents)`

按文档逐个调用 `chunkText(...)`，并为每个 chunk 自动补充：

- `chunk_index`
- `total_chunks`
- `chunk_id`

### `public List<TextChunk> process(List<Document> input, Map<String, Object> options)`

默认委托给 `chunkDocuments(...)`。
