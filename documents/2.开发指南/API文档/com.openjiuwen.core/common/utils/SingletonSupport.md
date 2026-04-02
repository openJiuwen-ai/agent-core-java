# com.openjiuwen.core.common.utils.SingletonSupport

## class SingletonSupport

```java
public abstract class SingletonSupport<T>
```

`SingletonSupport` provides generic, thread-safe singleton creation and reset helpers for Java services.

## Methods

| Signature | Description |
| --- | --- |
| `public static <T> T getInstance(Class<T> clazz, java.util.function.Supplier<T> factory)` | Return the singleton instance for `clazz`, creating and caching it under a class-keyed `ConcurrentHashMap` when absent. |
| `public static void reset(Class<?> clazz)` | Remove the cached singleton instance for `clazz`, primarily for tests or explicit reinitialization. |

## Notes

- Instance creation uses a class-level synchronized double-check around the concurrent cache.
- The type is intended to be subclassed, not instantiated directly.
