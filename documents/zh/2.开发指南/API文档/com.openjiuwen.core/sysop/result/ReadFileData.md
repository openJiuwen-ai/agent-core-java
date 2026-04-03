# com.openjiuwen.core.sysop.result.ReadFileData

## 类 ReadFileData

```java
public class ReadFileData
```

读取文件操作的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `path` | `String` | - | 读取目标文件路径。 |
| `content` | `Object` | - | 文件内容；`mode = "text"` 时为 `String`，`mode = "bytes"` 时为 `byte[]`。 |
| `mode` | `String` | - | 文件读取模式，可为 `"text"` 或 `"bytes"`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getContentAsString()` | 在 `text` 模式下以 `String` 形式获取文件内容。 |
| `public byte[] getContentAsBytes()` | 在 `bytes` 模式下以 `byte[]` 形式获取文件内容。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
