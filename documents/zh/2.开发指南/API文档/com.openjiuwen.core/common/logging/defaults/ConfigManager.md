# com.openjiuwen.core.common.logging.ConfigManager

## 类 ConfigManager

```java
public class ConfigManager
```

`ConfigManager` 负责装载完整 YAML 配置，并提供按点路径读取配置值的能力，例如 `logging.level`。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `config` | `Map<String, Object>` | 当前加载后的配置树。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ConfigManager()` | 使用默认日志配置初始化。 |
| `public ConfigManager(String configPath)` | 从指定 YAML 路径加载配置。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void reload(String configPath)` | 重新装载指定路径的配置。 |
| `public Object get(String key, Object defaultValue)` | 按点路径读取配置值；缺失时返回默认值。 |
| `public Object get(String key)` | 按点路径读取配置值；缺失时返回 `null`。 |
| `public Map<String, Object> getConfig()` | 返回当前配置树。 |

## 说明

- 当 `configPath == null` 时，会使用 `DefaultLogConstants.defaultLogConfig()` 作为初始配置。
- 如果配置包含 `logging.level`，`ConfigManager` 会把 `CRITICAL/FATAL/ERROR/WARNING/WARN/INFO/DEBUG/NOTSET` 规范化为数值级别 `50/50/40/30/30/20/10/0`。
- 读取外部路径前会调用 `PathChecker.isSensitivePath(...)`；若装载失败，则回退到 `Map.of("logging", Map.of("level", 30))`。
