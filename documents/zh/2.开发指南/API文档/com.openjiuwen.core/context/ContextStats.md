# com.openjiuwen.core.context.ContextStats

## class ContextStats

```java
public class ContextStats
```

`ContextStats` 表示上下文或上下文窗口的统计快照，覆盖消息总量、对话轮次、不同角色消息数以及消息/工具的 token 用量。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `totalMessages` | `int` | `0` | 当前统计范围内的消息总数。 |
| `totalTokens` | `int` | `0` | 当前统计范围内累计 token 总数。 |
| `totalDialogues` | `int` | `0` | 由 `ContextUtils.findAllDialogueRound()` 识别出的对话轮次数。 |
| `systemMessages` | `int` | `0` | `system` 角色消息数。 |
| `userMessages` | `int` | `0` | `user` 角色消息数。 |
| `assistantMessages` | `int` | `0` | `assistant` 角色消息数。 |
| `toolMessages` | `int` | `0` | `tool` 角色消息数。 |
| `tools` | `int` | `0` | 注入到窗口中的 `ToolInfo` 数量。 |
| `systemMessageTokens` | `int` | `0` | 系统消息累计 token。 |
| `userMessageTokens` | `int` | `0` | 用户消息累计 token。 |
| `assistantMessageTokens` | `int` | `0` | 助手消息累计 token。 |
| `toolMessageTokens` | `int` | `0` | 工具消息累计 token。 |
| `toolTokens` | `int` | `0` | 工具定义本身的 token 数。 |

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器和构造器。
- `ContextStatsTest` 验证了默认 builder 的零值初始化，以及 builder 对字段赋值的正确性。
