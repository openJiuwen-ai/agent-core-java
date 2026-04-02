# com.openjiuwen.core.singleagent.legacy.LegacyReActAgent

## 类 LegacyReActAgent

```java
public class LegacyReActAgent extends BaseAgent
```

把旧版 ReAct 配置转换到现代 `com.openjiuwen.core.singleagent.agents.ReActAgent` 的兼容实现。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public LegacyReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools)` | 创建现代 `ReActAgent` 代理，按旧配置生成 `AgentCard`，随后同步初始化工具和工作流。 |
| `public LegacyReActAgent(LegacyReActAgentConfig agentConfig)` | 仅使用配置创建实例，不预注册额外工具和工作流。 |

## 方法

| 签名 | 说明 |
|---|---|
| `@Override public void addTools(List<Tool> newTools)` | 在基类注册工具后，把每个工具的 `ToolCard` 同步加入现代代理的 `AbilityManager`。 |
| `@Override public void addWorkflows(List<Workflow> newWorkflows)` | 在基类注册工作流后，把每个工作流卡片同步加入现代代理。 |
| `@Override public void addWorkflowItems(List<?> items)` | 兼容 `WorkflowFactory`、`Workflow` 等异构工作流输入，并把对应卡片同步到现代代理。 |
| `public AssistantMessage callModel(String userInput, Session session, boolean isFirstCall)` | 构造提示词和聊天历史，获取可用工具信息，调用内部 `Model` 完成一次推理，并把 AI 回复写回上下文。 |
| `@Override public Object invoke(Map<String, Object> inputs, Session session)` | 将输入转成 `AgentSessionApi` 后委托给现代代理执行；如果没有外部会话，结束时自动 `postRun()`。 |
| `@Override public Iterator<Object> stream(Map<String, Object> inputs, Session session)` | 委托现代代理以 `StreamMode.OUTPUT` 执行流式调用，并在内部创建的会话结束后自动 `postRun()`。 |
| `public static LegacyReActAgentConfig createReActAgentConfig(String agentId, String agentVersion, String description, ModelConfig model, List<Map<String, String>> promptTemplate)` | 使用 builder 生成旧版 ReAct 配置对象，空提示词会回落到空列表。 |

## 说明

- `callModel(...)` 会缓存一个 `Model` 实例；首次使用时根据 `ModelConfig` 组装 `ModelClientConfig` 与 `ModelRequestConfig`。
- `toModernConfig(...)` 会把旧版 `ConstrainConfig`、模型参数和记忆作用域映射到现代 `ReActAgentConfig`。
