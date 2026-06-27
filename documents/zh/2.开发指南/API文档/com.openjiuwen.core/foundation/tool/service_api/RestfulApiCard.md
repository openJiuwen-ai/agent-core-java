# com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard

## class RestfulApiCard

```java
public class RestfulApiCard extends ToolCard
```

REST 工具卡片，声明 URL、HTTP 方法、默认请求头、默认参数和执行约束。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `SUPPORTED_METHODS` | `Set<String>` | `Set.of("POST", "GET")` | 支持的方法集合。 |
| `url` | `String` | `-` | 请求 URL。 |
| `method` | `String` | `"POST"` | HTTP 方法。 |
| `headers` | `Map<String, Object>` | `Map.of()` | 默认请求头。 |
| `queries` | `Map<String, Object>` | `Map.of()` | 默认查询参数。 |
| `paths` | `Map<String, Object>` | `Map.of()` | 默认路径参数。 |
| `timeout` | `double` | `60.0` | 默认超时时间，单位秒。 |
| `maxResponseByteSize` | `int` | `10 * 1024 * 1024` | 最大响应体大小，默认 10MB。 |

## 使用说明

- `RestfulApi` 构造阶段会校验 `method` 是否属于 `SUPPORTED_METHODS`。
- 空 URL 在卡片构造阶段允许存在，但真正执行时仍可能失败。

## 相关测试

- `RestfulApiTest`
