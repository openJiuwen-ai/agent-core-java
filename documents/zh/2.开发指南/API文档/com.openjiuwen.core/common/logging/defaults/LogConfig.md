# com.openjiuwen.core.common.logging.defaults.LogConfig

## 类 LogConfig

```java
public class LogConfig
```

`LogConfig` 负责解析 `logging` 段，并生成 common/interface/prompt_builder/performance 等 logger 的最终配置映射。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `logConfig` | `Map<String, Object>` | 当前 `logging` 段配置。 |
| `logPath` | `String` | 解析后的日志根路径。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LogConfig()` | 使用 `DefaultLogConstants.defaultInnerLogConfig()` 初始化。 |
| `public LogConfig(String configPath)` | 从指定 YAML 文件的 `logging` 段装载配置。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void reload(String configPath)` | 重新装载配置并更新 `logPath`。 |
| `public Map<String, Object> getCommonConfig()` | 返回 common logger 的最终配置。 |
| `public Map<String, Object> getInterfaceConfig()` | 返回 interface logger 的最终配置。 |
| `public Map<String, Object> getPromptBuilderConfig()` | 返回 prompt builder logger 的最终配置。 |
| `public Map<String, Object> getPerformanceConfig()` | 返回 performance logger 的最终配置。 |
| `public Map<String, Object> getCustomConfig(String logType)` | 生成任意自定义 `logType` 的默认文件配置。 |
| `public Map<String, Map<String, Object>> getAllConfigs()` | 返回 common/interface/prompt_builder/performance 四类标准配置。 |

## 说明

- `loadConfig()` 要求 YAML 顶层必须包含 `logging` 段；缺失时会抛出 `RuntimeException`。若文件不存在，则回退到 safe defaults：`level=WARNING`、`output=console`、默认日志文件名和轮转参数。
- `getBaseConfig(...)` 会把字符串级别规范化为数值级别，并通过 `LoggingUtils.getLogMaxBytes(...)` 把 `max_bytes` 限制在安全范围内。
- `getAllConfigs()` 只返回四类标准 logger；其他业务 logger 需通过 `getCustomConfig(logType)` 动态生成。
