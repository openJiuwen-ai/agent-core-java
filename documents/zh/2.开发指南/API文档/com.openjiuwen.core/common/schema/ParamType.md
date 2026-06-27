# com.openjiuwen.core.common.schema.ParamType

## enum ParamType

```java
public enum ParamType
```

`ParamType` 定义参数模型支持的类型集合。

## 枚举值

| 枚举值 | 序列化值 | 说明 |
| --- | --- | --- |
| `STRING` | `"string"` | 字符串类型。 |
| `BOOLEAN` | `"boolean"` | 布尔类型。 |
| `INTEGER` | `"integer"` | 整数类型。 |
| `NUMBER` | `"number"` | 数值类型。 |
| `ARRAY` | `"array"` | 数组类型。 |
| `OBJECT` | `"object"` | 对象类型。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回枚举对应的字符串值。 |
| `public static ParamType fromValue(String value)` | 按字符串值解析枚举；未知值会抛出 `IllegalArgumentException`。 |
