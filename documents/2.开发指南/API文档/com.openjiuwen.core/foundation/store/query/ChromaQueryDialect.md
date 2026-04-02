# com.openjiuwen.core.foundation.store.query.ChromaQueryDialect

## class ChromaQueryDialect

```java
public final class ChromaQueryDialect
```

Query expression support for ChromaDB.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `OPERATOR_MAP` | `static final Map<String, String>` | `Map.of( "==", "$eq", "!=", "$nin", ">", "$gt", ">=", "$gte", "<", "$lt", "<=", "$lte" )` | Operator map. |

## Constructors

| Signature | Description |
| --- | --- |
| `private ChromaQueryDialect()` | Create a new `ChromaQueryDialect` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QueryLanguageDefinition definition()` | Execute `definition`. |
| `static Map<String, Map<String, Object>> comparisonFilter(ComparisonExpr self)` | Execute `comparisonFilter`. |
| `static Map<String, Map<String, Object>> rangeFilter(RangeExpr self)` | Execute `rangeFilter`. |
| `static Map<String, Map<String, Object>> arithmeticFilter(ArithmeticExpr self)` | Execute `arithmeticFilter`. |
| `static Map<String, Map<String, Object>> nullFilter(NullExpr self)` | Execute `nullFilter`. |
| `static Map<String, Map<String, Object>> jsonFilter(JSONExpr self)` | Execute `jsonFilter`. |
| `static Map<String, Map<String, Object>> arrayFilter(ArrayExpr self)` | Execute `arrayFilter`. |
| `static Map<String, Map<String, Object>> logicalFilter(LogicalExpr self)` | Execute `logicalFilter`. |
| `static Map<String, Map<String, Object>> textMatchFilter(MatchExpr self)` | Execute `textMatchFilter`. |
| `private static Map<String, Object> combineFilters(String op, Map<String, Object> left, Map<String, Object> right)` | Execute `combineFilters`. |
| `private static void raiseQueryError(String reason)` | Execute `raiseQueryError`. |
