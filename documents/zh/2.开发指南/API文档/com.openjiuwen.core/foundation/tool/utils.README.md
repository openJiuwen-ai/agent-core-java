# utils

`com.openjiuwen.core.foundation.tool.utils` 提供基于反射的 Schema 提取工具，用于把 Java 方法参数和类型结构转换为 JSON Schema。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`CallableSchemaExtractor`](utils/CallableSchemaExtractor.md) | 从方法签名生成输入参数 Schema，并推导默认描述。 |
| [`TypeSchemaExtractor`](utils/TypeSchemaExtractor.md) | 从 Java 类型生成 JSON Schema 片段。 |

## 说明

- `CallableSchemaExtractor` 会把非 `Optional` 参数自动加入 `required` 列表。
- `TypeSchemaExtractor` 支持基本类型、时间类型、数组、集合、`Map`、`Optional`、枚举和普通 POJO 字段。 |
