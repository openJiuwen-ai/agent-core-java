# com.openjiuwen.core.common.security.JsonUtils

## class JsonUtils

```java
public final class JsonUtils
```

`JsonUtils` 基于共享的 Jackson `ObjectMapper` 提供安全的 JSON 读写能力。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `LOG` | `Logger` | 记录可降级错误日志。 |
| `MAPPER` | `ObjectMapper` | 共享的 Jackson 映射器实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static <T> T safeJsonLoads(String json, Class<T> type, T defaultValue)` | 反序列化 JSON；失败时若 `defaultValue` 为 `null` 则抛出 `COMMON_JSON_INPUT_PROCESS_ERROR`，否则记录日志并返回默认值。 |
| `public static <T> T safeJsonLoads(String json, Class<T> type)` | 使用 `null` 作为默认值执行反序列化。 |
| `public static String safeJsonDumps(Object obj, String defaultValue)` | 序列化对象；失败时若 `defaultValue` 为 `null` 则抛出 `COMMON_JSON_EXECUTION_PROCESS_ERROR`，否则记录日志并返回默认值。 |
| `public static String safeJsonDumps(Object obj)` | 使用 `null` 作为默认值执行序列化。 |
| `public static ObjectMapper getMapper()` | 返回共享的 `ObjectMapper` 实例，供高级场景直接复用。 |

## 说明

- `safeJsonLoads` 会区分 `JsonProcessingException` 与其他异常，但两类异常都会在无默认值时转换为框架错误。
- `safeJsonDumps` 在异常时同样遵循“有默认值则降级，无默认值则抛错”的策略。
