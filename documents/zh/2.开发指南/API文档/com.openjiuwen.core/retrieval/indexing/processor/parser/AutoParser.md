# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser

## 类 AutoParser

```java
public class AutoParser extends Parser
```

`AutoParser` 是统一入口，会先尝试链接解析器，再尝试文件解析器。

## 构造方法

- `public AutoParser()`：默认使用 `AutoLinkParser` 与 `AutoFileParser`。
- `public AutoParser(Parser linkParser, Parser fileParser)`：支持自定义两个子解析器。

## 公开方法

- `parse(...)`：先检查 `linkParser.supports(doc)`，命中则直接解析；否则再检查 `fileParser`。
- `supports(String doc)`：任一子解析器支持即可。

## 相关测试

- `AutoParserTest` 验证 URL 与现有文件都能被识别，且链接解析优先于文件解析。
