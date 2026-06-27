# com.openjiuwen.core.single_agent.AgentCallbackManager

## 类 AgentCallbackManager

```java
public class AgentCallbackManager
```

管理回调与 rail 的注册、注销和触发。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public AgentCallbackManager(String agentId)` | 使用 `agentId` 初始化回调命名空间。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority)` | 为指定事件注册回调，并显式设置优先级。 |
| `public void registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | 使用默认优先级 `100` 注册回调。 |
| `public void registerRail(AgentRail rail, Object agent)` | 注册 rail，并同步接入其暴露的回调和工具。 |
| `public void unregisterRail(AgentRail rail, Object agent)` | 注销 rail，并撤销其注册的回调与工具。 |
| `public void unregister(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback)` | 从指定事件移除单个回调。 |
| `public void clear(AgentCallbackEvent event)` | 清理某个事件或全部事件上的已注册钩子。 |
| `public boolean hasHooks(AgentCallbackEvent event)` | 检查指定事件当前是否存在已注册钩子。 |
| `public void execute(AgentCallbackEvent event, AgentCallbackContext ctx)` | 触发指定事件上的全部回调。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`。
- 该类型同时支持函数式回调和 `AgentRail` 回调，并通过 `agentId + "_" + event.getValue()` 生成事件名，避免不同 agent 之间冲突。
