# com.openjiuwen.core.sysop.result.FileSystemItem

## 类 FileSystemItem

```java
public class FileSystemItem
```

文件与目录公共属性的基础模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | - | 文件或目录名称。 |
| `path` | `String` | - | 文件或目录的绝对路径。 |
| `size` | `long` | - | 大小，单位为字节。 |
| `modifiedTime` | `String` | - | 最后修改时间，使用 ISO 字符串表示。 |
| `directory` | `boolean` | - | 是否为目录。 |
| `type` | `String` | - | 文件扩展名，仅对文件有效。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
