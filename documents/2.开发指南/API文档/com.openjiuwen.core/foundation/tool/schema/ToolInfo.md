# com.openjiuwen.core.foundation.tool.schema.ToolInfo

## class ToolInfo

```java
public class ToolInfo
```

工具描述对象，供模型函数调用或上层工具注册流程使用。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `String` | `"function"` | 工具类型。 |
| `name` | `String` | `""` | 工具名称。 |
| `description` | `String` | `""` | 工具描述。 |
| `parameters` | `Map<String, Object>` | `Map.of()` | 参数 JSON Schema。 |

## 使用说明

- `ToolCardTest` 说明无参 builder 默认会产生 `type=function`、空名称、空描述和空参数表。

## 相关测试

- `ToolCardTest`
- `LocalFunctionTest`
