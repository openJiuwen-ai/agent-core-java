# com.openjiuwen.core.single_agent.agents.ReActAgent

## 类 ReActAgent

```java
public class ReActAgent extends LegacyReActAgent
```

`LegacyReActAgent` 的旧名称包装类，不增加新的执行逻辑。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public ReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools)` | 使用旧版配置、工作流和工具初始化兼容 ReAct agent。 |
| `public ReActAgent(LegacyReActAgentConfig agentConfig)` | 仅使用旧版配置初始化兼容 ReAct agent。 |

## 说明

- 该类全部行为都继承自 `LegacyReActAgent`，保留旧命名仅用于兼容历史调用代码。
