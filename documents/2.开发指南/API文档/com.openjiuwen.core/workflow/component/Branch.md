# com.openjiuwen.core.workflow.component.Branch

## class Branch

```java
public class Branch
```

A single branch with condition and target nodes.

## Fields

| Signature | Description |
| --- | --- |
| `private final String branchId` | Branch id. |
| `private final Condition condition` | Condition. |
| `private final List<String> target` | Target. |

## Constructors

| Signature | Description |
| --- | --- |
| `public Branch(Object conditionObj, List<String> target, String branchId)` | Create a new `Branch` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public boolean evaluate(BaseSession session)` | Execute `evaluate`. |
| `public Object traceInfo(BaseSession session)` | Execute `traceInfo`. |
| `public String getBranchId()` | Return the branch id. |
| `public List<String> getTarget()` | Return the target. |
