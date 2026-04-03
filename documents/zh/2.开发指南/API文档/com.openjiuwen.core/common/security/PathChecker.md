# com.openjiuwen.core.common.security.PathChecker

## class PathChecker

```java
public final class PathChecker
```

`PathChecker` 是一个线程安全单例，用于判断给定路径是否命中敏感路径前缀。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `instance` | `PathChecker` | 单例实例，使用双重检查锁延迟初始化。 |
| `sensitivePaths` | `Set<String>` | 已归一化的敏感路径集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static PathChecker getInstance()` | 返回单例实例，首次访问时加载配置。 |
| `public boolean checkSensitive(String path)` | 判断输入路径是否位于任何敏感前缀下；输入为空返回 `false`，规范化失败时按失败关闭策略返回 `true`。 |
| `public static boolean isSensitivePath(String path)` | 静态便捷入口，内部调用单例的 `checkSensitive`。 |

## 说明

- 初始化阶段会调用 `UserConfig.getSensitivePaths()`；若读取失败，则回退到 `UserConfig.DEFAULT_SENSITIVE_PATHS`。
- 每个配置项会先尝试转为绝对规范路径；转换失败时保留去空白后的原始字符串。
