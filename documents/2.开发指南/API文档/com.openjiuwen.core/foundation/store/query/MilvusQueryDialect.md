# com.openjiuwen.core.foundation.store.query.MilvusQueryDialect

## class MilvusQueryDialect

```java
public final class MilvusQueryDialect
```

Query expression support for Milvus.

## Constructors

| Signature | Description |
| --- | --- |
| `private MilvusQueryDialect()` | Create a new `MilvusQueryDialect` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QueryLanguageDefinition definition()` | Execute `definition`. |
| `static String comparisonFilter(ComparisonExpr self)` | Execute `comparisonFilter`. |
| `static String rangeFilter(RangeExpr self)` | Execute `rangeFilter`. |
| `static String arithmeticFilter(ArithmeticExpr self)` | Execute `arithmeticFilter`. |
| `static String nullFilter(NullExpr self)` | Execute `nullFilter`. |
| `static String jsonFilter(JSONExpr self)` | Execute `jsonFilter`. |
| `static String arrayFilter(ArrayExpr self)` | Execute `arrayFilter`. |
| `static String logicalFilter(LogicalExpr self)` | Execute `logicalFilter`. |
| `static String textMatchFilter(MatchExpr self)` | Execute `textMatchFilter`. |
| `private static String sanitize(Object value)` | Execute `sanitize`. |
| `private static void raiseQueryError(String reason)` | Execute `raiseQueryError`. |
