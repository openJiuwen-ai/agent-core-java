# com.openjiuwen.core.foundation.tool.service_api.parser.JsonResponseParser

## class JsonResponseParser

```java
public class JsonResponseParser extends BaseResponseParser
```

JSON 响应解析器，使用 Jackson `ObjectMapper` 把响应体解析为 `Map`。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean canParse(String contentType, int statusCode, Map<String, String> headers)` | 支持 `application/json`、`text/json`、`text/x-json`、`application/javascript`，以及带 `json` 的兼容内容类型。 |
| `public Object parse(byte[] responseData, String contentType)` | 空响应返回空 `Map`；非空响应解析失败时抛出 `IllegalArgumentException`。 |

## 相关测试

- `ResponseParserTest`
