# com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser

## 类 WeChatArticleParser

```java
public class WeChatArticleParser extends WebPageParser
```

`WeChatArticleParser` 是微信公众号文章专用解析器，只接受 `https://mp.weixin.qq.com/s/...` 形式的 URL。

## 公开静态方法

- `isWechatArticleUrl(String url)`：判断链接是否为公众号文章地址。

## 公开方法

- `parse(...)`：非微信文章 URL 会抛 `validation("Not a WeChat article URL")`。
- `supports(String doc)`：委托 `isWechatArticleUrl(...)`。

## 输出 metadata

- `source_url`
- `title`
- `source_type = "wechat_article"`

## 提取规则

- 复用父类抓取 HTML。
- 标题优先取 `og:title`，其次退回 `<title>`。
- 正文优先提取 `id="js_content"` 容器。
