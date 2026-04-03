# com.openjiuwen.core.singleagent.legacy.WorkflowFactory

## 类 WorkflowFactory

```java
public class WorkflowFactory implements Supplier<Workflow>
```

按需创建工作流实例的提供器，每次 `get()` 都返回新的 `Workflow`。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory, String workflowName, String workflowDescription, Object inputSchema)` | 保存工作流构造函数，并根据传入元数据生成一个 `WorkflowCard`。 |
| `public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory)` | 使用空名称、空描述和空输入模式的简化构造。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public WorkflowCard card()` | 返回构造时缓存的工作流卡片。 |
| `@Override public Workflow get()` | 调用内部 `Supplier<Workflow>`，返回一个新的工作流实例。 |

## 说明

- 该类型适合把同一工作流逻辑按需重复注册到旧版 agent，避免在并发场景中复用同一个 `Workflow` 实例。
