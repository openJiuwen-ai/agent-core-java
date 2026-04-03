# com.openjiuwen.core.runner.callback.FilterAction

## 枚举 FilterAction

```java
public enum FilterAction
```

定义过滤器对回调执行流程的控制动作，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前过滤动作对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `CONTINUE` | 继续执行当前回调。 |
| `STOP` | 停止整个事件处理流程。 |
| `SKIP` | 跳过当前回调并继续后续回调。 |
| `MODIFY` | 使用过滤器返回的修改后参数继续执行。 |

## 说明

- 相关测试：`CallbackFiltersTest`、`CallbackModelsTest`、`FilterActionTest`。
