# com.openjiuwen.core.sys_operation.result.WriteFileData

## 类 WriteFileData

```java
public class WriteFileData
```

写入文件操作的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `path` | `String` | - | 写入目标文件路径。 |
| `size` | `int` | - | 文件内容大小，单位为字节。 |
| `mode` | `String` | - | 文件写入模式，可为 `"text"` 或 `"bytes"`。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
