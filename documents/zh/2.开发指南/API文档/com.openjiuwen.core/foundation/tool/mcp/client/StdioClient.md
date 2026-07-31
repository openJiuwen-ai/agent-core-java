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
| `public boolean connect(int retryTimes, float timeout) throws Exception` | 校验命令白名单后启动子进程，并尝试发送 `initialize` 请求。 |
| `public boolean disconnect(float timeout) throws Exception` | 关闭输入输出流并终止子进程。 |
| `public List<Object> listTools(float timeout) throws Exception` | 请求 `tools/list` 并把结果转成 `McpToolCard` 列表。 |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | 请求 `tools/call`。 |
| `public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception` | 通过 `listTools(...)` 过滤目标工具。 |
| `public String getServerPath()` | 返回配置中的 `serverPath`。 |

## 使用说明

- `serverPath` 声明受信的 MCP 服务可执行文件，相当于单项命令白名单。
- `params.command` 表示本次实际执行的命令；未提供时直接使用 `serverPath`，提供时其解析后的真实可执行文件必须与 `serverPath` 完全一致。例如，`serverPath` 为 `/usr/bin/python3`、`params.command` 为 `python3` 时，二者解析到同一真实文件才允许启动。
- 兼容旧配置而省略 `serverPath` 时，`params.command` 只允许 `java`、`python`、`python3` 启动器，既可填写 `PATH` 中的命令名，也可填写绝对路径。绝对路径会解析为真实路径并校验为普通可执行文件；校验通过后仍使用配置的启动器路径，以保留 Python 虚拟环境等启动语义。
- 默认白名单选择 Java 和 Python 启动器，是因为仓库内现有的 stdio MCP 场景分别使用 Java 进程和 Python 脚本。该名单仅用于兼容未配置 `serverPath` 的旧配置，不代表完整的业务支持范围。
- 新增其他启动器时，应优先通过 `serverPath` 显式指定单项白名单；是否扩充默认名单，需要结合实际 MCP 服务需求完成需求与安全评审，并补充兼容性和安全测试。
- 命令必须解析为真实的普通可执行文件，不能把任意字符串直接作为进程命令。
- `params.args` 可追加命令行参数，`params.env` 可注入环境变量，`params.cwd` 可指定工作目录；这些进程配置应仅来自受信的应用或部署配置，不能直接使用用户或模型输入。
- 读取响应时会循环跳过不匹配当前请求 `id` 的帧。
