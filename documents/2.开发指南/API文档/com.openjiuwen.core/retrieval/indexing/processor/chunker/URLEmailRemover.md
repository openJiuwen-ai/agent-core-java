# com.openjiuwen.core.retrieval.indexing.processor.chunker.URLEmailRemover

## 类 URLEmailRemover

```java
public class URLEmailRemover implements TextPreprocessor
```

`URLEmailRemover` 用于在切块前去掉 URL 和邮箱地址，减少噪声内容。

## 公开方法

### `public String process(String text)`

- `text == null` 时返回空串。
- 会先删除 `http://` / `https://` URL，再删除邮箱地址，最后做 `trim()`。
