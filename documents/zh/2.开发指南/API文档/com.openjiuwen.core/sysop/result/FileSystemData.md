# com.openjiuwen.core.sys_operation.result.FileSystemData

## 类 FileSystemData

```java
public class FileSystemData
```

列出文件与目录操作共用的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `totalCount` | `int` | - | 条目总数。 |
| `listItems` | `List<FileSystemItem>` | - | 文件或目录详情列表。 |
| `rootPath` | `String` | - | 原始输入目录路径。 |
| `recursive` | `boolean` | - | 实际使用的递归开关。 |
| `maxDepth` | `Integer` | - | 实际使用的最大递归深度。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
