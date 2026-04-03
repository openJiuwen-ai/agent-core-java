# com.openjiuwen.core.common.security.UserConfig

## class UserConfig

```java
public final class UserConfig
```

`UserConfig` 负责读取敏感路径配置，并以单例形式缓存结果。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `DEFAULT_SENSITIVE_PATHS` | `List<String>` | 源码常量 | 默认敏感路径集合，包含常见 Linux 与 Windows 系统路径。 |
| `instance` | `UserConfig` | `null` | 单例实例。 |
| `configPath` | `Path` | `null` | 初始化前可设置的配置文件路径。 |
| `sensitive` | `boolean` | `true` 或配置值 | 是否启用敏感路径检查。 |
| `sensitivePaths` | `List<String>` | 延迟初始化 | 实际生效的敏感路径列表。 |
| `properties` | `Properties` | 空配置 | 加载自外部配置文件的属性集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static void setConfigPath(Path path)` | 在首次初始化前设置配置文件路径；若单例已创建则抛出 `IllegalStateException`。 |
| `public static UserConfig getConfig()` | 返回单例配置实例。 |
| `public static boolean isSensitive()` | 判断是否启用敏感检查；当环境变量 `IS_SENSITIVE=false` 时优先返回 `false`。 |
| `public static List<String> getSensitivePaths()` | 返回当前配置解析出的敏感路径列表。 |
| `public static void setSensitive(boolean isSensitive)` | 运行时修改敏感检查开关。 |
| `public List<String> getSensitivePathsList()` | 延迟解析并返回敏感路径列表；未配置时回退到 `DEFAULT_SENSITIVE_PATHS`。 |
| `public static synchronized void reset()` | 重置单例与配置路径，主要用于测试。 |

## 说明

- 配置文件存在且可读取时，会解析 `settings.is_sensitive` 与 `settings.sensitive_paths`。
- `settings.sensitive_paths` 采用逗号分隔；空值会被过滤。
