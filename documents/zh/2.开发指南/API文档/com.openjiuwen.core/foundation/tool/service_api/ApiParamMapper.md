# com.openjiuwen.core.foundation.tool.service_api.ApiParamMapper

## class ApiParamMapper

```java
public class ApiParamMapper
```

请求参数映射器。它根据输入 Schema 中的 `location` 标记，把输入值分发到 `PATH`、`QUERY`、`BODY`、`HEADER` 等位置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `schema` | `Map<String, Object>` | `-` | 参数 Schema。 |
| `defaults` | `Map<ApiParamLocation, Map<String, Object>>` | `-` | 各位置默认值。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ApiParamMapper(Map<String, Object> schema, Map<String, Object> defaultQueries, Map<String, Object> defaultHeaders, Map<String, Object> defaultPaths)` | 以 Schema 和默认 query/header/path 参数构建映射器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Map<ApiParamLocation, Map<String, Object>> map(Map<String, Object> inputs, ApiParamLocation defaultLocation)` | 输出按位置分组后的参数表，并返回不可变 `Map`。 |

## 使用说明

- 当 `schema == null` 时，全部输入都放入 `defaultLocation` 对应分组。
- 只有在 `schema.properties` 中声明过的输入键才会被显式分流。
- `PATH`、`QUERY`、`HEADER` 三类位置会在分流后与默认值合并，并且输入值覆盖默认值。

## 相关测试

- `ApiParamMapperTest`
