# com.openjiuwen.core.retrieval.indexing.processor.extractor.SimpleTripleExtractor

## 类 SimpleTripleExtractor

```java
public class SimpleTripleExtractor extends Extractor
```

`SimpleTripleExtractor` 是无需模型的轻量三元组抽取器。它按句号、问号等标点切句，再按空白分词，使用前三段及其后续文本组装 `subject/predicate/object`。

## 公开方法

### `public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)`

- `chunks == null` 时返回空列表。
- 少于 3 个 token 的句子会被跳过。
- 每条 `Triple` 的 metadata 中会附加 `doc_id`、`chunk_id` 和序列化后的 `triple` 字符串。

## 相关测试

- `SimpleTripleExtractorTest` 验证基本抽取、空输入、短句跳过和多 chunk 输入。
