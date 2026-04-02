# com.openjiuwen.core.retrieval.common.RetrievalValidation

## class RetrievalValidation

```java
public final class RetrievalValidation
```

Shared retrieval validation helpers.

## Constructors

| Signature | Description |
| --- | --- |
| `private RetrievalValidation()` | Create a new `RetrievalValidation` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static final Set<String> INDEX_TYPES = Set.of("hybrid", "bm25", "vector")` | Execute `of`. |
| `public static final Set<String> DISTANCE_METRICS = Set.of("cosine", "euclidean", "dot")` | Execute `of`. |
| `public static final Set<String> STORE_TYPES = Set.of("milvus", "chroma", "pgvector")` | Execute `of`. |
| `public static void requireNonBlank(String value, String field)` | Execute `requireNonBlank`. |
| `} public static void requireNonNull(Object value, String field)` | Execute `requireNonNull`. |
| `} public static void requirePositive(int value, String field, StatusCode status)` | Execute `requirePositive`. |
| `} public static void requireNonNegative(int value, String field, StatusCode status)` | Execute `requireNonNegative`. |
| `} public static String validateIndexType(String value, String field)` | Execute `validateIndexType`. |
