# com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation

## 类 AddScalarFieldOperation

```java
public class AddScalarFieldOperation
```

`AddScalarFieldOperation` 表示为向量数据类型新增一个标量字段。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dataType` | `String` | 目标向量数据类型。 |
| `fieldName` | `String` | 新增字段名。 |
| `fieldType` | `String` | 新增字段的数据类型。 |
| `defaultValue` | `Object` | 字段默认值；可为 `null`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AddScalarFieldOperation(OperationMetadata metadata, String dataType, String fieldName, String fieldType, Object defaultValue)` | 创建一条新增标量字段操作。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDataType()` | 返回目标数据类型。 |
| `public String getFieldName()` | 返回字段名。 |
| `public String getFieldType()` | 返回字段类型。 |
| `public Object getDefaultValue()` | 返回默认值对象。 |
