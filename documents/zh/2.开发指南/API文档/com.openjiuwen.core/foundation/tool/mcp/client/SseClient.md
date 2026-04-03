# com.openjiuwen.core.foundation.tool.mcp.client.SseClient

## class SseClient

```java
public class SseClient extends AbstractHttpMcpClient
```

面向 SSE 风格 MCP 服务端的客户端实现。当前 Java 基线版本通过 `AbstractHttpMcpClient` 里的 HTTP JSON-RPC 逻辑完成实际调用。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SseClient(McpServerConfig config)` | 以指定配置创建客户端。 |

## 使用说明

- 本类没有新增公开方法，公共连接与调用能力全部继承自 `McpClient` 接口契约与抽象基类实现。
