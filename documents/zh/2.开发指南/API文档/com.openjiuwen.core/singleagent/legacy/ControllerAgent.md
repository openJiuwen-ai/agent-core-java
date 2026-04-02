# com.openjiuwen.core.singleagent.legacy.ControllerAgent

## 类 ControllerAgent

```java
public class ControllerAgent extends BaseAgent
```

以 `BaseController` 为执行入口的旧版 agent 包装器。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public ControllerAgent(AgentConfig agentConfig, BaseController controller)` | 初始化基类配置并绑定控制器；如果控制器非空，会立即调用 `controller.setupFromAgent(this)`。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public BaseController getController()` | 返回当前绑定的控制器。 |
| `public void setController(BaseController controller)` | 替换控制器，并在非空时重新执行 `setupFromAgent(this)`。 |
| `@Override public Object invoke(Map<String, Object> inputs, Session session)` | 将输入和会话转换为 `AgentSessionApi` 后调用 `controller.invoke(...)`；如果未传入外部会话，结束时自动 `postRun()`。 |
| `@Override public Iterator<Object> stream(Map<String, Object> inputs, Session session)` | 复用控制器的同步调用结果，再从 `AgentSessionApi.streamOutput` 收集流式输出；若没有流式片段且结果非空，则返回单个结果。 |

## 说明

- 当 `session` 不是 `AgentSessionApi` 时，会优先读取 `inputs["conversation_id"]` 作为会话 ID，否则回落到 `default_session`。
