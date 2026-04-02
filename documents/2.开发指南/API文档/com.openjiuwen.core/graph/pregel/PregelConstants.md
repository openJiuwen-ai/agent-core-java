# com.openjiuwen.core.graph.pregel.PregelConstants

## final 类 PregelConstants

```java
public final class PregelConstants
```

Pregel 执行引擎使用的常量定义。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `START` | `String` | `"__start__"` | 虚拟起始节点标识。 |
| `END` | `String` | `"__end__"` | 虚拟结束节点标识。 |
| `MAX_RECURSIVE_LIMIT` | `int` | `10000` | 默认最大 super-step 上限。 |
| `TASK_STATUS_INTERRUPT` | `String` | `"__interrupt__"` | 节点被中断时的状态值。 |
| `TASK_STATUS_ERROR` | `String` | `"__error__"` | 节点执行失败时的状态值。 |
| `NS_SEPARATOR` | `String` | `":"` | 配置路径使用的命名空间分隔符。 |
| `NS_REPLACE_CHAR` | `String` | `"#"` | 在 key 中替换命名空间分隔符的字符。 |
| `NS` | `String` | `"ns"` | 命名空间配置键。 |
| `PARENT_NS` | `String` | `"parent_ns"` | 父命名空间配置键。 |
| `SESSION_ID` | `String` | `"session_id"` | 会话 ID 配置键。 |
| `RECURSION_LIMIT` | `String` | `"recursion_limit"` | 递归上限配置键。 |

## 相关测试

- `PregelTest`
