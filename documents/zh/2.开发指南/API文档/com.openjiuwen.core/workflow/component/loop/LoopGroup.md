# com.openjiuwen.core.workflow.component.loop.LoopGroup

## 类 LoopGroup

```java
public class LoopGroup extends BaseWorkflow
```

循环体容器，负责维护循环内部节点、边和 break 组件集合。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LoopGroup()` | 创建 `LoopGroup` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public BaseWorkflow addWorkflowComp( String compId, ComponentComposable workflowComp, Boolean waitForAll, Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema, List<ComponentAbility> compAbility)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Boolean waitForAll)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Boolean waitForAll)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema, Object outputsSchema, Boolean waitForAll, List<ComponentAbility> compAbility)` | 向循环体中追加工作流节点。 |
| `public LoopGroup startNodes(List<String> nodes)` | 设置循环图的起始节点集合。 |
| `public LoopGroup start_nodes(List<String> nodes)` | 设置循环图的起始节点集合。 |
| `public BaseWorkflow startComp(String startCompId)` | 执行 `startComp`。 |
| `public LoopGroup endNodes(Object nodes)` | 设置循环图的结束节点集合。 |
| `public LoopGroup end_nodes(Object nodes)` | 设置循环图的结束节点集合。 |
| `public BaseWorkflow endComp(String endCompId)` | 执行 `endComp`。 |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 执行当前节点的运行逻辑。 |
| `public boolean skipTrace()` | 返回执行时是否跳过 trace 记录。 |
| `public boolean graphInvoker()` | 返回该类型是否通过图执行器调度。 |
| `public List<LoopBreakComponent> getBreakComponents()` | 返回`breakComponents` 字段。 |
| `public List<String> getStartNodesList()` | 返回`startNodesList` 字段。 |
| `public List<String> getEndNodesList()` | 返回`endNodesList` 字段。 |
| `public void checkValidate()` | 校验循环体配置是否合法。 |
