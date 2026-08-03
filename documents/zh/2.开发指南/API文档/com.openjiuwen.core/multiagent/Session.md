# com.openjiuwen.core.session.Session

## class Session

```java
public class Session extends AgentGroupSessionApi
```

`Session` 是 `AgentGroupSessionApi` 的包级别别名，使调用者能够沿用 `com.openjiuwen.core.session.Session` 这一顶层导入入口。

## 构造方法

### `public Session(String sessionId, Map<String, Object> envs)`

创建带显式会话 ID 与环境变量的分组会话。

### `public Session(String sessionId)`

创建只指定会话 ID 的分组会话。

### `public Session()`

创建完全使用默认参数的分组会话。

## 静态工厂

### `public static Session create(String sessionId, Map<String, Object> envs)`

返回新的 `Session` 实例，是 `MultiAgentSessions` 使用的统一底层入口。

## 继承行为

- 继承 `AgentGroupSessionApi` 的 `updateState(...)`、`getState(...)`、`getEnv(...)`、流式输出和交互辅助能力。
- `MultiAgentFacadeTest` 验证了通过 `MultiAgentSessions.createAgentGroupSession(...)` 创建出的对象就是该类型，并且会保留 `sessionId` 与 `envs`。
