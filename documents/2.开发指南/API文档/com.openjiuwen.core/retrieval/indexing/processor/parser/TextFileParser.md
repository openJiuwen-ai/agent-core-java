# com.openjiuwen.core.retrieval.indexing.processor.parser.TextFileParser

## 类 TextFileParser

```java
public class TextFileParser extends Parser
```

`TextFileParser` 是最简单的 UTF-8 文本文件解析器，直接返回文件原文。

## 公开方法

- `parseContent(...)`：文件不存在时抛 `RETRIEVAL_INDEXING_FILE_NOT_FOUND`，读取异常也会包装成同类错误。
- `supports(String doc)`：支持 `.txt` 和 `.md`。
