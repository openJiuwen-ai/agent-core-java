# com.openjiuwen.core.workflow.WorkflowComponent

## class WorkflowComponent

```java
public abstract class WorkflowComponent extends ComponentExecutable implements ComponentComposable
```

Standard implementation combining both execution and graph construction. This is the most common base class for user-defined workflow components.

## Methods

| Signature | Description |
| --- | --- |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | Add component. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
