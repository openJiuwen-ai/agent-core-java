# com.openjiuwen.core.runner.resourcemanager.WorkflowMgr

## 类 WorkflowMgr

```java
public class WorkflowMgr extends AbstractManager<Workflow>
```

`WorkflowMgr` 负责 `Workflow` 资源 provider 的注册、批量导入、获取与移除。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addWorkflow(String workflowId, Supplier<Workflow> workflow)` | - |
| `public void addWorkflows(List<WorkflowEntry> workflows)` | - |
| `public Workflow getWorkflow(String workflowId)` | - |
| `public Supplier<? extends Workflow> removeWorkflow(String workflowId)` | - |

## 嵌套类型

- `WorkflowEntry`: -
