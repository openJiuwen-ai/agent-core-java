# com.openjiuwen.core.common.schema.Param

## class Param

```java
public class Param
```

`Param` is an immutable parameter definition model that supports scalar, array, and object shapes.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `String` | required | Parameter name. |
| `description` | `String` | required | Human-readable description. |
| `type` | `ParamType` | required | Declared parameter kind. |
| `required` | `boolean` | factory-supplied | Whether callers must provide the value. |
| `defaultValue` | `Object` | `null` unless supplied | Default value for optional parameters. |
| `items` | `Param` | `null` unless `ARRAY` | Item schema used only for array parameters. |
| `properties` | `List<Param>` | `null` unless `OBJECT` | Child property definitions used only for object parameters. |

## Accessors

| Signature | Description |
| --- | --- |
| `public String getName()` | Return the parameter name. |
| `public String getDescription()` | Return the description. |
| `public ParamType getType()` | Return the declared parameter type. |
| `public boolean isRequired()` | Return whether the parameter is required. |
| `public Object getDefaultValue()` | Return the default value. |
| `public Param getItems()` | Return the nested item schema for array parameters. |
| `public List<Param> getProperties()` | Return the nested property definitions for object parameters. |
| `public String toString()` | Render a compact summary with name, type, and required flag. |

## Factory Methods

| Signature | Description |
| --- | --- |
| `public static Param string(String name, String description, boolean required)` | Create a string parameter without a default. |
| `public static Param string(String name, String description, boolean required, String defaultValue)` | Create a string parameter with a default value. |
| `public static Param bool(String name, String description, boolean required)` | Create a boolean parameter without a default. |
| `public static Param bool(String name, String description, boolean required, Boolean defaultValue)` | Create a boolean parameter with a default value. |
| `public static Param integer(String name, String description, boolean required)` | Create an integer parameter without a default. |
| `public static Param integer(String name, String description, boolean required, Integer defaultValue)` | Create an integer parameter with a default value. |
| `public static Param number(String name, String description, boolean required)` | Create a floating-point parameter without a default. |
| `public static Param number(String name, String description, boolean required, Double defaultValue)` | Create a floating-point parameter with a default value. |
| `public static Param array(String name, String description, boolean required, Param items)` | Create an array parameter with a required item schema. |
| `public static Param array(String name, String description, boolean required, Param items, Object defaultValue)` | Create an array parameter with an item schema and default value. |
| `public static Param object(String name, String description, boolean required, List<Param> properties)` | Create an object parameter with child property definitions. |
| `public static Param object(String name, String description, boolean required, List<Param> properties, Object defaultValue)` | Create an object parameter with child properties and a default value. |

## Notes

- Construction is validated immediately: `ARRAY` requires `items` and forbids `properties`, `OBJECT` requires `properties` and forbids `items`, and scalar types forbid both.
- Invalid shape combinations throw `IllegalArgumentException` with the parameter name embedded in the error message.
