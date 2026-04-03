# com.openjiuwen.core.common.exception.ErrorHelper

## 类 ErrorHelper

```java
public final class ErrorHelper
```

`ErrorHelper` 提供围绕 `StatusCode` 的静态工厂与立即抛出辅助方法。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static BaseError buildError(StatusCode status)` | 按 `StatusMapping` 的默认解析规则构造异常实例，但不立即抛出。 |
| `public static BaseError buildError(StatusCode status, String... kvPairs)` | 以交替的 key/value 参数构造异常，并将其用于模板渲染。 |
| `public static BaseError buildError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 使用完整上下文构造异常，并按解析结果选择具体子类。 |
| `public static void raiseError(StatusCode status)` | 解析异常类型后立即抛出。 |
| `public static void raiseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 使用完整上下文构造并立即抛出异常。 |
| `public static void systemError(StatusCode status)` | 立即抛出 `FrameworkError`。 |
| `public static void systemError(StatusCode status, Throwable cause, Map<String, Object> params)` | 立即抛出携带根因与参数的 `FrameworkError`。 |
| `public static void validateError(StatusCode status)` | 立即抛出 `ValidationError`。 |
| `public static void validateError(StatusCode status, Throwable cause, Map<String, Object> params)` | 立即抛出携带根因与参数的 `ValidationError`。 |
| `public static void terminate(StatusCode status)` | 立即抛出 `Termination`。 |
| `public static void terminate(StatusCode status, Map<String, Object> params)` | 立即抛出携带参数的 `Termination`。 |

## 说明

- 该类是 `final`，且只有私有构造方法。
- `buildError(StatusCode, String... kvPairs)` 按顺序消费成对参数；若末尾出现孤立值，会被静默忽略。
- `buildError(...)` 与 `raiseError(...)` 内部都会先借助 `StatusMapping` 决定具体异常子类。
- `ErrorTest` 覆盖了构造、抛出以及携带完整上下文的行为。
