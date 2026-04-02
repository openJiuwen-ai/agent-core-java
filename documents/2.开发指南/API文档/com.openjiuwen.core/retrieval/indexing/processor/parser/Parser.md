# com.openjiuwen.core.retrieval.indexing.processor.parser.Parser

## 抽象类 Parser

```java
public abstract class Parser implements Processor<String, List<Document>>
```

`Parser` 统一了“输入一个字符串路径或 URL，输出 `Document` 列表”的抽象。

## 可覆写方法

- `protected abstract String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)`：解析正文。
- `public boolean supports(String doc)`：默认返回 `false`，子类按需覆写。

## 默认实现

### `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)`

- 调用 `parseContent(...)`。
- 若正文为 `null`，返回空列表。
- 否则生成单个 `Document(docId, content, Map.of())`。

### `public List<Document> process(String input, Map<String, Object> options)`

等价于 `parse(input, "", null, options)`。
