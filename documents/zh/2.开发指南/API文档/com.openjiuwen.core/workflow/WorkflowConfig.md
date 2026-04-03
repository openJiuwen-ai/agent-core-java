# com.openjiuwen.core.workflow.WorkflowConfig

## 类 WorkflowConfig

```java
public class WorkflowConfig
```

`WorkflowConfig` 保存工作流卡片、结构规格以及最大嵌套深度等运行配置。

## 关键字段

| 字段 | 说明 |
| --- | --- |
| `card` | 当前工作流卡片。 |
| `spec` | 当前工作流结构定义。 |
| `workflowMaxNestingDepth` | 子工作流最大嵌套深度，默认值为 `5`。 |

## 方法

- 提供 `card`、`spec`、`workflowMaxNestingDepth` 的标准 getter/setter。
- `setWorkflowMaxNestingDepth(...)` 会把传入值约束在源码允许的范围内。
