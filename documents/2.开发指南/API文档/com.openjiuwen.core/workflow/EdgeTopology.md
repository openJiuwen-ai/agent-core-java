# com.openjiuwen.core.workflow.EdgeTopology

## class EdgeTopology

```java
public class EdgeTopology
```

Edge topology snapshot used by workflow ability inference.

## Fields

| Signature | Description |
| --- | --- |
| `private final Map<String, List<String>> sourceMap` | Source map. |
| `private final Map<String, List<String>> targetMap` | Target map. |
| `private final Map<String, List<String>> sourceStreamMap` | Source stream map. |
| `private final Map<String, List<String>> targetStreamMap` | Target stream map. |

## Constructors

| Signature | Description |
| --- | --- |
| `public EdgeTopology(Map<String, List<String>> sourceMap, Map<String, List<String>> targetMap, Map<String, List<String>> sourceStreamMap, Map<String, List<String>> targetStreamMap)` | Create a new `EdgeTopology` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, List<String>> getSourceMap()` | Return the source map. |
| `public Map<String, List<String>> getTargetMap()` | Return the target map. |
| `public Map<String, List<String>> getSourceStreamMap()` | Return the source stream map. |
| `public Map<String, List<String>> getTargetStreamMap()` | Return the target stream map. |
| `public Set<String> allEdgeNodes()` | Execute `allEdgeNodes`. |
