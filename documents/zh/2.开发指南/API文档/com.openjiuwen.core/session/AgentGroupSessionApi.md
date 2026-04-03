# com.openjiuwen.core.session.AgentGroupSessionApi

## 类 AgentGroupSessionApi

```java
public class AgentGroupSessionApi extends AgentSessionApi
```

面向 agent group 的对外会话门面，直接复用 `AgentSessionApi` 的状态、流式输出与交互能力。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentGroupSessionApi(String sessionId, Map<String, Object> envs)` | 使用给定的 `sessionId` 和环境变量创建 agent group 会话；`sessionId` 为空时由父类自动生成。 |
| `public AgentGroupSessionApi(String sessionId)` | - |
| `public AgentGroupSessionApi()` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs)` | 以静态工厂方式创建一个 `AgentGroupSessionApi` 实例。 |

## 说明

- 该类型只是对 `AgentSessionApi` 的轻量派生；源码没有新增额外状态字段或独立子系统。
