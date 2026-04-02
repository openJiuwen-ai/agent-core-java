# com.openjiuwen.core.session.interaction.BaseInteraction

## 类 BaseInteraction

```java
public abstract class BaseInteraction
```

交互处理抽象基类，负责维护交互输入队列、最近一次输入和关联 session。

## 字段

| 签名 | 说明 |
| --- | --- |
| `protected List<Object> interactiveInputs` | 当前待消费的交互输入队列。 |
| `protected Object latestInteractiveInputs` | 最近一次交互输入。 |
| `protected int idx` | 当前已消费的交互输入下标。 |
| `protected final BaseSession session` | 关联的 `BaseSession`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public abstract Object waitUserInputs(Object value)` | 等待用户输入；具体阻塞/打断方式由子类实现。 |
| `public Object userLatestInput(Object value)` | 返回最近一次用户输入；基类默认返回 `null`。 |

## 说明

- 构造阶段会尝试从 `session.state()` 里的 `Constant.INTERACTIVE_INPUT` 读取已有输入，并与 `defaultInput` 合并。
- 子类通常通过受保护的输入队列辅助逻辑依次消费交互输入。
