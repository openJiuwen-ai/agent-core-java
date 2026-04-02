# com.openjiuwen.core.sysop.local.InvokeData

## 类 InvokeData

```java
public class InvokeData
```

`InvokeData` 封装一次性子进程调用的完整结果，适用于 `ProcessHandler.invoke()` 这类需要一次性拿到全部输出的场景。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `stdout` | `String` | - | 子进程完整标准输出文本。 |
| `stderr` | `String` | - | 子进程完整标准错误文本。 |
| `exitCode` | `int` | - | 子进程退出码；`0` 表示成功，非 `0` 表示失败。 |
| `exception` | `Exception` | - | 调用过程中捕获到的异常；正常完成时通常为 `null`。 |

## Lombok 说明

- 该类型使用 `Data`、`Builder`、`NoArgsConstructor`、`AllArgsConstructor` 生成访问器、构建器和构造辅助方法。
- 文档字段表以源码显式声明的字段为准。

## 相关测试

- `LocalUtilsTest`
