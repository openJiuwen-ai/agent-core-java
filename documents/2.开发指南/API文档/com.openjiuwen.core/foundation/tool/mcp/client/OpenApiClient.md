# com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient

## class OpenApiClient

```java
public class OpenApiClient implements McpClient
```

OpenAPI-file backed MCP-style client. Parses OpenAPI spec files (JSON/YAML) and converts each route into an MCP tool card with proper parameter schemas, descriptions, and output schemas.

## Notes

- `connect(...)` accepts a comma-separated `serverPath` and loads each OpenAPI spec into an in-memory operation registry.
- `loadConf(...)` rejects missing files, directories, and symbolic links before parsing JSON or YAML.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `config` | `McpServerConfig` | `-` | - |

## Constructors

| Signature | Description |
| --- | --- |
| `public OpenApiClient(McpServerConfig config)` | - |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean connect(int retryTimes, float timeout) throws Exception` | - |
| `public boolean disconnect(float timeout)` | - |
| `public List<Object> listTools(float timeout)` | - |
| `public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception` | - |
| `public Optional<Object> getToolInfo(String toolName, float timeout)` | - |
| `public String getServerPath()` | - |
| `public static Map<String, Object> loadConf(String filePath) throws Exception` | Load and parse an OpenAPI spec from a file path. |
