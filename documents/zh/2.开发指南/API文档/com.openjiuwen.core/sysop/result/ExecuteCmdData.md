# com.openjiuwen.core.sys_operation.result.ExecuteCmdData

## 类 ExecuteCmdData

```java
public class ExecuteCmdData
```

命令执行的数据结构。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `command` | `String` | - | 原始执行命令。 |
| `cwd` | `String` | `"."` | 执行命令时的当前工作目录。 |
| `exitCode` | `Integer` | - | 命令退出码。 |
| `stdout` | `String` | `""` | 标准输出内容。 |
| `stderr` | `String` | `""` | 标准错误内容。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
