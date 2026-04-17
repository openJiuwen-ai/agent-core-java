# com.openjiuwen.core.workflow.component.llm.ExecutionStatus

Questioner 状态机的执行状态枚举。

## enum ExecutionStatus

```java
public enum ExecutionStatus
```

## 枚举值

| 值 | 说明 |
| --- | --- |
| `START` | 起始状态。 |
| `USER_INTERACT` | 用户交互状态。 |
| `END` | 结束状态。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前状态对应的字符串值。 |
| `public static ExecutionStatus fromValue(String value)` | 根据字符串值解析枚举成员；未知值会抛出 `IllegalArgumentException`。 |

## Notes

- 该枚举内部维护的字符串值分别为 `start`、`user_interact` 和 `end`。
