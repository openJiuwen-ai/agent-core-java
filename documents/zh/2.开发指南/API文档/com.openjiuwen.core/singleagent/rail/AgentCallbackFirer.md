# com.openjiuwen.core.single_agent.rail.AgentCallbackFirer

## 接口 AgentCallbackFirer

```java
public interface AgentCallbackFirer
```

供可触发生命周期事件的对象实现的分发接口。

## 说明

- 相关测试：`AgentCallbackContextTest`、`RailExecutorTest`。
- 该接口用于解耦 `AgentCallbackContext` 与具体 agent 实现。
