# com.openjiuwen.core.runner.resourcemanager.ResourceRegistry

## 类 ResourceRegistry

```java
public class ResourceRegistry
```

`ResourceRegistry` 聚合不同资源类型的子管理器，并提供统一清理与按资源 ID 移除入口。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `toolMgr` | `ToolMgr` | `new ToolMgr()` | - |
| `workflowMgr` | `WorkflowMgr` | `new WorkflowMgr()` | - |
| `promptMgr` | `PromptMgr` | `new PromptMgr()` | - |
| `modelMgr` | `ModelMgr` | `new ModelMgr()` | - |
| `agentMgr` | `AgentMgr<Object>` | `new AgentMgr<>()` | - |
| `agentGroupMgr` | `AgentGroupMgr<Object>` | `new AgentGroupMgr<>()` | - |
| `sysOperationMgr` | `SysOperationMgr` | `new SysOperationMgr()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void clearAll()` | 清空所有子管理器中已注册的资源。 |
| `public void removeById(String resourceId)` | - |
| `public ToolMgr tool()` | - |
| `public PromptMgr prompt()` | - |
| `public ModelMgr model()` | - |
| `public WorkflowMgr workflow()` | - |
| `public AgentMgr<Object> agent()` | - |
| `public AgentGroupMgr<Object> agentGroup()` | - |
| `public SysOperationMgr sysOperation()` | - |
