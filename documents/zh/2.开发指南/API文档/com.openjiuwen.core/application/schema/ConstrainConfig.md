# com.openjiuwen.core.single_agent.legacy.config.ConstrainConfig

## class ConstrainConfig

```java
public class ConstrainConfig
```

`ConstrainConfig` 描述应用层 Agent 的会话窗口与最大迭代约束，同时提供下划线和驼峰两套 JSON 字段入口。

## 常量

| 常量 | 值 | 说明 |
|---|---|---|
| `DEFAULT_RESERVED_MAX_CHAT_ROUNDS` | `10` | 默认保留的会话轮次。 |
| `DEFAULT_MAX_ITERATION` | `5` | 默认最大规划或执行迭代次数。 |

## 字段

| 字段 | 类型 | 默认值 | JSON 别名 | 说明 |
|---|---|---|---|---|
| `reservedMaxChatRounds` | `int` | `10` | `reserved_max_chat_rounds` / `reservedMaxChatRounds` | 影响上下文窗口推导；`LlmAgent` 会将它转换为 `maxContextMessageNum = rounds * 2`。 |
| `maxIteration` | `int` | `5` | `max_iteration` / `maxIteration` | ReAct 或工作流控制器允许的最大迭代次数。 |

## 构造方法

### `public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration)`

Builder 构造器会把 `null` 恢复为默认值，并对两个整数执行大于 `0` 的校验。

## 显式方法

### `public void setReservedMaxChatRounds(int reservedMaxChatRounds)`

设置保留会话轮次；当值小于等于 `0` 时抛出 `IllegalArgumentException`。

### `public void setMaxIteration(int maxIteration)`

设置最大迭代次数；当值小于等于 `0` 时抛出 `IllegalArgumentException`。

## 校验与行为说明

- `reservedMaxChatRounds` 与 `maxIteration` 都必须大于 `0`。
- `reservedMaxChatRounds = 3` 时，`LlmAgent` 会将上下文窗口上限推导为 `6` 条消息。
- 非法值会抛出 `IllegalArgumentException`，异常消息中包含当前输入值。
