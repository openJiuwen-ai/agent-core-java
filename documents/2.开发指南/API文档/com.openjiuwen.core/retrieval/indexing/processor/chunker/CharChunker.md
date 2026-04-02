# com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker

## 类 CharChunker

```java
public class CharChunker extends Chunker
```

`CharChunker` 按固定字符窗口切块，适合没有 tokenizer 或只需要稳定字符长度窗口的场景。

## 构造方法

### `public CharChunker(int chunkSize, int chunkOverlap)`

复用父类对 `chunkSize > 0`、`chunkOverlap >= 0`、`chunkOverlap < chunkSize` 的校验。

## 公开方法

### `public List<String> chunkText(String text)`

- `text == null` 或空串时返回空列表。
- 按步长 `chunkSize - chunkOverlap` 向前滑动。
- 最后一块长度不足 `chunkSize` 时保留剩余文本。

## 相关测试

- `CharChunkerTest` 覆盖固定长度切块、overlap、短文本单块返回以及非法构造参数异常。
