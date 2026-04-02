# com.openjiuwen.core.workflow.WorkflowOutput

## class WorkflowOutput

```java
public class WorkflowOutput
```

Final output container for workflow execution. Contains both the result data and the execution state.

## Fields

| Signature | Description |
| --- | --- |
| `private Object result` | Result. |
| `private WorkflowExecutionState state` | State. |

## Constructors

| Signature | Description |
| --- | --- |
| `public WorkflowOutput()` | Create a new `WorkflowOutput` instance. |
| `public WorkflowOutput(Object result, WorkflowExecutionState state)` | Create a new `WorkflowOutput` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object getResult()` | Return the result. |
| `public void setResult(Object result)` | Update the result. |
| `public WorkflowExecutionState getState()` | Return the state. |
| `public void setState(WorkflowExecutionState state)` | Update the state. |
| `public String toString()` | Execute `toString`. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
