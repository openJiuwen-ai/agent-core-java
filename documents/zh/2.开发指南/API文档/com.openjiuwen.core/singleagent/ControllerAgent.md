# com.openjiuwen.core.single_agent.ControllerAgent

## 类 ControllerAgent

```java
public class ControllerAgent extends BaseAgent
```

基于 `Controller` 的单智能体实现。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public ControllerAgent(AgentCard card, Controller controller)` | 使用默认 `ControllerConfig` 和默认 `ContextEngineConfig` 创建实例。 |
| `public ControllerAgent(AgentCard card, Controller controller, ControllerConfig config)` | 使用显式 `ControllerConfig` 创建实例。 |
| `protected ControllerAgent(AgentCard card, Controller controller, ControllerConfig config, ContextEngineConfig contextEngineConfig)` | 允许子类同时注入 `ControllerConfig` 与 `ContextEngineConfig`。 |

## 方法

| 签名 | 说明 |
|---|---|
| `@Override public BaseAgent configure(Object config)` | 接受 `ControllerConfig` 或配置 `Map` 并更新当前实例。 |
| `@Override public Object getConfig()` | 返回当前 `ControllerConfig`。 |
| `public Controller getController()` | 返回底层 `Controller` 实例。 |
| `public ContextEngine getContextEngine()` | 返回当前上下文引擎。 |
| `public void releaseSession(String sessionId)` | 释放指定 `sessionId` 关联的事件队列订阅和运行时资源。 |
| `@Override public ControllerOutput invoke(Object inputs, Session session)` | 将输入转换为 `InputEvent` 后执行一次控制器调用。 |
| `@Override public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | 将输入转换为 `InputEvent` 后执行控制器流式调用。 |

## 说明

- 相关测试：`ControllerAgentTest`。
- 该实现会在构造时初始化 `Controller` 与 `ContextEngine`，适用于需要事件驱动调度与任务处理的场景。
