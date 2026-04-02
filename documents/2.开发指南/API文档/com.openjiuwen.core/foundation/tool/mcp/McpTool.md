# com.openjiuwen.core.foundation.tool.mcp.McpTool

## class McpTool

```java
public class McpTool extends Tool
```

MCP 工具包装器。它把远端 MCP 服务端暴露的工具封装为本地 `Tool`，供上层统一调用。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mcpClient` | `McpClient` | `-` | 负责实际远端调用的客户端实现。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public McpTool(McpClient mcpClient, McpToolCard card)` | 创建 MCP 工具；当 `mcpClient` 为空时抛出错误。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 可选地按 `inputParams` 格式化输入，调用远端工具，并返回 `Map.of("result", result)`。 |
| `public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception` | 始终抛出 `TOOL_STREAM_NOT_SUPPORTED`。 |

## 使用说明

- `inputs == null` 时会回退为空 `Map`。
- 远端异常会被转换为 `TOOL_MCP_EXECUTION_ERROR`。

## 相关测试

- `McpToolTest`
