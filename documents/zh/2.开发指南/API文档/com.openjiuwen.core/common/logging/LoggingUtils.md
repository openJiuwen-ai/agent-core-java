# com.openjiuwen.core.common.logging.LoggingUtils

## 类 LoggingUtils

```java
public final class LoggingUtils
```

`LoggingUtils` 提供 logging 子系统的线程上下文与配置校验工具，主要负责维护 `sessionId/traceId`，以及校验日志大小与日志路径。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void setSessionId(String traceId)` | 把当前线程的 `sessionId/traceId` 设为指定值；传入 `null` 时回退到默认值。 |
| `public static String getSessionId()` | 读取当前线程的 `sessionId/traceId`；未设置时返回 `default_trace_id`。 |
| `public static void clearSessionId()` | 清空当前线程上下文中的 `sessionId/traceId`。 |
| `public static int getLogMaxBytes(Object maxBytesConfig)` | 解析 `max_bytes` 配置并把结果限制在 `1..100MB` 范围内。 |
| `public static String normalizeAndValidateLogPath(Object pathValue)` | 规范化日志路径，并拒绝敏感路径或空路径。 |

## 说明

- `TRACE_ID_CONTEXT` 使用 `InheritableThreadLocal`，因此子线程会继承父线程的 trace/session ID；`LogManagerTest` 对线程隔离和默认值行为做了覆盖。
- `getLogMaxBytes()` 无法解析整数时会抛出 `IllegalArgumentException`；超出范围时会回退到 `100 * 1024 * 1024`。
- `normalizeAndValidateLogPath()` 会优先尝试 `toRealPath()`，失败后回退到绝对路径规范化，并通过 `PathChecker.isSensitivePath(...)` 拒绝敏感位置；非法输入会抛出 `ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID, ...)`。
