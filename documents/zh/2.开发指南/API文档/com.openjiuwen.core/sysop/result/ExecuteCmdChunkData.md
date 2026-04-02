# com.openjiuwen.core.sysop.result.ExecuteCmdChunkData

## 类 ExecuteCmdChunkData

```java
public class ExecuteCmdChunkData
```

命令执行输出分块的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `text` | `String` | `""` | 输出分块的原始内容。 |
| `type` | `String` | - | 输出分块类型，可为 `"stdout"` 或 `"stderr"`。 |
| `chunkIndex` | `int` | - | 当前分块索引，从 `0` 开始。 |
| `exitCode` | `Integer` | - | 命令退出码。 |
| `metadata` | `Map<String, Object>` | - | 命令执行相关的附加元数据。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
