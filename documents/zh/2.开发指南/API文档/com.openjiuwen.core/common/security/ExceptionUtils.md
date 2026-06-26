# com.openjiuwen.core.common.security.ExceptionUtils

## class ExceptionUtils

```java
public final class ExceptionUtils
```

`ExceptionUtils` 提供异常链处理相关的静态工具方法。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static String formatValidationError(Throwable t)` | 将异常格式化为 `异常类型: 异常消息` 形式；当 `t` 为 `null` 时返回空字符串。 |
| `public static Throwable getRootCause(Throwable t)` | 沿 `getCause()` 链一直向下查找，返回最深层且非自引用的根因异常。 |

## 说明

- 该类型是纯工具类，源码仅保留私有构造器，不对外暴露实例化入口。
