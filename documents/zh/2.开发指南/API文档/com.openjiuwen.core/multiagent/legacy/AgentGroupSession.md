# com.openjiuwen.core.multiagent.legacy.AgentGroupSession

## class AgentGroupSession

```java
@Deprecated
public class AgentGroupSession extends AgentGroupSessionApi
```

`AgentGroupSession` 是 legacy 路径下的分组会话别名，用于保持 `openjiuwen.core.multi_agent.legacy.AgentGroupSession` 风格的导入方式。

## 构造方法

### `public AgentGroupSession(String sessionId, Map<String, Object> envs)`

创建带显式会话 ID 与环境变量的 legacy 分组会话。

### `public AgentGroupSession(String sessionId)`

创建只指定会话 ID 的 legacy 分组会话。

### `public AgentGroupSession()`

创建默认参数的 legacy 分组会话。

## 静态工厂

### `public static AgentGroupSession create(String sessionId, Map<String, Object> envs)`

返回新的 `AgentGroupSession` 实例。

## 说明

- 该类型本身不新增字段或方法，只保留 legacy 导入名。
- `LegacyCompatibilityAliasTest` 验证了它仍继承 `AgentGroupSessionApi` 的 `updateState(...)`、`getEnv(...)` 与 `getState(...)` 等会话辅助能力。
