# com.openjiuwen.core.retrieval.indexing.processor.chunker.TokenizerChunker

## 类 TokenizerChunker

```java
public class TokenizerChunker extends Chunker
```

`TokenizerChunker` 借助 `SentenceSplitter` 做 token-aware 切块，适合需要按句子与 token 窗口共同控制块大小的场景。

## 构造方法

- `public TokenizerChunker(int chunkSize, int chunkOverlap)`：使用默认 `language = "auto"`。
- `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer)`：显式传入 tokenizer。
- `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language, Map<String, Object> splitterConfig)`：完整配置构造。

## 公开方法

- `List<String> chunkText(String text)`：委托 `SentenceSplitter.splitText(text)`。
- `Function<String, List<String>> getTokenizer()`：返回当前 tokenizer。
- `String getLanguage()`：返回语言配置。
- `Map<String, Object> getSplitterConfig()`：返回创建时保存的 splitter 配置副本。

## 相关测试

- `TokenizerChunkerTest` 验证英文 tokenizer、生中文自动识别、overlap 场景以及默认语言与默认配置值。
