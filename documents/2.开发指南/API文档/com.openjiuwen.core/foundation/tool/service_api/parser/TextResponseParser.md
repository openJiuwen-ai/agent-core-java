# com.openjiuwen.core.foundation.tool.service_api.parser.TextResponseParser

## class TextResponseParser

```java
public class TextResponseParser extends BaseResponseParser
```

文本响应解析器，覆盖纯文本、HTML、XML、CSV 等内容类型。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean canParse(String contentType, int statusCode, Map<String, String> headers)` | 支持 `text/*`、多种 XML/HTML/CSV 类型，以及基于 `Accept` 头的空类型补偿判断。 |
| `public Object parse(byte[] responseData, String contentType)` | 空响应返回空字符串，其余响应按字符集解码为字符串。 |

## 相关测试

- `ResponseParserTest`
