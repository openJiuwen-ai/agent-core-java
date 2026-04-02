# com.openjiuwen.core.retrieval.indexing.processor.chunker.SpecialCharacterNormalizer

## 类 SpecialCharacterNormalizer

```java
public class SpecialCharacterNormalizer implements TextPreprocessor
```

`SpecialCharacterNormalizer` 把除换行和制表符外的控制字符统一替换为空格。

## 公开方法

### `public String process(String text)`

- `text == null` 时返回空串。
- 使用正则 `[\p{Cntrl}&&[^\r\n\t]]` 替换不可见控制字符。
