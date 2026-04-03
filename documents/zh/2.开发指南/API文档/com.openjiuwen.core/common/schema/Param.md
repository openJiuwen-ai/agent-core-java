# com.openjiuwen.core.common.schema.Param

## class Param

```java
public class Param
```

`Param` 表示不可变的参数定义，支持标量、数组和对象三类结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | 必填 | 参数名。 |
| `description` | `String` | 必填 | 参数描述。 |
| `type` | `ParamType` | 必填 | 参数类型。 |
| `required` | `boolean` | 工厂方法传入值 | 是否必填。 |
| `defaultValue` | `Object` | `null` 或工厂方法传入值 | 默认值。 |
| `items` | `Param` | 仅 `ARRAY` 使用 | 数组元素定义。 |
| `properties` | `List<Param>` | 仅 `OBJECT` 使用 | 对象属性定义。 |

## 访问方法

| 签名 | 说明 |
| --- | --- |
| `public String getName()` | 返回参数名。 |
| `public String getDescription()` | 返回参数描述。 |
| `public ParamType getType()` | 返回参数类型。 |
| `public boolean isRequired()` | 返回是否必填。 |
| `public Object getDefaultValue()` | 返回默认值。 |
| `public Param getItems()` | 返回数组元素定义。 |
| `public List<Param> getProperties()` | 返回对象属性定义。 |
| `public String toString()` | 返回包含名称、类型和必填标记的摘要字符串。 |

## 工厂方法

| 签名 | 说明 |
| --- | --- |
| `public static Param string(String name, String description, boolean required)` | 创建无默认值的字符串参数。 |
| `public static Param string(String name, String description, boolean required, String defaultValue)` | 创建带默认值的字符串参数。 |
| `public static Param bool(String name, String description, boolean required)` | 创建无默认值的布尔参数。 |
| `public static Param bool(String name, String description, boolean required, Boolean defaultValue)` | 创建带默认值的布尔参数。 |
| `public static Param integer(String name, String description, boolean required)` | 创建无默认值的整数参数。 |
| `public static Param integer(String name, String description, boolean required, Integer defaultValue)` | 创建带默认值的整数参数。 |
| `public static Param number(String name, String description, boolean required)` | 创建无默认值的数值参数。 |
| `public static Param number(String name, String description, boolean required, Double defaultValue)` | 创建带默认值的数值参数。 |
| `public static Param array(String name, String description, boolean required, Param items)` | 创建数组参数。 |
| `public static Param array(String name, String description, boolean required, Param items, Object defaultValue)` | 创建带默认值的数组参数。 |
| `public static Param object(String name, String description, boolean required, List<Param> properties)` | 创建对象参数。 |
| `public static Param object(String name, String description, boolean required, List<Param> properties, Object defaultValue)` | 创建带默认值的对象参数。 |

## 说明

- 构造阶段会立即执行结构校验：`ARRAY` 必须提供 `items` 且不能提供 `properties`；`OBJECT` 必须提供 `properties` 且不能提供 `items`；其他类型两者都不能提供。
- 结构不合法时会抛出 `IllegalArgumentException`，错误消息中包含参数名。
