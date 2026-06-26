# com.openjiuwen.core.common.logging.LogManager

## 类 LogManager

```java
public final class LogManager
```

`LogManager` 是按 `logType` 缓存 `LoggerProtocol` 的全局注册中心，负责延迟初始化默认 logger、注册自定义 logger，以及返回所有已注册实例。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void setDefaultLoggerFactory(LoggerFactory factory)` | 设置默认 logger 工厂。 |
| `public static synchronized void initialize()` | 初始化 logging 系统；若已初始化则直接返回。 |
| `public static void registerLogger(String logType, LoggerProtocol logger)` | 为指定 `logType` 注册自定义 logger。 |
| `public static LoggerProtocol getLogger(String logType)` | 返回指定类型的 logger；缺失时按需创建并缓存。 |
| `public static Map<String, LoggerProtocol> getAllLoggers()` | 返回当前 logger 注册表的不可变快照。 |
| `public static synchronized void reset()` | 清空全部 logger、初始化标记和默认工厂，主要供测试使用。 |

## 嵌套类型

| 类型 | 说明 |
| --- | --- |
| `LoggerFactory` | `@FunctionalInterface`，定义 `LoggerProtocol create(String logType, Map<String, Object> config)`。 |
| `LogConfigProvider` | 提供公开方法 `setProvider(Supplier<Map<String, Map<String, Object>>> p)`，用于注入日志配置来源。 |

## 说明

- `initialize()` 幂等；若 `defaultLoggerFactory` 为空，会尝试反射加载 `com.openjiuwen.core.common.logging.defaults.DefaultLogger` 的 `(String, Map)` 构造函数。
- `getLogger()` 在配置源中找不到当前 `logType` 时，会回退到 `Map.of("level", "INFO", "output", "console")`。
- `LogManagerTest` 覆盖了自定义注册、按需创建、缓存复用、`getAllLoggers()` 返回值以及 `reset()` 后重新初始化的行为。
