# com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl

## class SubWorkflowComponentImpl

```java
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent
```

Component that wraps a sub-workflow and delegates execution to it.

## Fields

| Signature | Description |
| --- | --- |
| `private static final String SUB_WORKFLOW_COMPONENT =` | . |
| `private final Workflow subWorkflow` | Sub workflow. |

## Constructors

| Signature | Description |
| --- | --- |
| `public SubWorkflowComponentImpl(Workflow subWorkflow)` | Create a new `SubWorkflowComponentImpl` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
| `public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context)` | Stream the component or workflow output. |
| `public boolean graphInvoker()` | Execute `graphInvoker`. |
| `public String componentType()` | Execute `componentType`. |
| `public Workflow getSubWorkflow()` | Return the sub workflow. |
| `public HasDrawable getSubWorkflowInternal()` | Return the sub workflow internal. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
