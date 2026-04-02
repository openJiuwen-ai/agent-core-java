# com.openjiuwen.core.workflow.component.BranchComponent

## class BranchComponent

```java
public class BranchComponent extends WorkflowComponent
```

Conditional routing component that evaluates branches and routes execution.

## Fields

| Signature | Description |
| --- | --- |
| `private final BranchRouter router` | Router. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BranchComponent()` | Create a new `BranchComponent` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addBranch(Object condition, Object target, String branchId)` | Add a branch with condition and target(s). |
| `public void addBranch(Object condition, Object target)` | Add branch. |
| `public void add_branch(Object condition, Object target, String branchId)` | Compatibility alias for translated tests that still use snake_case naming. |
| `public void add_branch(Object condition, Object target)` | Compatibility alias for translated tests that still use snake_case naming. |
| `public BranchRouter router()` | Gets the router associated with this branch component. |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | Invoke the component or workflow. |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | Add component. |
| `public boolean skipTrace()` | Execute `skipTrace`. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
