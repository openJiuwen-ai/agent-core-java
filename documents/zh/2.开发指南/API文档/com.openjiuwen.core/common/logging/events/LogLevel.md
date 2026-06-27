# com.openjiuwen.core.common.logging.events.LogLevel

## 枚举 LogLevel

```java
public enum LogLevel
```

`LogLevel` 表示结构化事件的日志级别，供 `BaseLogEvent` 和 `DefaultLogger.logEvent(...)` 使用。

## 枚举值

| 枚举值 | 序列化值 | 说明 |
| --- | --- | --- |
| `DEBUG` | `DEBUG` | 调试级。 |
| `INFO` | `INFO` | 信息级。 |
| `WARNING` | `WARNING` | 警告级。 |
| `ERROR` | `ERROR` | 错误级。 |
| `CRITICAL` | `CRITICAL` | 严重错误级。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的大写字符串。 |
