# com.openjiuwen.core.common.constants.ControllerType

## enum ControllerType

```java
public enum ControllerType
```

`ControllerType` 定义控制器类型标识。

## 枚举值

| 枚举值 | 字符串值 | 说明 |
| --- | --- | --- |
| `REACT_CONTROLLER` | `"react"` | ReAct 控制器。 |
| `WORKFLOW_CONTROLLER` | `"workflow"` | 工作流控制器。 |
| `UNDEFINED` | `"undefined"` | 未知输入的回退值。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举的字符串值。 |
| `public static ControllerType fromValue(String value)` | 解析字符串为枚举；无匹配项时返回 `UNDEFINED`。 |

## 说明

- `getValue()` 带有 `@JsonValue`，`fromValue(String)` 带有 `@JsonCreator`，便于 JSON 序列化与反序列化。
