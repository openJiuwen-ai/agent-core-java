# com.openjiuwen.core.foundation.tool.mcp.McpServerConfig

## class McpServerConfig

```java
public class McpServerConfig
```

MCP 服务端配置对象，描述服务端标识、访问路径、客户端类型以及认证附加参数。

## 字段

源码通过 Lombok 生成访问器与构建器；下表列出显式声明字段。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `serverId` | `String` | `UUID.randomUUID().toString().replace("-", "")` | 服务端唯一标识。 |
| `serverName` | `String` | `null` | 服务端显示名称。 |
| `serverPath` | `String` | `null` | 服务端路径或 URL。 |
| `clientType` | `String` | `"sse"` | 客户端类型。 |
| `params` | `Map<String, Object>` | `new HashMap<>()` | 附加参数，例如 stdio 命令、环境变量或 OpenAPI 额外配置。 |
| `authHeaders` | `Map<String, String>` | `new HashMap<>()` | 认证请求头。 |
| `authQueryParams` | `Map<String, String>` | `new HashMap<>()` | 认证查询参数。 |
| `NO_TIMEOUT` | `float` | `-1` | 无超时常量。 |

## 使用说明

- `McpToolTest` 说明默认 `clientType` 为 `sse`。
- 未显式提供 `serverId` 时会自动生成非空随机值。
- 默认 `authHeaders`、`authQueryParams` 与 `params` 均为空 `Map`。
- `clientType` 会在 MCP Server 注册路径上做归一化后用于 `ClientRegistry` 查询；原始配置对象中的字段值不会被改写。
- 支持的主要 `clientType` 包括 `sse`、`stdio`、`streamable-http`、`openapi`、`playwright`。
- 常见别名会被兼容：`SSE`、`mcp_SSE` 会归一化为 `sse`；`streamable_http`、`streamableHttp`、`STREAMABLE_HTTP`、`mcp_streamable_http`、`mcp-streamable-http` 会归一化为 `streamable-http`；`open-api` 会归一化为 `openapi`。

## 相关测试

- `McpToolTest`
- `McpClientsTest`
