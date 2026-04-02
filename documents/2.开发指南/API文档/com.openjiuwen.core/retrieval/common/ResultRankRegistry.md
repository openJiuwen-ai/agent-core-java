# com.openjiuwen.core.retrieval.common.ResultRankRegistry

## class ResultRankRegistry

```java
public final class ResultRankRegistry
```

Registry for database-native ranker implementations.

## Constructors

| Signature | Description |
| --- | --- |
| `private ResultRankRegistry()` | Create a new `ResultRankRegistry` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void registerResultRankerClass(String database, Class<?> weightedClass, Class<?> rrfClass, Map<String, Class<?>> extras)` | Execute `registerResultRankerClass`. |

## Notes

- Related tests: `ConfigTest.java`.
