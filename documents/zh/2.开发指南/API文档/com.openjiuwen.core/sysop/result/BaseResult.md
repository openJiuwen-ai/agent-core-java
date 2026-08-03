# com.openjiuwen.core.sys_operation.result.BaseResult

## 类 BaseResult

```java
public abstract class BaseResult<T>
```

所有 `sysop` 结果对象的公共基类。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `code` | `int` | - | 状态码；`0` 表示成功，非 `0` 表示失败。 |
| `message` | `String` | - | 结果消息。 |
| `data` | `T` | - | 业务数据，仅在成功时返回。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static <T, R extends BaseResult<T>> R buildOperationErrorResult(StatusCode errorType, Map<String, String> msgFormatKwargs, ResultFactory<R> resultFactory, T data)` | 根据指定错误类型和格式化参数构造标准化错误结果对象。 |
| `public static <T, R extends BaseResult<?>> R buildOperationErrorResult(StatusCode errorType, String execution, String errorMsg, ResultFactory<R> resultFactory, Object data)` | 针对 `execution` 与 `error_msg` 占位符的便捷重载。 |

## 嵌套公开类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| `ResultFactory` | 接口 | 用于创建具体结果实例的工厂接口，约定为无参 supplier。 |

## Lombok 说明

- 该类型使用 `Data`, `NoArgsConstructor`, `SuperBuilder` 生成访问器、builder 或构造辅助代码。
- 字段表基于源码中的显式字段整理；单独列出的方法仅包含显式声明的方法。
