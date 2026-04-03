# com.openjiuwen.core.workflow.WorkflowComponent

## 抽象类 WorkflowComponent

```java
public abstract class WorkflowComponent extends ComponentExecutable implements ComponentComposable
```

`WorkflowComponent` 是最常见的工作流组件基类，同时具备执行能力和构图能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | 直接把当前组件实例注册到工作流图中。 |

## 说明

- `Start`、`End`、`BranchComponent`、`SubWorkflowComponentImpl` 等类型都以它为基础。
