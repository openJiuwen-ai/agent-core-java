# com.openjiuwen.core.runner.callback.HookType

## 枚举 HookType

```java
public enum HookType
```

定义回调框架可注册的生命周期 hook 类型，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前 hook 类型对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `BEFORE` | 在事件处理前执行。 |
| `AFTER` | 在事件处理后执行。 |
| `ERROR` | 在事件处理发生异常时执行。 |
| `CLEANUP` | 在清理阶段执行。 |

## 说明

- 相关测试：`CallbackFrameworkTest`、`HookTypeTest`。
