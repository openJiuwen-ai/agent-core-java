# com.openjiuwen.core.common.logging.LoggerProtocol

## 接口 LoggerProtocol

```java
public interface LoggerProtocol
```

`LoggerProtocol` 定义 Java 侧统一日志接口，覆盖等级日志、异常日志、级别控制、处理器/过滤器扩展，以及配置读取和重载能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void debug(String msg, Object... args)` | 记录调试级日志。 |
| `void info(String msg, Object... args)` | 记录信息级日志。 |
| `void warning(String msg, Object... args)` | 记录警告级日志。 |
| `default void warn(String msg, Object... args)` | `warning(...)` 的默认别名。 |
| `void error(String msg, Object... args)` | 记录错误级日志。 |
| `void critical(String msg, Object... args)` | 记录严重错误级日志。 |
| `void exception(String msg, Throwable t, Object... args)` | 记录异常及其栈信息。 |
| `void log(int level, String msg, Object... args)` | 按数字级别路由日志。 |
| `void setLevel(int level)` | 调整当前 logger 的阈值级别。 |
| `default void addHandler(Handler handler)` | 添加 `java.util.logging.Handler`；默认实现为空操作。 |
| `default void removeHandler(Handler handler)` | 移除 `java.util.logging.Handler`；默认实现为空操作。 |
| `default void addFilter(Filter filter)` | 添加 `java.util.logging.Filter`；默认实现为空操作。 |
| `default void removeFilter(Filter filter)` | 移除 `java.util.logging.Filter`；默认实现为空操作。 |
| `default Logger logger()` | 返回底层 `java.util.logging.Logger`；默认实现返回 `null`。 |
| `Map<String, Object> getConfig()` | 返回当前 logger 配置。 |
| `void reconfigure(Map<String, Object> config)` | 用新配置重载当前 logger。 |

## 说明

- 该接口统一定义等级日志、异常日志、配置重载与 JUL 扩展点；实现方可以按需覆盖默认方法。
- 处理器、过滤器和 `logger()` 都提供默认实现，因此不依赖 JUL 的实现也可以只关注核心日志方法。
