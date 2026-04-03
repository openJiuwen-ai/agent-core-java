# com.openjiuwen.core.retrieval.indexing.processor.chunker.CharSplitterText

## 类 CharSplitterText

```java
public class CharSplitterText extends TextSplitter
```

`CharSplitterText` 是不依赖 tokenizer 的轻量 `TextSplitter`，直接按字符长度把单个 `Document` 切成多个 `TextChunk`。

## 构造方法

### `public CharSplitterText()`

默认使用 `chunkSize = 200`、`chunkOverlap = 40`。

### `public CharSplitterText(Integer chunkSize, Integer chunkOverlap)`

- `null` 参数会回退到默认值。
- `chunkOverlap` 会被截断到 `[0, chunkSize - 1]` 区间。

## 公开方法

### `public List<TextChunk> split(Document doc)`

- 使用 `UUID.randomUUID()` 为每个 chunk 生成新 id。
- 继承原始 `docId` 与 metadata。
- 当输入文本为空时返回空列表。
