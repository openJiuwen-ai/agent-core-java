# com.openjiuwen.core.common.security.ExceptionUtils

## class ExceptionUtils

```java
public final class ExceptionUtils
```

`ExceptionUtils` formats validation failures into short readable strings and unwraps nested exception chains.

## Constructors

| Signature | Description |
| --- | --- |
| `private ExceptionUtils()` | Utility-class constructor; the type is not instantiable. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String formatValidationError(Throwable t)` | Return `"Type: message"` for the supplied exception, or an empty string when `t` is `null`. |
| `public static Throwable getRootCause(Throwable t)` | Walk the `Throwable#getCause()` chain until it reaches the deepest non-self-referential cause. |

## Notes

- `formatValidationError` is the generic Java replacement for the Python-side validation-error formatter.
