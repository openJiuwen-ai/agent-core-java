# com.openjiuwen.core.foundation.tool.service_api.BaseResponseParser

## class BaseResponseParser

```java
public abstract class BaseResponseParser
```

响应解析器抽象基类。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public abstract boolean canParse(String contentType, int statusCode, Map<String, String> headers)` | 判断当前解析器是否能处理该响应。 |
| `public abstract Object parse(byte[] responseData, String contentType)` | 解析响应体并返回结果对象。 |

## 使用说明

- 受保护辅助方法 `decodeBytes(...)` 会根据 `Content-Type` 中的 `charset` 选择字符集；缺省时使用 UTF-8。 |
