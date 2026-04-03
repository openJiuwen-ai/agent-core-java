# com.openjiuwen.core.runner.resourcemanager.ResourceMgr

## 类 ResourceMgr

```java
public class ResourceMgr
```

`ResourceMgr` 是统一资源门面，负责 Agent、Workflow、Tool、Prompt、Model、MCP Server 与 SysOperation 的注册、查询、移除和标签管理。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `resourceRegistry` | `ResourceRegistry` | `new ResourceRegistry()` | - |
| `tagMgr` | `TagMgr` | `new TagMgr()` | - |
| `idToCard` | `Map<String, BaseCard>` | `new HashMap<>()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Result<GroupCard> addAgentGroup(GroupCard card, Supplier<Object> agentGroup, Object tag)` | - |
| `public List<Result<GroupCard>> removeAgentGroup(Object groupId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getAgentGroup(String groupId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Result<AgentCard> addAgent(AgentCard card, Supplier<Object> agent, Object tag)` | - |
| `public List<Result<AgentCard>> addAgents(List<AgentEntry> agents, Object tag)` | - |
| `public Object removeAgent(Object agentId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getAgent(String agentId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getAgent(String agentId)` | - |
| `public Result<WorkflowCard> addWorkflow(WorkflowCard card, Supplier<Workflow> workflow, Object tag)` | - |
| `public List<Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows, Object tag)` | - |
| `public Object removeWorkflow(Object workflowId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getWorkflow(String workflowId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getWorkflow(String workflowId)` | - |
| `public Result<ToolCard> addTool(Tool tool, Object tag)` | - |
| `public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag)` | - |
| `public Object getTool(String toolId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getTool(String toolId)` | - |
| `public Object removeTool(Object toolId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Result<String> addModel(String modelId, Supplier<Model> model, Object tag)` | - |
| `public List<Result<String>> addModels(List<ModelEntry> models, Object tag)` | - |
| `public Object removeModel(Object modelId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getModel(String modelId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getModel(String modelId)` | - |
| `public Result<String> addPrompt(String promptId, PromptTemplate template, Object tag)` | - |
| `public List<Result<String>> addPrompts(List<PromptEntry> prompts, Object tag)` | - |
| `public Object removePrompt(Object promptId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getPrompt(String promptId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getPrompt(String promptId)` | - |
| `public Result<SysOperationCard> addSysOperation(SysOperationCard card, Object tag)` | - |
| `public Object removeSysOperation(Object sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists)` | - |
| `public Object getSysOperation(String sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public Object getSysOpToolCards(String sysOperationId, Object operationName, Object toolName)` | - |
| `public List<ToolInfo> getToolInfos(Object toolId, Object toolType, Object tag, TagMatchStrategy tagMatchStrategy)` | - |
| `public List<Result<String>> addMcpServer(Object serverConfig, Object tag, Double expiryTime) throws Exception` | - |
| `public List<Result<String>> removeMcpServer(Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists) throws Exception` | - |
| `public Object getMcpTool(Object name, Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists) throws Exception` | - |
| `public List<Result<String>> refreshMcpServer(Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean ignoreException, boolean skipIfTagNotExists)` | 按 `serverId`、`serverName` 和标签条件刷新 MCP Server 关联的工具卡片。 |
| `public List<ToolInfo> getMcpToolInfos(Object name, Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists, boolean ignoreException) throws Exception` | 按名称、Server 标识和标签条件查询 MCP 工具元数据列表。 |
| `public List<BaseCard> getResourceByTag(String tag)` | - |
| `public List<String> listTags()` | - |
| `public boolean hasTag(String tag)` | - |
| `public List<Result<String>> removeTag(Object tag, boolean skipIfTagNotExists)` | - |
| `public Result<List<String>> updateResourceTag(String resourceId, Object tag)` | - |
| `public Result<List<String>> addResourceTag(String resourceId, Object tag)` | - |
| `public Result<List<String>> removeResourceTag(String resourceId, Object tag, boolean skipIfTagNotExists)` | - |
| `public List<String> getResourceTag(String resourceId)` | - |
| `public boolean resourceHasTag(String resourceId, String tag)` | - |
| `public void release()` | - |

## 嵌套类型

- `AgentEntry`: -
- `WorkflowEntry`: -
- `ModelEntry`: -
- `PromptEntry`: -

## 相关测试

- `ResourceMgrTest`
- `RunnerTest`
