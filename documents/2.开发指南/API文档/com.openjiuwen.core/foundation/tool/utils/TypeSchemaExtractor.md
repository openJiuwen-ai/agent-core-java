# com.openjiuwen.core.foundation.tool.utils.TypeSchemaExtractor

## class TypeSchemaExtractor

```java
public final class TypeSchemaExtractor
```

类型 Schema 提取器。它会根据 Java 类型信息生成 JSON Schema 片段，并对复杂 POJO 递归展开字段。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> extract(Type type)` | 从给定类型生成 JSON Schema。 |

## 使用说明

- 本类是纯静态工具类，源码使用私有构造器阻止实例化。
- `String`、`UUID` 映射为 `type=string`；整数、浮点、布尔、日期时间等基础类型会映射为对应 JSON Schema 基本类型或 `format`。
- `Optional<T>` 会在子类型 Schema 上追加 `nullable=true`。
- `List<T>`、`Set<T>`、数组会映射为 `type=array` 并递归生成 `items`。
- `Map<K,V>` 会映射为 `type=object`，并在可用时生成 `additionalProperties`。
- POJO 提取时会跳过 `static` 与合成字段，并把非 `Optional` 字段加入 `required` 列表。
