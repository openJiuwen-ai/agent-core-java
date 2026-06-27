# service_api

`com.openjiuwen.core.foundation.tool.service_api` 提供基于 HTTP 的工具实现，包括参数位置映射、REST 工具卡片和具体的 `RestfulApi` 运行时。

## 子包

| 子包 | 说明 |
| --- | --- |
| [`parser`](tool/service_api/parser.README.md) | 提供响应解压缩器、响应解析器与注册中心。 |

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ApiParamLocation`](service_api/ApiParamLocation.md) | 声明参数在请求中的位置。 |
| [`ApiParamMapper`](service_api/ApiParamMapper.md) | 按 Schema 把输入拆分到 path、query、body、header。 |
| [`RestfulApi`](service_api/RestfulApi.md) | 基于 JDK `HttpClient` 的 REST 工具实现。 |
| [`RestfulApiCard`](service_api/RestfulApiCard.md) | REST 工具卡片，声明 URL、方法、超时和默认参数。 |

## 关键行为

- `ApiParamMapper` 只会为 `PATH`、`QUERY`、`HEADER` 三类位置合并默认值，且输入值优先于默认值。
- `RestfulApi` 对 GET 请求会把 body 参数追加到查询串；非 GET 请求则统一序列化为 JSON 请求体。
- `RestfulApi` 返回值统一包含 `code`、`data`、`url`、`headers`、`reason`、`message` 六类核心字段。

## 相关测试

- `ApiParamMapperTest`
- `RestfulApiTest`
- `ResponseParserTest`
