# com.openjiuwen.core.singleagent.legacy.LegacyApi

## 类 LegacyApi

```java
public final class LegacyApi
```

旧版单智能体模块的静态兼容入口。

## 方法

| 签名 | 说明 |
|---|---|
| `@Deprecated(since = "0.1.7", forRemoval = true) public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion, String workflowName, String workflowDescription, Object inputSchema, Supplier<Workflow> factory)` | 记录弃用告警，并把工作流元数据与工厂函数封装成 `WorkflowFactory`。 |
| `@Deprecated(since = "0.1.7", forRemoval = true) public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion, Supplier<Workflow> factory)` | 省略名称、描述和输入模式的简化重载。 |
| `@Deprecated(since = "0.1.7", forRemoval = true) public static LegacyReActAgentConfig createReActAgentConfig(String agentId, String agentVersion, String description, ModelConfig model, List<Map<String, String>> promptTemplate)` | 记录弃用告警，并调用 `LegacyReActAgent.createReActAgentConfig(...)` 生成旧版 ReAct 配置。 |
| `public static void emitDeprecationWarning(String className, String alternative)` | 通过 agent 日志输出运行时弃用提示，说明推荐替代类型。 |

## 说明

- 类本身已标记 `@Deprecated(since = "0.1.7", forRemoval = true)`。
- 源码说明这些兼容工厂会在 `v1.0.0` 移除，新代码应直接使用现代工作流与现代 `singleagent` 配置。
