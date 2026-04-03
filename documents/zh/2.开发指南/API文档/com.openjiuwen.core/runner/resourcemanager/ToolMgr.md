# com.openjiuwen.core.runner.resourcemanager.ToolMgr

## 类 ToolMgr

```java
public class ToolMgr
```

`ToolMgr` 负责普通 `Tool`、MCP Server 工具以及 `SysOperation` 关联工具的注册与释放。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `tools` | `ConcurrentHashMap<String, Tool>` | `new ConcurrentHashMap<>()` | - |
| `mcpServerNameToIds` | `Map<String, List<String>>` | `new HashMap<>()` | - |
| `mcpServerResources` | `Map<String, McpServerResource>` | `new HashMap<>()` | - |
| `sysOpResources` | `Map<String, SysOpToolResource>` | `new HashMap<>()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addTool(String toolId, Tool tool)` | - |
| `public Tool getTool(String toolId)` | - |
| `public Tool getMcpTool(String toolName, String serverId)` | - |
| `public List<Tool> getMcpTools(String serverId)` | - |
| `public Object getMcpToolId(String serverId, String toolName)` | - |
| `public Tool removeTool(String toolId)` | - |
| `public static String generateMcpToolId(String serverId, String serverName, String toolName)` | - |
| `public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception` | - |
| `public List<String> getMcpServerIds(String serverName)` | - |
| `public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception` | - |
| `public List<String> removeToolServer(String serverId) throws Exception` | - |
| `public void addSysOperationTools(String sysOpId, List<String> toolIds)` | - |
| `public List<String> removeSysOperationTools(String sysOpId)` | - |
| `public List<String> getSysOperationToolIds(String sysOpId)` | - |
| `public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception` | - |
| `public void release()` | - |

## 嵌套类型

- `McpServerResource`: -
- `SysOpToolResource`: -
