# com.openjiuwen.core.foundation.store.graph.GraphUtils

## class GraphUtils

```java
public final class GraphUtils
```

Graph store utility functions.

## Constructors

| Signature | Description |
| --- | --- |
| `private GraphUtils()` | Create a new `GraphUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n, boolean strict)` | Batch an iterable into fixed-size chunks. |
| `public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n)` | Batch an iterable into fixed-size chunks (non-strict). |
