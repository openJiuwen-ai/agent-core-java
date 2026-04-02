# com.openjiuwen.core.foundation.tool.service_api.ApiParamLocation

## enum ApiParamLocation

```java
public enum ApiParamLocation
```

请求参数位置枚举，用于标记参数属于查询串、路径、请求体或请求头。

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `QUERY` | URL 查询参数。 |
| `PATH` | 路径占位参数。 |
| `BODY` | 请求体参数。 |
| `HEADER` | HTTP 请求头参数。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回对应的小写字符串值。 |
| `public static ApiParamLocation fromString(String text)` | 忽略大小写解析字符串；未知值回退到 `BODY`。 |

## 相关测试

- `ApiParamMapperTest`
