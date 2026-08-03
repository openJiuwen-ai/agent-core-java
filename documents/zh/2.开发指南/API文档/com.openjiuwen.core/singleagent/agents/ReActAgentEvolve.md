# com.openjiuwen.core.single_agent.agents.ReActAgentEvolve

## 类 ReActAgentEvolve

```java
public class ReActAgentEvolve extends BaseAgent
```

在 ReAct 基础上引入可演进 operator 的单智能体实现。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public ReActAgentEvolve(AgentCard card)` | 使用 `AgentCard` 创建实例，并初始化默认配置、上下文引擎、技能工具和 `ToolCallOperator`。 |

## 方法

| 签名 | 说明 |
|---|---|
| `@Override public BaseAgent configure(Object configObj)` | 用新的 `ReActAgentConfig` 更新模型、上下文和记忆相关配置。 |
| `@Override public Object getConfig()` | 返回当前 `ReActAgentConfig`。 |
| `public ContextEngine getContextEngine()` | 返回当前上下文引擎。 |
| `public Map<String, Operator> getOperators()` | 返回当前可演进 operator 注册表。 |
| `@Override public Object invoke(Object inputs, Session session)` | 执行一次支持 operator 自演进的 ReAct 调用。 |
| `@Override public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes)` | 执行流式调用，并从 `AgentSessionApi` 的流迭代器返回结果。 |
| `public void registerSkill(Object skillPath)` | 注册技能路径。 |

## 说明

- 相关测试：`ReActAgentEvolveTest`。
- 该实现使用 `LLMCallOperator` 和 `ToolCallOperator` 作为可演进 operator，可在运行期同步系统提示与工具描述更新。
