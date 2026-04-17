# com.openjiuwen.core.multiagent.MultiAgentSessions

## final class MultiAgentSessions

```java
public final class MultiAgentSessions
```

`MultiAgentSessions` 是 `multiagent` 包内的静态工厂门面，用来在不跳出当前包的情况下创建 `Session`。

## 静态工厂

| 方法 | 返回 | 说明 |
|---|---|---|
| `createAgentGroupSession(String sessionId, Map<String, Object> envs)` | `Session` | 用显式会话 ID 与环境变量创建 `Session`。 |
| `createAgentGroupSession()` | `Session` | 使用 `null` ID / `null` env 创建默认会话。 |
| `createAgentGroupSession(String sessionId)` | `Session` | 只指定会话 ID，环境变量留空。 |
| `createAgentGroupSession(Map<String, Object> envs)` | `Session` | 只指定环境变量，会话 ID 交给底层自动生成。 |

## 说明

- 类构造器是私有的，调用方式完全围绕静态工厂展开。
- 所有重载最终都委托给 `Session.create(...)`。
- `MultiAgentFacadeTest` 验证了 `sessionId` 与 `envs` 会原样透传到返回的 `Session`。
