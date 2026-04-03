# com.openjiuwen.core.controller.schema.DataFrame

## sealed interface DataFrame

```java
public sealed interface DataFrame permits DataFrame.TextDataFrame, DataFrame.FileDataFrame, DataFrame.JsonDataFrame
```

`DataFrame` 是控制器侧统一的数据承载接口，当前允许三种具体实现：文本、文件和 JSON。相关对象会出现在 `InputEvent` 的输入列表中，也会被控制器内部模块用作结构化数据容器。

## 公共方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getType()` | `String` | 返回当前数据帧的类型标识，取值为 `"text"`、`"file"` 或 `"json"`。 |

## 具体实现类型

| 类型 | 说明 | 类型标识 |
|---|---|---|
| `TextDataFrame` | 仅保存一段文本内容。 | `"text"` |
| `FileDataFrame` | 保存文件名、MIME 类型以及可选的字节内容或 URI。 | `"file"` |
| `JsonDataFrame` | 保存一个 `Map<String, Object>` 形式的 JSON 结构。 | `"json"` |

### `TextDataFrame`

| 字段 | 类型 | 说明 |
|---|---|---|
| `text` | `String` | 文本内容。 |

- `getType()` 固定返回 `"text"`。

### `FileDataFrame`

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | `String` | 文件名。 |
| `mimeType` | `String` | MIME 类型。 |
| `bytes` | `byte[]` | 文件字节内容，可为 `null`。 |
| `uri` | `String` | 文件 URI，可为 `null`。 |

- 主记录构造参数顺序为 `name`、`mimeType`、`bytes`、`uri`。
- 额外提供 `FileDataFrame(String name, String mimeType)` 便捷构造方法，内部会把 `bytes` 和 `uri` 置为 `null`。
- `getType()` 固定返回 `"file"`。

### `JsonDataFrame`

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | `Map<String, Object>` | JSON 对象形式的数据载荷。 |

- `getType()` 固定返回 `"json"`。

## 说明

- `IntentRecognizer.recognize(...)` 当前只接受单条 `TextDataFrame` 输入；如果输入中包含文件帧、JSON 帧或多条文本帧，会抛出运行时错误。
- `DataFrame` 本身只定义类型标识接口，不负责序列化、校验或转换逻辑。
