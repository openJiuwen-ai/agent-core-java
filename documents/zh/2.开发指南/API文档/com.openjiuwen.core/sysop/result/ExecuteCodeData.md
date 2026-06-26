# com.openjiuwen.core.sys_operation.result.ExecuteCodeData

## 类 ExecuteCodeData

```java
public class ExecuteCodeData
```

代码执行结果的数据模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `codeContent` | `String` | - | 原始执行代码。 |
| `language` | `String` | - | 原始代码使用的语言。 |
| `exitCode` | `Integer` | - | 执行退出码。 |
| `stdout` | `String` | `""` | 标准输出内容。 |
| `stderr` | `String` | `""` | 标准错误内容。 |

## Lombok 说明

- 该类型使用 `Data`, `Builder`, `NoArgsConstructor`, `AllArgsConstructor` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
