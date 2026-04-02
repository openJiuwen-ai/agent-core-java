# com.openjiuwen.core.foundation.tool.ToolCard

## class ToolCard

```java
public class ToolCard extends BaseCard
```

工具元数据卡片。它在 `BaseCard` 基础上增加输入参数 Schema 与扩展属性，并可导出模型可消费的 `ToolInfo`。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `inputParams` | `Map<String, Object>` | `new HashMap<>()` | 输入参数 JSON Schema。 |
| `properties` | `Map<String, Object>` | `new HashMap<>()` | 额外自定义属性。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public ToolInfo toolInfo()` | 基于 `name`、`description` 与 `inputParams` 构造 `ToolInfo`。 |

## 使用说明

- `ToolCardTest` 说明 builder 默认会生成非空 `id`。
- 当未显式设置 `inputParams` 时，默认值为空 `Map`。

## 相关测试

- `ToolCardTest`
- `LocalFunctionTest`
- `McpToolTest`
