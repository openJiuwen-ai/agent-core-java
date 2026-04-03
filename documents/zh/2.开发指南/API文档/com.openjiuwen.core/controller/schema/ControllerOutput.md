# com.openjiuwen.core.controller.schema.ControllerOutput

## class ControllerOutput

```java
public class ControllerOutput
```

`ControllerOutput` 表示控制器批量调用的聚合结果，兼容“输出块列表”和“字典结果”两种数据形态。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | `String` | 输出类型；既可以是 `EventType` 的值，也可以是 `processing` 之类的特殊常量。 |
| `data` | `Object` | 结果体；源码允许 `List<ControllerOutputChunk>` 或 `Map<String, Object>`。 |
| `inputEventId` | `String` | 与本次结果关联的输入事件 ID。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `ControllerOutput()` | 创建空结果对象。 |
| `ControllerOutput(EventType type, List<ControllerOutputChunk> data)` | 使用枚举事件类型和输出块列表构造结果。 |
| `ControllerOutput(String type, Object data)` | 直接使用字符串类型和原始数据构造结果。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `setType(EventType type)` | `void` | 使用 `EventType.getValue()` 写入字符串类型。 |
| `getData()` | `Object` | 返回原始数据对象。 |
| `getDataAsChunks()` | `List<ControllerOutputChunk>` | 仅当 `data` 是列表时返回输出块列表，否则返回 `null`。 |
| `getDataAsMap()` | `Map<String, Object>` | 仅当 `data` 是映射时返回字典结果，否则返回 `null`。 |

## 说明

- `Controller.invoke()` 会把流式返回的输出聚合成该类型，并忽略 `all_tasks_processed` 结束块。
- 该类没有对 `data` 的具体结构做更强约束，调用方需要结合 `type` 判断实际载荷格式。
