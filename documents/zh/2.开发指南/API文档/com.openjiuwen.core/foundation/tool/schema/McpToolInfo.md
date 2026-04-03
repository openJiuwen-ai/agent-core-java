# com.openjiuwen.core.foundation.tool.schema.McpToolInfo

## class McpToolInfo

```java
public class McpToolInfo extends ToolInfo
```

MCP 工具描述对象，在 `ToolInfo` 的基础上追加服务端名称字段。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `serverName` | `String` | `null` | MCP 服务端名称，对应序列化键 `server_name`。 |

## 使用说明

- `McpToolCard.toolInfo()` 会构造该类型，而不是基础 `ToolInfo`。
