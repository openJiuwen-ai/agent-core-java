# schema

`com.openjiuwen.core.foundation.tool.schema` 提供供模型函数调用消费的工具描述对象，以及带服务端名称的 MCP 扩展描述对象。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`McpToolInfo`](schema/McpToolInfo.md) | 在 `ToolInfo` 基础上补充 `serverName`。 |
| [`ToolInfo`](schema/ToolInfo.md) | 通用工具描述对象，包含类型、名称、描述和参数 Schema。 |

## 说明

- `ToolInfo.type` 默认值为 `function`。
- `ToolCard.toolInfo()` 与 `McpToolCard.toolInfo()` 都会构造本包中的 DTO 供上层使用。
