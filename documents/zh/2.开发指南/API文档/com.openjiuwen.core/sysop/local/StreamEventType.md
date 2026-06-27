# com.openjiuwen.core.sys_operation.local.StreamEventType

## 枚举 StreamEventType

```java
public enum StreamEventType
```

定义本地流式执行过程中可能出现的事件类型，可通过 `getValue()` 读取对应的内部字符串值。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前事件类型对应的内部字符串值。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `STDOUT` | 标准输出文本事件。 |
| `STDERR` | 标准错误文本事件。 |
| `EXIT` | 进程退出事件。 |
| `ERROR` | 处理器内部错误或超时事件。 |

## 相关测试

- `LocalUtilsTest`
