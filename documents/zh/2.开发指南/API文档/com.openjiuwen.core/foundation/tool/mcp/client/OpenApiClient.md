# com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient

## class OpenApiClient

```java
public class OpenApiClient implements McpClient
```

基于 OpenAPI 规范文件的客户端实现。它会读取 JSON/YAML 规范，把每个接口操作转换为 `McpToolCard`，并可按生成的参数结构直接发起 HTTP 请求。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `config` | `McpServerConfig` | `-` | 当前客户端配置。 |
| `httpClient` | `HttpClient` | `HttpClient.newHttpClient()` | 发起 HTTP 请求的客户端。 |
| `operations` | `Map<String, Operation>` | `new LinkedHashMap<>()` | 已加载的工具操作定义。 |
| `usedNames` | `Map<String, Integer>` | `new HashMap<>()` | 用于消除工具名冲突的计数器。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OpenApiClient(McpServerConfig config)` | 以指定配置创建客户端。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | 清空已有状态，读取 `serverPath` 中逗号分隔的多个规范文件并装载操作。 |
| `public boolean disconnect(float timeout)` | 清空已加载操作并返回 `true`。 |
| `public List<Object> listTools(float timeout)` | 返回当前已加载操作对应的 `McpToolCard` 列表。 |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | 按工具名解析操作，替换路径参数，并以 GET 查询串或 JSON 请求体调用远端接口。 |
| `public Optional<Object> getToolInfo(String toolName, float timeout)` | 返回单个工具卡片信息。 |
| `public String getServerPath()` | 返回配置中的 `serverPath`。 |
| `public static Map<String, Object> loadConf(String filePath) throws Exception` | 读取并解析 `.json`、`.yaml` 或 `.yml` 格式的 OpenAPI 文件。 |

## 使用说明

- `loadConf(...)` 要求路径存在、为普通文件且不能是符号链接。
- 当工具名冲突时，会在原名后追加 `_2`、`_3` 等后缀。
- 请求体中的路径参数会在真正发送前移除，避免同时出现在 URL 与 JSON body 中。
