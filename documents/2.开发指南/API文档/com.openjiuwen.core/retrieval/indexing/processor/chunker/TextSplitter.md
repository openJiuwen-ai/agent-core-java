# com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter

## 抽象类 TextSplitter

```java
public abstract class TextSplitter
```

`TextSplitter` 是一个更轻量的切分基类，只约定如何把单个 `Document` 转成 `TextChunk` 列表。

## 抽象方法

- `List<TextChunk> split(Document doc)`：切分单个文档。
