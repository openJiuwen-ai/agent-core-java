# com.openjiuwen.core.runner.callback.ChainAction

## 枚举 ChainAction

```java
public enum ChainAction
```

定义回调在链式执行过程中可返回的控制动作，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前链式动作对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `CONTINUE` | 继续执行链中的下一个回调。 |
| `BREAK` | 终止链式执行并返回当前结果。 |
| `RETRY` | 重新执行当前回调。 |
| `ROLLBACK` | 回滚已经执行过的回调。 |

## 说明

- 相关测试：`CallbackChainTest`、`CallbackFrameworkTest`、`CallbackModelsTest`、`ChainActionTest`。
