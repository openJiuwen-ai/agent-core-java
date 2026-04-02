# com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser

## 类 WebPageParser

```java
public class WebPageParser extends Parser
```

`WebPageParser` 负责抓取普通网页并提取可读文本，不处理微信公众号文章链接。

## 构造方法

- `public WebPageParser()`：创建 30 秒连接超时的默认 `HttpClient`。
- `public WebPageParser(HttpClient httpClient)`：允许注入自定义客户端。

## 公开方法

- `parse(...)`：若传入的是微信文章 URL，会抛 `validation("Use WeChatArticleParser for WeChat URLs")`。
- `supports(String doc)`：只接受 HTTP/HTTPS，且排除微信文章 URL。

## 输出 metadata

- `source_url`
- `title`
- `source_type = "web_page"`

## 文本提取规则

- 优先提取 `<article>`，否则退回 `<body>`。
- 会剥离 `script`、`style` 和其他 HTML 标签，并压缩空白。
