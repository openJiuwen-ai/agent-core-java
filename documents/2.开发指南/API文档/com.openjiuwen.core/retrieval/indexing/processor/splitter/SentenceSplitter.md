# com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter

## 类 SentenceSplitter

```java
public class SentenceSplitter extends Splitter
```

`SentenceSplitter` 支持句子边界切分、中文/英文自动识别，以及基于 token 计数的窗口和 overlap 控制。

## 构造方法

- `public SentenceSplitter(int chunkSize, int chunkOverlap)`：默认 `tokenizer = null`、`language = "auto"`。
- `public SentenceSplitter(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language)`：完整配置构造。

## 公开方法

### `public List<String> splitText(String text)`

- 空白文本返回空列表。
- `language = auto` 时会根据中文字符占比自动判断中英文。
- 中文块拼接时不插入空格，英文块拼接时使用空格。
- 当加入下一句会超出 `chunkSize` 时，会按 `chunkOverlap` 回收窗口尾部句子形成重叠上下文。

## 相关测试

- `SentenceSplitterTest` 验证中文标点切句与英文 tokenizer 窗口控制。
