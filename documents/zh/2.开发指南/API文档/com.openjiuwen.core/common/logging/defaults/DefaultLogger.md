# com.openjiuwen.core.common.logging.defaults.DefaultLogger

## 类 DefaultLogger

```java
public class DefaultLogger implements LoggerProtocol
```

`DefaultLogger` 是默认的 `LoggerProtocol` 实现，底层同时维护 provider-neutral SLF4J 与 `java.util.logging.Logger`，并支持结构化事件输出。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `logType` | `String` | 当前 logger 的业务类型键。 |
| `config` | `Map<String, Object>` | 当前 logger 配置的不可变快照。 |
| `slf4jLogger` | `org.slf4j.Logger` | 面向普通文本日志的 SLF4J logger。 |
| `julLogger` | `java.util.logging.Logger` | 用于 handler/filter 扩展和辅助输出的 JUL logger。 |
| `filters` | `List<Filter>` | 当前注册的 JUL 过滤器列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DefaultLogger(String logType, Map<String, Object> config)` | 绑定 `logType`、缓存配置，并初始化 SLF4J/JUL 双后端。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void debug(String msg, Object... args)` | 在 DEBUG 开启时输出调试日志。 |
| `public void info(String msg, Object... args)` | 输出信息级日志。 |
| `public void warning(String msg, Object... args)` | 输出警告级日志。 |
| `public void error(String msg, Object... args)` | 输出错误级日志。 |
| `public void critical(String msg, Object... args)` | 以 ERROR 通道输出严重错误，并追加 `[CRITICAL]` 前缀。 |
| `public void exception(String msg, Throwable t, Object... args)` | 输出异常日志，并附带 Throwable。 |
| `public void log(int level, String msg, Object... args)` | 把数值级别映射到 `error/warning/info/debug`。 |
| `public void setLevel(int level)` | 调整 SDK 内部日志阈值，并同步调整 JUL logger 级别；SLF4J provider 的真实输出级别由应用侧配置决定。 |
| `public void addHandler(Handler handler)` | 向 JUL logger 注册 handler。 |
| `public void removeHandler(Handler handler)` | 从 JUL logger 移除 handler。 |
| `public void addFilter(Filter filter)` | 追加 JUL 过滤器。 |
| `public void removeFilter(Filter filter)` | 删除 JUL 过滤器。 |
| `public java.util.logging.Logger logger()` | 返回内部 JUL logger。 |
| `public Map<String, Object> getConfig()` | 返回当前配置快照。 |
| `public void reconfigure(Map<String, Object> newConfig)` | 用新配置覆盖当前配置快照。 |
| `public void logEvent(String msg, LogEventType eventType, BaseLogEvent event)` | 把结构化事件序列化为 JSON，并按 `event.getLogLevel()` 对应的通道输出。 |

## 说明

- 文本日志输出前会把 `trace_id` 和 `log_type` 写入 MDC；控制字符会被 `sanitize()` 转义为 `\n`、`\t`、`\xNN` 等安全文本。
- `addHandler/removeHandler/addFilter/removeFilter/logger()` 都只作用于 JUL 后端；SLF4J 后端主要负责常规文本输出。
- `logEvent()` 在 `event == null && eventType == null` 时退化为 `info(msg)`；仅传入 `eventType` 时会自动创建事件对象、补齐 `traceId/moduleId/moduleName`，并在 JSON 序列化失败时回退到 `event.toMap().toString()`。
