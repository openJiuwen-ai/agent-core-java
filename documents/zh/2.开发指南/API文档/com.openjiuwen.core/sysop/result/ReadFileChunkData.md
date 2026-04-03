# com.openjiuwen.core.sysop.result.ReadFileChunkData

## 类 ReadFileChunkData

```java
public class ReadFileChunkData
```

分块读取文件的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `path` | `String` | - | 读取目标文件路径。 |
| `chunkContent` | `Object` | - | 当前分块内容；`mode = "text"` 时为 `String`，`mode = "bytes"` 时为 `byte[]`。 |
| `mode` | `String` | - | 文件读取模式，可为 `"text"` 或 `"bytes"`。 |
| `chunkSize` | `int` | - | 每个分块的大小，单位为字节。 |
| `chunkIndex` | `int` | - | 当前分块索引，从 `0` 开始。 |
| `lastChunk` | `boolean` | - | 是否为最后一个分块。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getChunkContentAsString()` | 在 `text` 模式下以 `String` 形式获取分块内容。 |
| `public byte[] getChunkContentAsBytes()` | 在 `bytes` 模式下以 `byte[]` 形式获取分块内容。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
