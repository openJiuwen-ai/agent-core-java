# com.openjiuwen.core.controller.schema.InputEvent

## class InputEvent

```java
public class InputEvent extends Event
```

`InputEvent` 是控制器接收用户请求时使用的主输入事件，载荷由 `List<DataFrame>` 表示。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `inputData` | `List<DataFrame>` | 空列表 | 输入数据帧列表。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `InputEvent()` | 创建空输入事件，并把 `eventType` 固定为 `INPUT`。 |
| `InputEvent(List<DataFrame> inputData)` | 使用传入数据帧列表构造输入事件；传 `null` 时回退为空列表。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getInputData()` | `List<DataFrame>` | 读取输入数据帧列表。 |
| `setInputData(List<DataFrame> inputData)` | `void` | 更新输入列表；传 `null` 时回退为空列表。 |
| `fromUserInput(Object userInput)` | `InputEvent` | 把 `String`、`Map` 或已有 `InputEvent` 转换为控制器输入事件。 |

## 说明

- `fromUserInput(String)` 会创建只包含一条 `TextDataFrame` 的输入事件。
- `fromUserInput(Map)` 会创建只包含一条 `JsonDataFrame` 的输入事件。
- 其他类型会触发 `IllegalArgumentException`；因此调用控制器入口时最好先完成输入归一化。
