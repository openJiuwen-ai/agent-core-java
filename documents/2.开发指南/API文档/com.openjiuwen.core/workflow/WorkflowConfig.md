# com.openjiuwen.core.workflow.WorkflowConfig

## class WorkflowConfig

```java
public class WorkflowConfig
```

Configuration for a workflow instance.

## Fields

| Signature | Description |
| --- | --- |
| `private WorkflowCard card` | Card. |
| `private WorkflowSpec spec` | Spec. |
| `private int workflowMaxNestingDepth = 5` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public WorkflowConfig()` | Create a new `WorkflowConfig` instance. |
| `public WorkflowConfig(WorkflowCard card)` | Create a new `WorkflowConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public WorkflowCard getCard()` | Return the card. |
| `public void setCard(WorkflowCard card)` | Update the card. |
| `public WorkflowSpec getSpec()` | Return the spec. |
| `public void setSpec(WorkflowSpec spec)` | Update the spec. |
| `public int getWorkflowMaxNestingDepth()` | Return the workflow max nesting depth. |
| `public void setWorkflowMaxNestingDepth(int workflowMaxNestingDepth)` | Update the workflow max nesting depth. |
