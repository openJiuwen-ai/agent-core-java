# interaction

`com.openjiuwen.core.session.interaction` 负责组织用户交互载荷、交互等待逻辑，以及 agent/workflow 两类打断恢复入口。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AgentInteraction`](./interaction/AgentInteraction.md) | agent 级交互处理器。 |
| [`AgentInterrupt`](./interaction/AgentInterrupt.md) | agent 因等待用户输入而中断时抛出的异常。 |
| [`BaseInteraction`](./interaction/BaseInteraction.md) | 管理交互输入队列的抽象基类。 |
| [`InteractionOutput`](./interaction/InteractionOutput.md) | 用于输出交互事件的数据载荷。 |
| [`InteractiveInput`](./interaction/InteractiveInput.md) | 表达用户交互输入的值对象。 |
| [`SimpleAgentInteraction`](./interaction/SimpleAgentInteraction.md) | 通过 checkpointer 打断 agent 的简化交互器。 |
| [`WorkflowInteraction`](./interaction/WorkflowInteraction.md) | 通过 graph interrupt 打断工作流执行的交互器。 |

## 说明

- 相关测试：`InteractiveInputFullTest`、`InteractiveInputTest`、`WorkflowInteractionTest`。
