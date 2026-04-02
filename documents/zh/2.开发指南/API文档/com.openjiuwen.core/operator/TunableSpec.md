# com.openjiuwen.core.operator.TunableSpec

## record TunableSpec

```java
public record TunableSpec(String name, String kind, String path, Object constraint)
```

`TunableSpec` 描述单个可调参数的名称、类型、路径和可选约束元数据。

## 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `name` | `String` | 参数名称。 |
| `kind` | `String` | 参数种类，例如 `prompt`、`discrete`、`text`。 |
| `path` | `String` | 参数在算子内部状态中的定位路径。 |
| `constraint` | `Object` | 可选约束信息，通常是范围、类型或结构描述。 |

## 构造方法

### `public TunableSpec(String name, String kind, String path)`

创建不带约束元数据的参数规格，`constraint` 固定为 `null`。

## 说明

- `OperatorBaseTest` 覆盖了完整四参构造和三参简化构造，确认简化构造不会自动推断额外约束。
