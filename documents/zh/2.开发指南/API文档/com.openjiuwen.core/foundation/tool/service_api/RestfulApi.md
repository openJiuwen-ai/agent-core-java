# com.openjiuwen.core.foundation.tool.service_api.RestfulApi

## class RestfulApi

```java
public class RestfulApi extends Tool
```

REST 工具实现。它基于 JDK `HttpClient` 构造请求、应用代理与 SSL 配置、解析响应，并把结果标准化为统一结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` | `String` | `-` | 请求 URL 模板。 |
| `method` | `String` | `-` | 大写 HTTP 方法。 |
| `timeout` | `double` | `-` | 默认超时时间，单位秒。 |
| `maxResponseByteSize` | `int` | `-` | 最大响应字节数限制。 |
| `apiParamMapper` | `ApiParamMapper` | `-` | 参数位置映射器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RestfulApi(RestfulApiCard card)` | 校验卡片配置后创建 REST 工具实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 校验输入、按位置分流参数、执行 HTTP 请求并返回标准化响应。 |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 始终抛出 `TOOL_STREAM_NOT_SUPPORTED`。 |

## 使用说明

- `kwargs.timeout`、`kwargs.max_response_byte_size`、`kwargs.raise_for_status` 会覆盖默认行为。
- 非 GET 请求总是使用 JSON 请求体，并自动补充 `Content-Type: application/json`。
- 响应先经过 `ParserRegistry`，再封装为包含状态码、解析后数据、最终 URL、响应头、reason、message 的结果 `Map`。
- 当响应字节数超过限制、HTTP 状态异常或响应解析失败时，会转换成对应 `StatusCode` 错误。 |

## 相关测试

- `RestfulApiTest`
- `ResponseParserTest`
