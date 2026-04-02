# com.openjiuwen.core.singleagent.rail.AgentRail

## 抽象类 AgentRail

```java
public abstract class AgentRail
```

单智能体 rail 的抽象基类。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `EVENT_METHOD_MAP` | `Map<AgentCallbackEvent, String>` | `new EnumMap<>(AgentCallbackEvent.class)` | 公开暴露的事件与 hook 方法名映射。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public int getPriority()` | 返回当前 rail 优先级。 |
| `public void setPriority(int priority)` | 设置 rail 优先级，数值越小越先执行。 |
| `public List<ToolCard> getTools()` | 返回 rail 携带的工具列表。 |
| `public List<Object> getSkills()` | 返回 rail 携带的技能列表，当前主要作为保留扩展位。 |
| `public void beforeInvoke(AgentCallbackContext ctx)` | `invoke` 前置 hook。 |
| `public void afterInvoke(AgentCallbackContext ctx)` | `invoke` 后置 hook。 |
| `public void beforeModelCall(AgentCallbackContext ctx)` | 模型调用前置 hook。 |
| `public void afterModelCall(AgentCallbackContext ctx)` | 模型调用后置 hook。 |
| `public void onModelException(AgentCallbackContext ctx)` | 模型调用异常 hook。 |
| `public void beforeToolCall(AgentCallbackContext ctx)` | 工具调用前置 hook。 |
| `public void afterToolCall(AgentCallbackContext ctx)` | 工具调用后置 hook。 |
| `public void onToolException(AgentCallbackContext ctx)` | 工具调用异常 hook。 |
| `public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks()` | 仅提取子类真正覆写的 hook 方法并返回事件映射。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`AgentRailTest`。
- rail 支持跨回调保存状态、自动向 agent 注册工具，并按优先级顺序执行。
