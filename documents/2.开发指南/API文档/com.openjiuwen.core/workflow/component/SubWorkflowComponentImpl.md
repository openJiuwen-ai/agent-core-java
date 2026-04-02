# com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl

## 类 SubWorkflowComponentImpl

```java
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent
```

`SubWorkflowComponentImpl` 把一个 `Workflow` 作为节点嵌入父工作流中。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public SubWorkflowComponentImpl(Workflow subWorkflow)` | 使用指定子工作流创建组件；参数不能为空。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 以子工作流模式执行内部 `Workflow`。 |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | 流式执行内部 `Workflow`。 |
| `public boolean graphInvoker()` | 返回 `true`，表示该组件会触发图执行。 |
| `public Workflow getSubWorkflow()` | 返回内部子工作流。 |
| `public HasDrawable getSubWorkflowInternal()` | 返回内部子工作流的可视化对象。 |

## 说明

- `WorkflowTest` 的子工作流场景覆盖了该类型的核心行为。
