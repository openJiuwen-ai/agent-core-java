# com.openjiuwen.core.sys_operation.result.SearchFilesData

## 类 SearchFilesData

```java
public class SearchFilesData
```

搜索文件操作的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `totalMatches` | `int` | - | 匹配文件总数。 |
| `matchingFiles` | `List<FileSystemItem>` | - | 匹配文件列表。 |
| `searchPath` | `String` | - | 搜索时使用的基础路径。 |
| `searchPattern` | `String` | - | 原始搜索模式。 |
| `excludePatterns` | `List<String>` | - | 原始排除模式列表。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
