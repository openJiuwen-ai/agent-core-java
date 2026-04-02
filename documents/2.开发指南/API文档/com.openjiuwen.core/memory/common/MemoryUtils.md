# com.openjiuwen.core.memory.common.MemoryUtils

## class MemoryUtils

```java
public final class MemoryUtils
```

Utility methods for memory module.

## Constructors

| Signature | Description |
| --- | --- |
| `private MemoryUtils()` | Create a new `MemoryUtils` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static String generateIdxName(String userId, String scopeId, String memType)` | Generate vector index name from user id, scope id and memory type. |
| `public static String parseMemTypeFromIdxName(String idxName)` | Parse memory type from vector index name. |
| `public static HitParseResult parseMemoryHitInfos(List<Map.Entry<String, Double>> hits)` | Parse memory hit infos from search results. |

## Nested Public Types

| Type | Signature | Description |
| --- | --- | --- |
| `HitParseResult` | `public static class HitParseResult` | Result of parsing memory hit infos. |
