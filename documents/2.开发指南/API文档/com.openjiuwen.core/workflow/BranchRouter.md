# com.openjiuwen.core.workflow.BranchRouter

## class BranchRouter

```java
public class BranchRouter implements Router
```

Router that evaluates branch conditions and returns target node paths.

## Fields

| Signature | Description |
| --- | --- |
| `private BaseSession session` | Session. |
| `private final boolean reportTrace` | Report trace. |
| `private DrawableBranchRouter drawableBranchRouter` | Drawable branch router. |

## Constructors

| Signature | Description |
| --- | --- |
| `public BranchRouter(boolean reportTrace)` | Create a new `BranchRouter` instance. |
| `public BranchRouter()` | Create a new `BranchRouter` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void addBranch(Object condition, Object target, String branchId)` | Add a branch with condition and target(s). |
| `public DrawableBranchRouter getDrawableBranchRouter()` | Return the drawable branch router. |
| `public void setSession(Object session)` | Set the session for condition evaluation. |
| `private static BaseSession extractInnerSession(Object sessionApi)` | Execute `extractInnerSession`. |
| `public Object apply(Object input)` | Execute `apply`. |

## Notes

- Representative workflow regression coverage appears in `WorkflowTest.java`.
