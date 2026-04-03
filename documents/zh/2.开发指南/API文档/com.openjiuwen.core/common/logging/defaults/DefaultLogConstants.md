# com.openjiuwen.core.common.logging.defaults.DefaultLogConstants

## 类 DefaultLogConstants

```java
public final class DefaultLogConstants
```

`DefaultLogConstants` 汇总 logging 子系统的默认级别、默认路径、默认文件名、轮转参数和默认格式串。

## 常量

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `DEFAULT_LEVEL` | `"INFO"` | 默认日志级别。 |
| `DEFAULT_LOG_PATH` | `"./logs/"` | 默认日志根目录。 |
| `DEFAULT_LOG_FILE` | `"run/jiuwen.log"` | common logger 默认文件名。 |
| `DEFAULT_INTERFACE_LOG_FILE` | `"interface/jiuwen_interface.log"` | interface logger 默认文件名。 |
| `DEFAULT_PROMPT_BUILDER_LOG_FILE` | `"interface/jiuwen_prompt_builder_interface.log"` | prompt builder logger 默认文件名。 |
| `DEFAULT_PERFORMANCE_LOG_FILE` | `"performance/jiuwen_performance.log"` | performance logger 默认文件名。 |
| `DEFAULT_BACKUP_COUNT` | `20` | 默认轮转备份份数。 |
| `DEFAULT_MAX_BYTES` | `20 * 1024 * 1024` | 默认单文件大小上限，约 `20 MB`。 |
| `DEFAULT_FORMAT` | `%d{yyyy-MM-dd HH:mm:ss.SSS} \| %X{log_type} \| %file \| %line \| %method \| %X{trace_id} \| %-5level \| %msg%n` | 默认日志格式串。 |

## 工厂方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> defaultInnerLogConfig()` | 构造 `logging` 段内部使用的默认配置映射。 |
| `public static Map<String, Object> defaultLogConfig()` | 构造顶层配置映射，格式为 `{ "logging": defaultInnerLogConfig() }`。 |
