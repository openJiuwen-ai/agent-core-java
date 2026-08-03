# com.openjiuwen.core.single_agent.legacy.config.ConstrainConfig

## 类 ConstrainConfig

```java
public class ConstrainConfig
```

控制上下文保留轮次与 ReAct 最大迭代次数的约束配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `DEFAULT_RESERVED_MAX_CHAT_ROUNDS` | `static final int` | `10` | 默认保留的历史对话轮次数。 |
| `DEFAULT_MAX_ITERATION` | `static final int` | `5` | 默认最大推理迭代次数。 |
| `reservedMaxChatRounds` | `int` | `DEFAULT_RESERVED_MAX_CHAT_ROUNDS` | 实际保留的历史轮次数，要求大于 `0`。 |
| `maxIteration` | `int` | `DEFAULT_MAX_ITERATION` | 实际最大迭代次数，要求大于 `0`。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `@Builder public ConstrainConfig(Integer reservedMaxChatRounds, Integer maxIteration)` | `null` 会回落到默认值，非空参数会经过正整数校验。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void setReservedMaxChatRounds(int reservedMaxChatRounds)` | 设置保留轮次；传入 `<= 0` 时抛出 `IllegalArgumentException`。 |
| `public void setMaxIteration(int maxIteration)` | 设置最大迭代次数；传入 `<= 0` 时抛出 `IllegalArgumentException`。 |

## 说明

- `ConstrainConfigValidationTest` 验证了默认值保持为 `10` 和 `5`，并校验 builder 与 setter 都会拒绝 `0`。
