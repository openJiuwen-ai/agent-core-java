# com.openjiuwen.core.session.interaction.WorkflowInteraction

## 类 WorkflowInteraction

```java
public class WorkflowInteraction extends BaseInteraction
```

工作流级交互处理器，通过抛出 graph interrupt 来中断当前图执行并等待用户输入。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowInteraction(BaseSession session)` | 绑定 session，并尝试读取/清理工作流状态中残留的交互输入。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object waitUserInputs(Object value)` | 优先消费已有输入；若没有，则提交组件状态、写出交互输出，并抛出 `GraphInterruptRuntimeWrapper`。 |
| `public Object userLatestInput(Object value)` | 返回最近一次输入；若没有，则直接发出交互输出并抛出 `GraphInterruptRuntimeWrapper`。 |

## 嵌套类型

| 签名 | 说明 |
| --- | --- |
| `public static class GraphInterruptRuntimeWrapper extends RuntimeException` | 对图中断异常的运行时包装，便于在不声明 checked exception 的方法中抛出。 |

## 说明

- 相关测试：`WorkflowInteractionTest`。
- 当需要真正打断执行时，源码会构造 `InteractionOutput(nodeId, value)`，再包装成可恢复的图中断对象抛出。
