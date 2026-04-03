# com.openjiuwen.core.common.logging.defaults.LoggingDefaults

## 类 LoggingDefaults

```java
public final class LoggingDefaults
```

`LoggingDefaults` 提供 logging 配置的全局便捷入口，分别包装 `ConfigManager` 和 `LogConfig` 两个单例对象。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static ConfigManager config()` | 返回全局 `ConfigManager` 单例。 |
| `public static void configure(String configPath)` | 重新装载全局配置单例。 |
| `public static LogConfig logConfig()` | 返回全局 `LogConfig` 单例。 |
| `public static void configureLog(String configPath)` | 重新装载全局日志配置单例。 |
| `public static synchronized void reset()` | 重建两个全局单例，主要供测试使用。 |

## 说明

- `config()` / `configure()` 对应完整配置树的读取与重载；`logConfig()` / `configureLog()` 对应 `logging` 段的读取与重载。
- `reset()` 会重新创建 `ConfigManager()` 和 `LogConfig()`，因此会恢复到源码里的默认配置常量。
