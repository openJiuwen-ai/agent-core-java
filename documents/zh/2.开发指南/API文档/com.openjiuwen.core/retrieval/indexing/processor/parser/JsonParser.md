# com.openjiuwen.core.workflow.component.llm.JsonParser

## 类 JsonParser

```java
public class JsonParser extends Parser
```

`JsonParser` 读取 JSON 文件，并在可能的情况下用 Jackson 重新格式化为易读的缩进文本。

## 公开方法

- `parse(...)`：读取 UTF-8 文件内容；读取失败时返回空列表。
- `supports(String doc)`：仅支持 `.json`。

## 格式化规则

- JSON 合法时，返回 pretty print 文本。
- JSON 非法时，退回原始字符串，不额外抛错。
