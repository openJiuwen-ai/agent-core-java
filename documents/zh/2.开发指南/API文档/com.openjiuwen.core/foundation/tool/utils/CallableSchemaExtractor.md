# com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor

## class CallableSchemaExtractor

```java
public final class CallableSchemaExtractor
```

方法签名 Schema 提取器。它把 Java `Method` 的参数列表转换成输入参数 JSON Schema。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> generateSchema(Method method)` | 为方法构造 `type=object` 的输入 Schema，并生成 `properties`、`required`、`title`。 |
| `public static String extractFunctionDescription(Method method)` | 返回基于方法名推导的描述文本。 |

## 使用说明

- 本类是纯静态工具类，源码使用私有构造器阻止实例化。
- 参数描述默认来自 `humanizeName(parameter.getName())` 的结果。
- 方法名中的 camelCase 与下划线会被转换为空格分隔的小写文本。
