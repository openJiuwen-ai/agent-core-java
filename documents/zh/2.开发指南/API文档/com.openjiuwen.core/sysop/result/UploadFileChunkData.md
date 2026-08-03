# com.openjiuwen.core.sys_operation.result.UploadFileChunkData

## 类 UploadFileChunkData

```java
public class UploadFileChunkData
```

上传文件分块返回的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `localPath` | `String` | - | 本地文件路径。 |
| `targetPath` | `String` | - | 目标文件路径。 |
| `chunkSize` | `int` | - | 每个分块的大小，单位为字节。 |
| `chunkIndex` | `int` | - | 当前分块索引，从 `0` 开始。 |
| `lastChunk` | `boolean` | - | 是否为最后一个分块。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
