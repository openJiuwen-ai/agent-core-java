# com.openjiuwen.core.singleagent.agents.ReActAgent

## 类 ReActAgent

```java
public class ReActAgent extends BaseAgent
```

基于 ReAct 循环的单智能体实现。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public ReActAgent(AgentCard card)` | 使用 `AgentCard` 创建实例，并初始化默认配置、上下文引擎与记忆作用域。 |

## 方法

| 签名 | 说明 |
|---|---|
| `@Override public BaseAgent configure(Object configObj)` | 用新的 `ReActAgentConfig` 更新模型、上下文和记忆相关配置。 |
| `@Override public Object getConfig()` | 返回当前 `ReActAgentConfig`。 |
| `public ContextEngine getContextEngine()` | 返回当前上下文引擎。 |
| `@Override public Object invoke(Object inputs, Session session)` | 执行一次 ReAct 调用，并在工具调用完成或达到最大轮次后返回结果。 |
| `@Override public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | 执行流式调用，并从 `AgentSessionApi` 的流迭代器返回结果。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`、`ReActAgentConfigTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`。
- 调用流程按“推理 -> 行动 -> 观察 -> 重复”循环执行，直到模型不再返回 `toolCalls` 或达到 `maxIterations`。
- 输入支持 `Map`（读取 `query` 与可选 `conversation_id`）或 `String`；`invoke` 返回包含 `output` 和 `result_type` 的结果 `Map`。
