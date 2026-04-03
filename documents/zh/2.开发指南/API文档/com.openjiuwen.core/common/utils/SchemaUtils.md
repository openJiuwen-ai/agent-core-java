# com.openjiuwen.core.common.utils.SchemaUtils

## class SchemaUtils

```java
public final class SchemaUtils
```

`SchemaUtils` 负责基于 schema 对 `Map<String, Object>` 数据进行默认值填充、结构校验、反射式 schema 提取以及空值移除。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MAPPER` | `ObjectMapper` | 用于反射和节点转换的共享 Jackson 映射器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema)` | 按 schema 填充默认值，并执行校验。 |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema, boolean skipValidate)` | 按需跳过校验执行格式化。 |
| `public static Map<String, Object> formatWithSchema(Map<String, Object> data, Map<String, Object> schema, boolean skipNoneValue, boolean skipValidate)` | 可选先移除空值，再填充默认值并按需执行校验。 |
| `public static void validateWithSchema(Map<String, Object> data, Map<String, Object> schema)` | 校验必填字段，以及字符串长度、数值范围、数组长度和基础类型约束。 |
| `public static Map<String, Object> getSchemaDict(Class<?> clazz)` | 根据类的声明字段构造简化版 schema；传入 `null` 时返回 `null`。 |
| `public static Map<String, Object> removeNoneValues(Map<String, Object> data)` | 递归移除 `Map` 与 `List` 中的 `null` 值；若全部为空则返回 `null`。 |

## 说明

- `formatWithSchema(...)` 在 `data` 为 `null` 时会抛出 `ValidationError`，状态码为 `SCHEMA_FORMAT_INVALID`。
- `validateWithSchema(...)` 在校验失败时会抛出 `ValidationError`，状态码为 `SCHEMA_VALIDATE_INVALID`。
- `applyDefaults(...)` 会为 `Map` 与 `List` 类型的默认值创建浅复制副本，避免直接复用 schema 中的可变对象。
- `SchemaUtilsTest` 覆盖默认值注入、约束校验、反射输出与空值场景。
