# com.openjiuwen.core.singleagent.legacy.TaskSession

## 类 TaskSession

```java
public class TaskSession implements Session
```

面向旧调用代码的 `Session` 包装器，内部委托 `AgentSessionApi`。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public TaskSession(AgentSessionApi inner)` | 包装现有 `AgentSessionApi`，并立即输出弃用告警。 |
| `public TaskSession(String sessionId)` | 以指定 `sessionId` 创建新的 `AgentSessionApi` 后再包装。 |
| `public TaskSession()` | 创建默认 `AgentSessionApi` 后再包装。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public AgentSessionApi getInnerSession()` | 返回内部持有的 `AgentSessionApi`。 |
| `public void postRun()` | 调用内部会话的 `postRun()` 执行收尾逻辑。 |
| `@Override public String getSessionId()` | 透传内部会话的 `sessionId`。 |
| `@Override public Object getState(String key)` | 透传内部会话的状态读取。 |
| `@Override public void updateState(Map<String, Object> state)` | 透传内部会话的状态更新。 |

## 说明

- 类已标记 `@Deprecated(since = "0.1.7", forRemoval = true)`；新代码应直接使用 `AgentSessionApi`。
