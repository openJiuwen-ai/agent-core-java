# com.openjiuwen.core.session.internal.WorkflowSessions

## 类 WorkflowSessions

```java
public final class WorkflowSessions
```

`WorkflowSessions` 是 workflow 包级 session 创建门面，统一代理到 `WorkflowSessionApi.create(...)`。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static WorkflowSessionApi createWorkflowSession()` | 使用默认参数创建工作流 session。 |
| `public static WorkflowSessionApi createWorkflowSession(String sessionId)` | 使用指定 session id 创建工作流 session。 |
| `public static WorkflowSessionApi createWorkflowSession(BaseSession parent)` | 基于父 session 创建工作流 session。 |
| `public static WorkflowSessionApi createWorkflowSession(BaseSession parent, String sessionId, Map<String, Object> envs)` | 使用完整参数创建工作流 session。 |
