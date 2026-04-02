# com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser

## 类 TxtMdParser

```java
public class TxtMdParser extends Parser
```

`TxtMdParser` 用于读取普通文本和 Markdown 文件，出错时采用“吞异常并返回空结果”的保守策略。

## 公开方法

- `parse(...)`：读取成功时返回单个 `Document`；任意运行时异常会返回空列表。
- `parseContent(...)`：按 UTF-8 读取文件并做 `trim()`；I/O 失败时返回 `null`。
- `supports(String doc)`：支持 `.txt`、`.md`、`.markdown`。
