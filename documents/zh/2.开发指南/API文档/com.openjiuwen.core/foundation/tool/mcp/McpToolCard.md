# com.openjiuwen.core.foundation.tool.mcp.McpToolCard

## class McpToolCard

```java
public class McpToolCard extends ToolCard
```

MCP 工具卡片。在 `ToolCard` 基础上增加服务端名称与服务端标识。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `serverName` | `String` | `null` | 工具所属服务端名称。 |
| `serverId` | `String` | `""` | 工具所属服务端标识。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public McpToolInfo toolInfo()` | 返回包含 `serverName` 的 `McpToolInfo`。 |

## 使用说明

- `McpToolTest` 说明 builder 默认会为继承自 `BaseCard` 的 `id` 生成非空值。
- `serverId` 的显式默认值为空字符串，而不是随机值。

## 相关测试

- `McpToolTest`
