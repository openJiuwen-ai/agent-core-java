# com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser

## 类 AutoLinkParser

```java
public class AutoLinkParser extends Parser
```

`AutoLinkParser` 按 URL 规则在不同链接解析器之间路由。默认优先识别微信公众号文章，其余 HTTP/HTTPS 链接走通用网页解析器。

## 公开常量

- `HTTP_URL_PATTERN`：匹配 HTTP/HTTPS URL。

## 构造方法

- `public AutoLinkParser()`：默认路由顺序为 `WeChatArticleParser` -> `WebPageParser`。
- `public AutoLinkParser(List<Route> routes)`：使用自定义路由列表。

## 公开方法

- `parse(...)`：按顺序找到第一个匹配路由并调用对应 parser；无匹配时返回空列表。
- `supports(String doc)`：只要存在任一可匹配路由即返回 `true`。

## Route 说明

源码声明了一个嵌套 `record Route`，用于组合“匹配条件”和“命中后的解析器”。

- `matches(String value)` 会用路由内保存的谓词判断当前 URL 是否命中。
- 路由命中后，`AutoLinkParser` 会把解析工作委托给该路由持有的解析器实例。

## 相关测试

- `AutoLinkParserTest` 验证微信公众号链接优先匹配，且会把 `doc` 与 `docId` 透传给首个命中的 parser。
