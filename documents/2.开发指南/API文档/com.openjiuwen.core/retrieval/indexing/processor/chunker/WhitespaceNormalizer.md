# com.openjiuwen.core.retrieval.indexing.processor.chunker.WhitespaceNormalizer

## 类 WhitespaceNormalizer

```java
public class WhitespaceNormalizer implements TextPreprocessor
```

`WhitespaceNormalizer` 将连续空白折叠为单个空格，并去掉首尾空白。

## 公开方法

### `public String process(String text)`

- `text == null` 时返回空串。
- 使用 `replaceAll("\\s+", " ")` 统一空白。
