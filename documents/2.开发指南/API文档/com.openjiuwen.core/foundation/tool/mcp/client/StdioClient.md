# com.openjiuwen.core.foundation.tool.mcp.client.StdioClient

## class StdioClient

```java
public class StdioClient implements McpClient
```

基于 stdio 传输的 MCP 客户端。它通过 `Content-Length` 分帧的 JSON-RPC 协议与本地子进程通信。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `config` | `McpServerConfig` | `-` | 客户端配置。 |
| `requestCounter` | `AtomicLong` | `new AtomicLong()` | JSON-RPC 请求编号。 |
| `process` | `Process` | `null` | 启动后的子进程。 |
| `stdout` | `BufferedInputStream` | `null` | 读取子进程标准输出。 |
| `stdin` | `BufferedOutputStream` | `null` | 写入子进程标准输入。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StdioClient(McpServerConfig config)` | 以指定配置创建客户端。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | 按 `params.command` 或 `serverPath` 启动子进程，并尝试发送 `initialize` 请求。 |
| `public boolean disconnect(float timeout) throws Exception` | 关闭输入输出流并终止子进程。 |
| `public List<Object> listTools(float timeout) throws Exception` | 请求 `tools/list` 并把结果转成 `McpToolCard` 列表。 |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | 请求 `tools/call`。 |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | 通过 `listTools(...)` 过滤目标工具。 |
| `public String getServerPath()` | 返回配置中的 `serverPath`。 |

## 使用说明

- `params.args` 可追加命令行参数，`params.env` 可注入环境变量，`params.cwd` 可指定工作目录。
- 读取响应时会循环跳过不匹配当前请求 `id` 的帧。
