# com.openjiuwen.core.retrieval.common.RetrievalExceptions

## class RetrievalExceptions

```java
public final class RetrievalExceptions
```

Helpers for building retrieval-related exceptions with concise call sites.

## Constructors

| Signature | Description |
| --- | --- |
| `private RetrievalExceptions()` | Create a new `RetrievalExceptions` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static BaseError error(StatusCode status, String message)` | Execute `error`. |
| `public static ValidationError validation(String message)` | Execute `validation`. |
