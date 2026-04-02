# com.openjiuwen.core.workflow.WorkflowSessions

## class WorkflowSessions

```java
public final class WorkflowSessions
```

Convenience facade for creating workflow sessions from the workflow package. from `openjiuwen.core.workflow`.

## Constructors

| Signature | Description |
| --- | --- |
| `private WorkflowSessions()` | Create a new `WorkflowSessions` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static WorkflowSessionApi createWorkflowSession(BaseSession parent, String sessionId, Map<String, Object> envs)` | Create a new workflow session. |
| `public static WorkflowSessionApi createWorkflowSession()` | Create a new workflow session with defaults. |
| `public static WorkflowSessionApi createWorkflowSession(String sessionId)` | Create a new workflow session with a specific session ID. |
| `public static WorkflowSessionApi createWorkflowSession(BaseSession parent)` | Create a new workflow session with a parent session. |
