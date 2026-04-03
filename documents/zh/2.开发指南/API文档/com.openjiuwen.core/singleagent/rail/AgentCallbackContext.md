# com.openjiuwen.core.singleagent.rail.AgentCallbackContext

## 类 AgentCallbackContext

```java
public class AgentCallbackContext
```

在 rail 与 callback 钩子之间传递状态的统一上下文对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `agent` | `Object` | `-` | 当前触发回调的 agent 实例。 |
| `event` | `AgentCallbackEvent` | `-` | 当前生命周期事件。 |
| `inputs` | `EventInputs` | `null` | 当前事件对应的输入载荷。 |
| `config` | `Object` | `-` | 运行时配置对象。 |
| `session` | `Session` | `-` | 当前会话实例。 |
| `context` | `ModelContext` | `-` | 当前模型上下文。 |
| `extra` | `Map<String, Object>` | `new HashMap<>()` | 供不同 rail 共享状态的附加数据。 |
| `exception` | `Exception` | `-` | 异常事件中记录的异常对象。 |
| `retryAttempt` | `int` | `0` | 当前重试序号。 |
| `retryRequest` | `RetryRequest` | `-` | 待消费的重试请求。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void fire(AgentCallbackEvent event)` | 触发指定事件上的全部已注册回调。 |
| `public void requestRetry(double delaySeconds)` | 请求当前 rail 包装的方法在延迟后再重试一次。 |
| `public RetryRequest consumeRetryRequest()` | 读取并清空待消费的重试请求。 |
| `public void lifecycle(AgentCallbackEvent before, AgentCallbackEvent after, Runnable body)` | 在 `before` / `after` 生命周期事件之间执行一段代码。 |

## 说明

- 相关测试：`AbilityManagerSupplementTest`、`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`DataClassCoverageTest`、`AgentCallbackContextTest`、`AgentRailTest`、`RailExecutorTest`。
- `lifecycle(...)` 会自动保存并恢复进入方法前的 `inputs`，便于模型调用和工具调用阶段覆写临时载荷。
