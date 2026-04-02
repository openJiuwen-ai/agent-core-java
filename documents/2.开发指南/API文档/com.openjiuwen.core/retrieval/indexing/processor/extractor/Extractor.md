# com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor

## 抽象类 Extractor

```java
public abstract class Extractor implements Processor<List<TextChunk>, List<Triple>>
```

`Extractor` 把抽取器统一建模为 `List<TextChunk> -> List<Triple>` 的处理器。

## 抽象方法

- `List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)`：执行实际抽取。

## 默认实现

### `public List<Triple> process(List<TextChunk> input, Map<String, Object> options)`

默认直接调用 `extract(input, options)`。
