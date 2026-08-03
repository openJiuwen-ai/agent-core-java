# com.openjiuwen.core.session.AgentSession

## 类 AgentSession

```java
public class AgentSession
```

旧版会话工厂，用于创建并预热 `AgentSessionApi`。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public AgentSession()` | 通过 `CheckpointerFactory.getCheckpointer()` 获取检查点实现，供 `release` 释放会话资源。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public AgentSessionApi preRun(String sessionId, Map<String, Object> inputs)` | 创建 `AgentSessionApi`，调用 `preRun(inputs)` 完成初始化；当 `sessionId` 为空时回落到 `default_session`。 |
| `public void release(String sessionId)` | 如果存在 `Checkpointer`，释放指定会话的持久化资源。 |

## 说明

- 该类型保留旧调用习惯；新代码应直接使用 `AgentSessionApi`。
